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

    public static String compose(String original, int level, int imageIndex) {
        String base = original == null ? "" : original.trim();
        if (base.length() == 0 || imageIndex <= 0) return base;
        int value = clamp(level);
        if (value == 0) return base;

        String instruction;
        if (value <= 33) {
            instruction = "第" + (imageIndex + 1) + "张只做轻微机位或视角变化";
        } else if (value <= 66) {
            instruction = "第" + (imageIndex + 1) + "张克制调整机位、景别或前后景层次";
        } else {
            instruction = "第" + (imageIndex + 1) + "张明显改变机位、视角和取景层次";
        }
        return base + "。同一批次保持主体、身份、数量、动作、关系和剧情不变，"
                + instruction + "。不要新增角色、道具、文字、Logo或水印。";
    }

    public static String displayValue(int level) {
        return String.format(java.util.Locale.US, "%.2f", clamp(level) / 100f);
    }
}
