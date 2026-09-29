import java.lang.reflect.Method;

public class ZoomImageTest {
    static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    static float near(float value) { return Math.round(value * 1000f) / 1000f; }

    public static void main(String[] args) throws Exception {
        Method limit = Class.forName("app.inkbench.studio.ZoomImageView")
                .getDeclaredMethod("maxOffset", float.class, float.class);
        limit.setAccessible(true);
        check(near((Float) limit.invoke(null, 100f, 200f)) == 0f, "smaller content cannot pan");
        check(near((Float) limit.invoke(null, 200f, 200f)) == 0f, "exact fit cannot pan");
        check(near((Float) limit.invoke(null, 300f, 200f)) == 50f, "overflow allows half the overflow");
        check(near((Float) limit.invoke(null, 200f * 2f, 200f)) == 100f, "2x zoom allows panning");
        check(near((Float) limit.invoke(null, 200f * 5f, 200f)) == 400f, "5x zoom allows panning");
        for (float content : new float[]{10f, 100f, 200f, 300f, 900f}) {
            for (float view : new float[]{50f, 200f, 500f}) {
                float allowed = (Float) limit.invoke(null, content, view);
                check(allowed >= 0f, "pan limit is never negative");
                for (float offset : new float[]{-900f, -250f, 0f, 17f, 400f}) {
                    float clamped = Math.max(-allowed, Math.min(allowed, offset));
                    check(Math.abs(clamped) <= allowed + 0.001f, "pan stays inside limit");
                    if (content <= view) check(clamped == 0f, "fitted content stays centered");
                    else check(Math.abs(clamped) <= (content - view) / 2f + 0.001f, "no empty edge");
                }
            }
        }

        Method zoomOffset = Class.forName("app.inkbench.studio.ZoomImageView")
                .getDeclaredMethod("zoomOffset", float.class, float.class, float.class, float.class, float.class);
        zoomOffset.setAccessible(true);
        check(near((Float) zoomOffset.invoke(null, 0f, 300f, 500f, 1f, 2f)) == 200f,
                "zoom around left-side focus");
        check(near((Float) zoomOffset.invoke(null, -80f, 300f, 500f, 2f, 3f)) == -20f,
                "zoom preserves focus after panning");
        check(near((Float) zoomOffset.invoke(null, 120f, 200f, 500f, 3f, 1f)) == -160f,
                "zoom back preserves focus");

        Method page = Class.forName("app.inkbench.studio.ViewerActivity")
                .getDeclaredMethod("pageIndex", int.class, int.class, int.class);
        page.setAccessible(true);
        check((Integer) page.invoke(null, 0, 1, 3) == 1, "next page");
        check((Integer) page.invoke(null, 2, 1, 3) == 2, "next at end stays");
        check((Integer) page.invoke(null, 0, -1, 3) == 0, "previous at start stays");
        check((Integer) page.invoke(null, 1, -1, 3) == 0, "previous page");
        check((Integer) page.invoke(null, 1, 1, 1) == 1, "single page");
        check((Integer) page.invoke(null, 1, 1, 0) == 1, "empty gallery");

        Method caption = Class.forName("app.inkbench.studio.ViewerActivity")
                .getDeclaredMethod("captionFor", String[].class, int.class);
        caption.setAccessible(true);
        check("a".equals(caption.invoke(null, new String[]{"a", "b"}, 0)), "first caption");
        check("b".equals(caption.invoke(null, new String[]{"a", "b"}, 1)), "second caption");
        check("".equals(caption.invoke(null, new String[]{"a"}, 5)), "out of range caption");
        check("".equals(caption.invoke(null, new String[]{null}, 0)), "null caption");
        check("".equals(caption.invoke(null, (Object) null, 0)), "null array");

        System.out.println("PASS: zoom pan limits, paging bounds, caption resolution");
    }
}
