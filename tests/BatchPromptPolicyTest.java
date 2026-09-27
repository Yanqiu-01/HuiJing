import app.inkbench.studio.BatchPromptPolicy;

public final class BatchPromptPolicyTest {
    public static void main(String[] args) {
        String original = "一只猫坐在窗边";
        long seed = 123456789L;

        if (!original.equals(BatchPromptPolicy.compose(original, 35, 0, seed))) throw new AssertionError("first image changed");
        if (!original.equals(BatchPromptPolicy.compose(original, 0, 1, seed))) throw new AssertionError("zero level changed");
        if (!"".equals(BatchPromptPolicy.compose("", 100, 1, seed))) throw new AssertionError("empty prompt changed");

        String low = BatchPromptPolicy.compose(original, 20, 1, seed);
        String mid = BatchPromptPolicy.compose(original, 50, 1, seed);
        String high = BatchPromptPolicy.compose(original, 100, 1, seed);
        if (!low.contains("视觉重心") && !low.contains("主体") && !low.contains("呼吸感") && !low.contains("构图")) throw new AssertionError("low composition variation missing");
        if (!low.contains("色") || low.contains("材质表现偏哑光")) throw new AssertionError("low-level dimension count wrong");
        if (!mid.contains("光") || !mid.contains("环境") || mid.contains("捕捉更自然的瞬间表情")) throw new AssertionError("mid-level dimensions wrong");
        if (!high.contains("材质") || !high.contains("视觉焦点") || !high.contains("瞬间表情")) throw new AssertionError("high-level variation too conservative");
        if (!high.startsWith(original + "。")) throw new AssertionError("original prompt not preserved");
        if (!high.contains("主体、身份、数量、核心动作、关系、剧情、文字内容和画幅")) throw new AssertionError("hard constraints missing");
        if (!high.contains("不要新增角色、道具、文字、Logo或水印")) throw new AssertionError("guardrail missing");

        String image2 = BatchPromptPolicy.compose(original, 100, 1, seed);
        String image3 = BatchPromptPolicy.compose(original, 100, 2, seed);
        String image4 = BatchPromptPolicy.compose(original, 100, 3, seed);
        if (image2.equals(image3) || image2.equals(image4) || image3.equals(image4)) throw new AssertionError("batch variants repeated");
        if (!image2.equals(BatchPromptPolicy.compose(original, 100, 1, seed))) throw new AssertionError("same image is not deterministic");
        String nextBatch = BatchPromptPolicy.compose(original, 100, 1, seed + 1);
        if (image2.equals(nextBatch)) throw new AssertionError("new batch seed did not vary profile");

        String locked = "一位女性坐在窗边，保持正面全身构图，固定蓝绿色配色，保持逆光，表情平静，核心动作不变";
        String guarded = BatchPromptPolicy.compose(locked, 100, 1, seed);
        if (!guarded.startsWith(locked + "。")) throw new AssertionError("original constraints not preserved");
        if (guarded.contains("冷蓝和灰紫") || guarded.contains("红色为主") || guarded.contains("暖色主光")) throw new AssertionError("locked palette/light contradicted");
        if (!guarded.contains("只改变未被用户锁定的视觉表现")) throw new AssertionError("locked dimensions policy missing");
        String ordinaryNight = BatchPromptPolicy.compose("夜景中的人物肖像", 100, 1, seed);
        if (!ordinaryNight.contains("采用自然环境色") && !ordinaryNight.contains("色彩关系")) throw new AssertionError("ordinary context over-locked");

        if (BatchPromptPolicy.clamp(-2) != 0 || BatchPromptPolicy.clamp(101) != 100) throw new AssertionError("clamp");
        if (!"0.35".equals(BatchPromptPolicy.displayValue(35))) throw new AssertionError("display");
        System.out.println("PASS: batch-seeded multi-dimensional prompt variation, hard constraints, intensity tiers, and retry determinism");
    }
}
