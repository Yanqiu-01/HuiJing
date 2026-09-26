import app.inkbench.studio.BatchPromptPolicy;

public final class BatchPromptPolicyTest {
    public static void main(String[] args) {
        String original = "一只猫坐在窗边";
        if (!original.equals(BatchPromptPolicy.compose(original, 35, 0))) throw new AssertionError("first image changed");
        if (!original.equals(BatchPromptPolicy.compose(original, 0, 1))) throw new AssertionError("zero level changed");
        if (!"".equals(BatchPromptPolicy.compose("", 100, 1))) throw new AssertionError("empty prompt changed");
        String low = BatchPromptPolicy.compose(original, 20, 1);
        String mid = BatchPromptPolicy.compose(original, 50, 1);
        String high = BatchPromptPolicy.compose(original, 100, 1);
        if (!low.contains("轻微机位")) throw new AssertionError("low policy");
        if (!mid.contains("克制调整")) throw new AssertionError("mid policy");
        if (!high.contains("明显改变")) throw new AssertionError("high policy");
        if (!low.contains("数量、动作、关系和剧情不变")) throw new AssertionError("constraint");
        if (!low.startsWith(original + "。")) throw new AssertionError("original not preserved");
        if (BatchPromptPolicy.clamp(-2) != 0 || BatchPromptPolicy.clamp(101) != 100) throw new AssertionError("clamp");
        if (!"0.35".equals(BatchPromptPolicy.displayValue(35))) throw new AssertionError("display");
        System.out.println("PASS: new batch prompt policy keeps first image unchanged and clamps levels");
    }
}
