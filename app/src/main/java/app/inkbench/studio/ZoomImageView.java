package app.inkbench.studio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Pinch, double-tap and drag zoom; horizontal flings are reported only at rest scale. */
public final class ZoomImageView extends ImageView {
    public interface SwipeListener { void onSwipe(int direction); }
    public interface TapListener { void onTap(); }

    private static final float MAX_SCALE = 5f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;
    private static final float SWIPE_SLOP_DP = 48f;

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float baseScale = 1f;
    private float baseX;
    private float baseY;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;
    private SwipeListener swipeListener;
    private TapListener tapListener;

    public ZoomImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        final float slop = SWIPE_SLOP_DP * getResources().getDisplayMetrics().density;
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomTo(detector.getFocusX(), detector.getFocusY(), scale * detector.getScaleFactor());
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
                zoomTo(event.getX(), event.getY(), scale > 1.01f ? 1f : DOUBLE_TAP_SCALE);
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
                    applyMatrix();
                }
                lastX = event.getX();
                lastY = event.getY();
                break;
            default:
                break;
        }
        return true;
    }

    private void zoomTo(float focusX, float focusY, float target) {
        Drawable drawable = getDrawable();
        if (drawable == null) return;
        float contentX = (focusX - baseX - offsetX) / (baseScale * scale);
        float contentY = (focusY - baseY - offsetY) / (baseScale * scale);
        scale = Math.max(1f, Math.min(MAX_SCALE, target));
        offsetX = focusX - baseX - contentX * baseScale * scale;
        offsetY = focusY - baseY - contentY * baseScale * scale;
        applyMatrix();
    }

    private void applyMatrix() {
        clampOffsets();
        matrix.reset();
        matrix.postScale(baseScale * scale, baseScale * scale);
        matrix.postTranslate(baseX + offsetX, baseY + offsetY);
        setImageMatrix(matrix);
        invalidate();
    }

    private void clampOffsets() {
        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() <= 0 || getHeight() <= 0) return;
        float width = drawable.getIntrinsicWidth() * baseScale * scale;
        float height = drawable.getIntrinsicHeight() * baseScale * scale;
        offsetX = clampCenter(baseX + offsetX, width, getWidth()) - baseX;
        offsetY = clampCenter(baseY + offsetY, height, getHeight()) - baseY;
    }

    static float clampCenter(float value, float content, float view) {
        if (content <= view) return (view - content) / 2f;
        return Math.max(view - content, Math.min(0f, value));
    }

    @Override public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        resetZoom();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetZoom();
    }

    private void resetZoom() {
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        Drawable drawable = getDrawable();
        int width = getWidth();
        int height = getHeight();
        if (drawable == null || width <= 0 || height <= 0) {
            baseScale = 1f;
            baseX = 0f;
            baseY = 0f;
            return;
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            baseScale = 1f;
            baseX = 0f;
            baseY = 0f;
            return;
        }
        baseScale = Math.min((float) width / intrinsicWidth, (float) height / intrinsicHeight);
        baseX = (width - intrinsicWidth * baseScale) / 2f;
        baseY = (height - intrinsicHeight * baseScale) / 2f;
        applyMatrix();
    }
}
