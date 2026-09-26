package app.inkbench.studio;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** Full-screen gallery viewer with previous/next navigation. */
public class ViewerActivity extends Activity {
    private PreviewLoader previews;
    private String[] paths = new String[0];
    private String[] prompts = new String[0];
    private int index;
    private ImageView image;
    private TextView caption;
    private TextView position;
    private TextView previous;
    private TextView next;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        previews = new PreviewLoader();
        Motion.refresh(this, getSharedPreferences("inkbench", MODE_PRIVATE).getBoolean("highRefresh", true));
        paths = stringArray("paths");
        prompts = stringArray("prompts");
        String singlePath = getIntent().getStringExtra("path");
        String singlePrompt = getIntent().getStringExtra("prompt");
        if (paths.length == 0 && singlePath != null) {
            paths = new String[]{singlePath};
            prompts = new String[]{singlePrompt == null ? "" : singlePrompt};
        }
        index = getIntent().getIntExtra("index", 0);
        if (index < 0 || index >= paths.length) index = 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101210);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout top = row();
        TextView back = label("‹  返回", 16);
        back.setOnClickListener(v -> finish());
        position = label("", 14);
        top.addView(back, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(position);
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(image, new LinearLayout.LayoutParams(-1, -2));
        caption = label("", 15);
        caption.setTextIsSelectable(true);
        caption.setPadding(dp(20), dp(16), dp(20), dp(24));
        body.addView(caption);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout controls = row();
        previous = label("‹ 上一张", 16);
        next = label("下一张 ›", 16);
        previous.setGravity(Gravity.START);
        next.setGravity(Gravity.END);
        previous.setOnClickListener(v -> move(-1));
        next.setOnClickListener(v -> move(1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(controls);

        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                if (start == null || end == null) return false;
                float dx = end.getX() - start.getX();
                if (Math.abs(dx) < dp(48) || Math.abs(dx) < Math.abs(end.getY() - start.getY())) return false;
                move(dx < 0 ? 1 : -1);
                return true;
            }
        });
        View.OnTouchListener touch = (v, event) -> gestures.onTouchEvent(event);
        image.setOnTouchListener(touch);
        scroll.setOnTouchListener(touch);
        setContentView(root);
        root.requestApplyInsets();
        showCurrent();
    }

    private void move(int delta) {
        int target = index + delta;
        if (target < 0 || target >= paths.length) return;
        index = target;
        showCurrent();
    }

    private void showCurrent() {
        boolean has = index >= 0 && index < paths.length;
        previous.setEnabled(has && index > 0);
        next.setEnabled(has && index < paths.length - 1);
        previous.setAlpha(previous.isEnabled() ? 1f : 0.35f);
        next.setAlpha(next.isEnabled() ? 1f : 0.35f);
        position.setText(has ? (index + 1) + " / " + paths.length : "没有图片");
        caption.setText(has && index < prompts.length && prompts[index] != null ? prompts[index] : "");
        if (has) previews.load(image, new File(paths[index]), 2400, true);
    }

    private String[] stringArray(String key) {
        String[] values = getIntent().getStringArrayExtra(key);
        return values == null ? new String[0] : values;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        return row;
    }

    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(0xFFF3F1EA);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    @Override protected void onDestroy() {
        if (previews != null) previews.close();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
