package app.inkbench.studio;

/**
 * Owns the batch-viewpoint option. It is deliberately independent from Android,
 * SharedPreferences, the HTTP client, and the Activity lifecycle.
 */
public final class BatchPromptPolicy {
    public static final String PREF_KEY = "batch_prompt_level_v1";
    public static final int DEFAULT_LEVEL = 35;

    private BatchPromptPolicy() { }

    public static int clamp(int level) {
        return level < 0 ? 0 : (level > 100 ? 100 : level);
    }

    private static final String[][] CAMERA_PLANS = {
            {"保持眼平机位，改为轻微的三分之二侧向视角", "从略高于主体的机位拍摄，强调前景与主体的距离", "从略低机位拍摄，保留原景别并改变画面留白位置"},
            {"保持眼平机位，改从主体另一侧取景", "采用轻微俯视机位，形成清楚的前景、中景、背景层次", "采用略低的三分之二侧向机位，让主体与背景分层"},
            {"保持原景别，轻微移到主体侧前方", "采用略低机位和偏侧构图，主体仍完整且动作不变", "采用较高的斜向机位，利用前景框景并改变留白方向"}
    };

    private static final String[] LOCKED_CAMERA_PLANS = {
            "不改变指定机位与景别，改用左侧前景遮挡并将主体放在画面偏右",
            "不改变指定机位与景别，改用右侧前景层次并将主体放在画面偏左",
            "不改变指定机位与景别，改变主体周围留白和背景层次，避免重复前两张构图"
    };

    public static String compose(String original, int level, int imageIndex) {
        String base = original == null ? "" : original.trim();
        if (base.length() == 0 || imageIndex <= 0) return base;
        int value = clamp(level);
        if (value == 0) return base;

        String plan = CAMERA_PLANS[(imageIndex - 1) % CAMERA_PLANS.length][tier(value)];
        boolean cameraLocked = containsAny(base, "正面构图", "侧面构图", "正面视角", "侧面视角", "特写镜头", "近景镜头", "中景镜头", "远景镜头", "全景镜头", "全身构图", "俯拍镜头", "仰拍镜头", "固定机位", "保持平视", "保持俯视", "保持仰视", "广角镜头", "长焦镜头");
        boolean lightLocked = containsAny(base, "保持逆光", "保持侧光", "保持顺光", "固定主光方向", "不要改变光线", "光线方向保持不变", "必须是夕阳", "必须是日出", "必须是日落");
        if (cameraLocked) {
            plan = LOCKED_CAMERA_PLANS[(imageIndex - 1) % LOCKED_CAMERA_PLANS.length];
        }
        if (lightLocked) {
            plan += "，并保持提示词指定的光线不变";
        }
        return base + "。本批次第" + (imageIndex + 1) + "张，主体、身份、数量、动作、关系和剧情保持不变；"
                + plan + "。不要新增角色、道具、文字、Logo或水印。";
    }

    private static int tier(int level) {
        if (level <= 33) return 0;
        if (level <= 66) return 1;
        return 2;
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    public static String displayValue(int level) {
        return String.format(java.util.Locale.US, "%.2f", clamp(level) / 100f);
    }
}
