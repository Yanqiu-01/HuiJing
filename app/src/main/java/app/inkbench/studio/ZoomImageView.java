package app.inkbench.studio;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Pinch, double-tap and drag zoom on top of the platform fit-center scaling. */
public final class ZoomImageView extends ImageView {
    public interface SwipeListener { void onSwipe(int direction); }
    public interface TapListener { void onTap(); }

    private static final float MAX_SCALE = 5f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;
    private static final float SWIPE_SLOP_DP = 48f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;
    private SwipeListener swipeListener;
    private TapListener tapListener;

    public ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
        final float slop = SWIPE_SLOP_DP * getResources().getDisplayMetrics().density;
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomAt(detector.getFocusX(), detector.getFocusY(), scale * detector.getScaleFactor());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                if (tapListener != null) tapListener.onTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                zoomAt(event.getX(), event.getY(), scale > 1.01f ? 1f : DOUBLE_TAP_SCALE);
                return true;
            }
            @Override public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                if (scale > 1.01f || start == null || end == null) return false;
                float dx = end.getX() - start.getX();
                float dy = end.getY() - start.getY();
                if (Math.abs(dx) < slop || Math.abs(dx) < Math.abs(dy)) return false;
                if (swipeListener != null) swipeListener.onSwipe(dx < 0 ? 1 : -1);
                return true;
            }
        });
    }

    public void setSwipeListener(SwipeListener listener) { swipeListener = listener; }
    public void setTapListener(TapListener listener) { tapListener = listener; }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (scale > 1.01f && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    offsetX += event.getX() - lastX;
                    offsetY += event.getY() - lastY;
                    applyZoom();
                }
                lastX = event.getX();
                lastY = event.getY();
                break;
            default:
                break;
        }
        return true;
    }

    @Override public void setImageBitmap(android.graphics.Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        resetZoom();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetZoom();
    }

    private void zoomAt(float focusX, float focusY, float target) {
        float previous = scale;
        scale = Math.max(1f, Math.min(MAX_SCALE, target));
        offsetX += (focusX - getWidth() / 2f) * (1f - scale / previous);
        offsetY += (focusY - getHeight() / 2f) * (1f - scale / previous);
        applyZoom();
    }

    private void applyZoom() {
        float limitX = maxOffset(fittedWidth(), getWidth());
        float limitY = maxOffset(fittedHeight(), getHeight());
        offsetX = Math.max(-limitX, Math.min(limitX, offsetX));
        offsetY = Math.max(-limitY, Math.min(limitY, offsetY));
        setScaleX(scale);
        setScaleY(scale);
        setTranslationX(offsetX);
        setTranslationY(offsetY);
    }

    private float fittedWidth() { return drawableWidth() * fittedScale(); }
    private float fittedHeight() { return drawableHeight() * fittedScale(); }

    private float fittedScale() {
        float width = getWidth();
        float height = getHeight();
        float drawableWidth = drawableWidth();
        float drawableHeight = drawableHeight();
        if (width <= 0 || height <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return 1f;
        return Math.min(width / drawableWidth, height / drawableHeight);
    }

    private float drawableWidth() {
        return getDrawable() == null ? 0f : getDrawable().getIntrinsicWidth();
    }

    private float drawableHeight() {
        return getDrawable() == null ? 0f : getDrawable().getIntrinsicHeight();
    }

    static float maxOffset(float content, float view) {
        return Math.max(0f, (content - view) / 2f);
    }

    private void resetZoom() {
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        applyZoom();
    }
}
