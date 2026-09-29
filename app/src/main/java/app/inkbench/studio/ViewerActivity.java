package app.inkbench.studio;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** Full-screen viewer: paging, pinch/double-tap zoom, and captions revealed with the image. */
public class ViewerActivity extends Activity {
    private PreviewLoader previews;
    private String[] paths = new String[0];
    private String[] prompts = new String[0];
    private int index;
    private ZoomImageView image;
    private TextView caption;
    private TextView position;
    private TextView previous;
    private TextView next;
    private LinearLayout top;
    private LinearLayout controls;
    private ScrollView captionScroll;

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

        top = row();
        TextView back = label("‹  返回", 16);
        back.setOnClickListener(v -> finish());
        position = label("", 14);
        top.addView(back, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(position);
        root.addView(top);

        FrameLayout stage = new FrameLayout(this);
        image = new ZoomImageView(this);
        image.setSwipeListener(direction -> move(direction));
        image.setTapListener(this::toggleChrome);
        stage.addView(image, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        captionScroll = new ScrollView(this);
        captionScroll.setFillViewport(true);
        caption = label("", 15);
        caption.setTextIsSelectable(true);
        caption.setPadding(dp(20), dp(12), dp(20), dp(12));
        captionScroll.addView(caption, new FrameLayout.LayoutParams(-1, -2));
        root.addView(captionScroll, new LinearLayout.LayoutParams(-1, 0, 0.22f));

        controls = row();
        previous = label("‹ 上一张", 16);
        next = label("下一张 ›", 16);
        previous.setGravity(Gravity.START);
        next.setGravity(Gravity.END);
        previous.setOnClickListener(v -> move(-1));
        next.setOnClickListener(v -> move(1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, -2, 1f));
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(controls);

        setContentView(root);
        root.requestApplyInsets();
        showCurrent();
    }

    private void move(int delta) {
        int target = pageIndex(index, delta, paths.length);
        if (target == index) return;
        index = target;
        showCurrent();
    }

    private void toggleChrome() {
        boolean show = top.getVisibility() != View.VISIBLE;
        top.setVisibility(show ? View.VISIBLE : View.GONE);
        controls.setVisibility(show ? View.VISIBLE : View.GONE);
        captionScroll.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showCurrent() {
        boolean has = index >= 0 && index < paths.length;
        previous.setEnabled(has && index > 0);
        next.setEnabled(has && index < paths.length - 1);
        previous.setAlpha(previous.isEnabled() ? 1f : 0.35f);
        next.setAlpha(next.isEnabled() ? 1f : 0.35f);
        position.setText(has ? (index + 1) + " / " + paths.length : "没有图片");
        if (!has) {
            caption.setText("");
            image.setImageDrawable(null);
            return;
        }
        final String text = captionFor(prompts, index);
        previews.load(image, new File(paths[index]), 2400, true, bitmap -> {
            if (bitmap != null) caption.setText(text);
        }, true);
    }

    static int pageIndex(int current, int delta, int count) {
        int target = current + delta;
        if (target < 0 || target >= count) return current;
        return target;
    }

    static String captionFor(String[] prompts, int index) {
      if (prompts == null || index < 0 || index >= prompts.length) return "";
      return prompts[index] == null ? "" : prompts[index];
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
