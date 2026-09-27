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
        String second = BatchPromptPolicy.compose(original, 50, 2);
        String third = BatchPromptPolicy.compose(original, 50, 3);
        if (!low.contains("轻微")) throw new AssertionError("low policy");
        if (!mid.contains("略高于主体的机位")) throw new AssertionError("mid policy");
        if (!high.contains("略低机位")) throw new AssertionError("high policy");
        if (!mid.contains("主体、身份、数量、动作、关系和剧情保持不变")) throw new AssertionError("constraint");
        if (!mid.startsWith(original + "。")) throw new AssertionError("original not preserved");
        if (mid.equals(second) || mid.equals(third) || second.equals(third)) throw new AssertionError("batch plans repeated");
        String lockedCamera = BatchPromptPolicy.compose("正面构图，全身镜头，红色外套", 100, 1);
        if (!lockedCamera.contains("不改变指定机位与景别")) throw new AssertionError("camera constraint ignored");
        String lockedCamera2 = BatchPromptPolicy.compose("正面全身照，红色外套", 100, 2);
        if (lockedCamera.equals(lockedCamera2)) throw new AssertionError("locked camera fallback repeated");
        String lockedLight = BatchPromptPolicy.compose("保持逆光，人物肖像", 100, 1);
        if (!lockedLight.contains("保持提示词指定的光线不变")) throw new AssertionError("light constraint ignored");
        String ordinaryNight = BatchPromptPolicy.compose("夜景中的人物肖像", 100, 1);
        if (ordinaryNight.contains("保持提示词指定的光线不变")) throw new AssertionError("ordinary lighting description treated as locked");
        String[] batch = new String[4];
        for (int i = 0; i < batch.length; i++) batch[i] = BatchPromptPolicy.compose(original, 100, i);
        if (!batch[0].equals(original)) throw new AssertionError("request one changed");
        if (!batch[1].contains("从略低机位拍摄") || !batch[2].contains("三分之二侧向机位")
                || !batch[3].contains("较高的斜向机位")) throw new AssertionError("three distinct plans missing");
        for (int i = 1; i < batch.length; i++) {
            if (batch[i].equals(batch[0])) throw new AssertionError("request variation missing at " + i);
            for (int j = i + 1; j < batch.length; j++) {
                if (batch[i].equals(batch[j])) throw new AssertionError("request plans repeated");
            }
        }
        if (BatchPromptPolicy.clamp(-2) != 0 || BatchPromptPolicy.clamp(101) != 100) throw new AssertionError("clamp");
        if (!"0.35".equals(BatchPromptPolicy.displayValue(35))) throw new AssertionError("display");
        System.out.println("PASS: new batch prompt policy keeps first image unchanged and clamps levels");
    }
}
