package app.inkbench.studio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

/** A full-width selection field whose popup uses the field's outer bounds. */
public final class AlignedDropdown extends TextView {
    public interface Choice { void select(int position); }
    private String[] labels = new String[0];
    private int selected;
    private Choice choice;
    private PopupWindow popup;
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private ViewTreeObserver.OnScrollChangedListener scrollListener;

    public AlignedDropdown(Context context) {
        super(context);
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        setMinHeight(dp(48));
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setPadding(dp(14), dp(12), dp(14), dp(12));
        setTextSize(15);
        setTextColor(0xFF1A1C19);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setFocusable(true);
        setClickable(true);
        setOnClickListener(v -> showChoices());
    }

    public void bind(String[] values, int index, Choice callback) {
        dismissChoices();
        labels = values.clone();
        selected = Math.max(0, Math.min(index, labels.length - 1));
        choice = callback;
        updateLabel();
        // Binding/restoring never fires a selection callback or saves preferences.
    }

    private void updateLabel() {
        String label = labels.length == 0 ? "" : labels[selected];
        setText(label + "  ▾");
        setContentDescription(label + "，点击选择");
    }

    private void showChoices() {
        if (!isEnabled() || labels.length == 0 || getWindowToken() == null || getWidth() <= 0) return;
        dismissChoices();
        final ListView list = new ListView(getContext());
        list.setDivider(null);
        list.setPadding(0, 0, 0, 0);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setTextSize(15);
                row.setTextColor(0xFF1A1C19);
                row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(48));
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                row.setSingleLine(false);
                row.setBackgroundColor(position == selected ? 0x221F6B4A : Color.TRANSPARENT);
                return row;
            }
        });
        Rect frame = new Rect();
        getWindowVisibleDisplayFrame(frame);
        int[] location = new int[2];
        getLocationOnScreen(location);
        int available = Math.max(frame.bottom - location[1] - getHeight(), location[1] - frame.top) - dp(6);
        int limit = Math.max(dp(48), Math.min(dp(320), available));
        int height = 0;
        for (int i = 0; i < labels.length && height < limit; i++) {
            View row = list.getAdapter().getView(i, null, list);
            row.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            height += row.getMeasuredHeight();
        }
        final PopupWindow window = new PopupWindow(list, getWidth(), Math.min(height, limit), true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF7F3EC);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0xFFD9D3C7);
        window.setBackgroundDrawable(background);
        window.setElevation(dp(6));
        window.setOutsideTouchable(true);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setOnDismissListener(() -> {
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(layoutListener);
                observer.removeOnScrollChangedListener(scrollListener);
            }
            observer = null;
            popup = null;
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            selected = position;
            updateLabel();
            window.dismiss();
            if (choice != null) choice.select(position);
        });
        popup = window;
        // PopupWindow anchors in the owner's window (including settings dialogs).
        // Unlike Spinner it does not add the field's internal text padding.
        window.showAsDropDown(this, 0, dp(4), Gravity.LEFT);
        list.setSelection(selected);
        final int width = getWidth();
        layoutListener = () -> {
            int[] now = new int[2];
            getLocationOnScreen(now);
            if (!isShown() || getWidth() != width || now[0] != location[0] || now[1] != location[1]) dismissChoices();
        };
        scrollListener = this::dismissChoices;
        observer = getViewTreeObserver();
        observer.addOnGlobalLayoutListener(layoutListener);
        observer.addOnScrollChangedListener(scrollListener);
    }

    private void dismissChoices() { if (popup != null) popup.dismiss(); }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) dismissChoices();
        setAlpha(enabled ? 1f : 0.5f);
    }
    @Override protected void onDetachedFromWindow() { dismissChoices(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) dismissChoices();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
