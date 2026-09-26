package app.inkbench.studio;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.transition.*;

/** System-paced animations, with no fixed 60 fps timer and no forced resolution switch. */
public final class Motion {
    private Motion() { }
    public static boolean enabled(Context c) {
        return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }
    public static void appear(View v) {
        v.animate().cancel();
        if (!enabled(v.getContext())) { v.setAlpha(1f); v.setTranslationY(0); return; }
        v.setAlpha(0f); v.setTranslationY(6*v.getResources().getDisplayMetrics().density);
        v.animate().alpha(1f).translationY(0).setDuration(170).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    public static void toggle(ViewGroup parent, View child, boolean open) {
        if (enabled(parent.getContext())) {
            AutoTransition t=new AutoTransition(); t.setDuration(160);
            TransitionManager.beginDelayedTransition(parent,t);
        }
        child.setVisibility(open?View.VISIBLE:View.GONE);
    }
    public static String refresh(Activity a, boolean preferHigh) {
        Display display=a.getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams attrs=a.getWindow().getAttributes();
        attrs.preferredRefreshRate=0;
        if(Build.VERSION.SDK_INT>=23) {
            attrs.preferredDisplayModeId=0;
            if(preferHigh) {
                Display.Mode current=display.getMode(), best=current;
                float bestRate=0;
                for(Display.Mode mode:display.getSupportedModes()) {
                    if(mode.getPhysicalWidth()==current.getPhysicalWidth() && mode.getPhysicalHeight()==current.getPhysicalHeight()
                            && mode.getRefreshRate()<=120.5f && mode.getRefreshRate()>bestRate) {
                        best=mode; bestRate=mode.getRefreshRate();
                    }
                }
                attrs.preferredDisplayModeId=best.getModeId();
                attrs.preferredRefreshRate=best.getRefreshRate();
            }
        } else if(preferHigh) {
            float best=0;
            for(float rate:display.getSupportedRefreshRates()) if(rate<=120.5f && rate>best) best=rate;
            attrs.preferredRefreshRate=best;
        }
        a.getWindow().setAttributes(attrs);
        return (preferHigh?"优先高刷新率":"跟随系统") + " · 屏幕当前报告 " + Math.round(display.getRefreshRate())
                + " Hz（不是实测帧率）";
    }
}
