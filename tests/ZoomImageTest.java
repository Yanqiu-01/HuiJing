import java.lang.reflect.Method;

public class ZoomImageTest {
    static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    static float near(float value) { return Math.round(value * 1000f) / 1000f; }

    public static void main(String[] args) throws Exception {
        Method clamp = Class.forName("app.inkbench.studio.ZoomImageView")
                .getDeclaredMethod("clampCenter", float.class, float.class, float.class);
        clamp.setAccessible(true);
        check(near((Float) clamp.invoke(null, 0f, 100f, 200f)) == 50f, "small content centered");
        check(near((Float) clamp.invoke(null, 40f, 100f, 200f)) == 50f, "small content ignores offset");
        check(near((Float) clamp.invoke(null, 200f, 200f, 200f)) == 0f, "exact fit");
        check(near((Float) clamp.invoke(null, 0f, 300f, 200f)) == 0f, "overflow keeps left edge at rest");
        check(near((Float) clamp.invoke(null, -200f, 300f, 200f)) == -100f, "overflow clamps far drag");
        check(near((Float) clamp.invoke(null, 60f, 300f, 200f)) == 0f, "overflow clamps opposite drag");
        for (float content : new float[]{10f, 100f, 200f, 300f, 900f}) {
            for (float view : new float[]{50f, 200f, 500f}) {
                for (float offset : new float[]{-900f, -250f, 0f, 17f, 400f}) {
                    float value = (Float) clamp.invoke(null, offset, content, view);
                    if (content <= view) {
                        check(near(value) == near((view - content) / 2f), "centered: " + content + "/" + view);
                    } else {
                        check(value <= 0.001f, "no right gap: " + content + "/" + view);
                        check(value + content >= view - 0.001f, "no left gap: " + content + "/" + view);
                    }
                }
            }
        }

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

        System.out.println("PASS: zoom clamping keeps content inside bounds, paging bounds, caption resolution");
    }
}
