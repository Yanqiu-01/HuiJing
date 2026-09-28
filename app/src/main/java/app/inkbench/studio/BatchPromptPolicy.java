package app.inkbench.studio;

import java.util.Locale;

/**
 * Builds deterministic, prompt-visible visual variants for one image batch.
 * The seed belongs to the current batch only; it is never sent as an API parameter.
 */
public final class BatchPromptPolicy {
    public static final String PREF_KEY = "batch_prompt_level_v1";
    public static final int DEFAULT_LEVEL = 35;
    private static final long DEFAULT_SEED = 0x4D59535F42415443L;

    private static final String[] COMPOSITIONS = {
            "主体偏左，右侧保留有意义的负空间",
            "主体偏右，左侧保留有意义的负空间",
            "主体居中但采用明显的前后层次和纵深",
            "采用对角线视觉重心，让主体与环境形成方向关系",
            "用前景框景突出主体，背景退到次要层级",
            "让环境占据更大画面比例，主体仍然是唯一视觉焦点",
            "采用紧凑构图强化主体轮廓，减少无关空白",
            "让主体与背景形成清楚的明暗或色彩分区",
            "保留较开阔的呼吸感，让视线从环境回到主体",
            "采用不对称构图，避免模板化的正中摆拍",
            "强化主体与最近环境物的尺度关系，保持画面可读",
            "把视觉重心放在主体与背景交界处的关系上"
    };

    private static final String[] VIEWS = {
            "采用略高机位观察主体与环境的关系",
            "采用略低机位，让主体在画面中更有存在感",
            "从主体侧前方取景，保留清楚的空间纵深",
            "采用三分之二侧向视角，避免正面摆拍",
            "从较远的观察距离取景，让环境参与叙事",
            "采用贴近主体但不改变画幅的观察视角",
            "把视线引向主体与环境交界的位置",
            "采用具有方向性的斜向视点，避免平铺直叙"
    };

    private static final String[] SPACES = {
            "让主体约占画面的三分之一，环境承担明显的空间信息",
            "让主体占据画面主要区域，同时保留可辨认的前景和背景",
            "拉开主体与背景的距离，形成清楚的前景、中景、远景",
            "使用前景遮挡或框景制造层次，但不添加新的物件",
            "把大部分视觉重量放在环境，主体作为明确的视觉锚点",
            "让主体与最近的已有环境元素形成尺度对照",
            "保留一侧较大的留白，让视线有明确的移动路径",
            "使用不对称的空间分配，避免与其他图片只做细节差异"
    };

    private static final String[] COLORS = {
        "以低饱和青灰为主，保留一处温暖色彩作为视觉锚点",
            "采用土黄、赭红和深褐的自然色阶，避免鲜艳塑料感",
            "以冷蓝和灰紫建立安静的色彩关系，局部保留肤色或主体固有色",
            "采用米白、浅绿和木色的柔和色调，层次靠明度区分",
            "让暗部偏冷、受光面偏暖，形成清楚但不夸张的冷暖对比",
            "压低背景饱和度，让主体固有色成为画面最醒目的颜色",
            "使用雾化的中性色，并保留少量高纯度色彩作为焦点",
            "采用自然环境色，不使用统一滤镜式的过度调色"
    };

    private static final String[] LIGHTING = {
            "光线柔和且有明确方向，主体亮部与阴影都保留细节",
            "使用局部侧向光，让主体轮廓从环境中自然分离",
            "让光线从环境缝隙或远处进入，形成可见的明暗递进",
            "采用阴天漫射光，靠细微明度差表现体积而非强烈光晕",
            "保留自然逆光的轮廓亮边，但主体面部和关键细节不能变黑",
            "让主体处于较稳定的环境光中，背景光影只承担层次作用",
            "使用一处温暖主光和较冷的环境填充光，控制对比度",
            "光影像真实现场的瞬间记录，不要舞台追光或人工光效"
    };

    private static final String[] ATMOSPHERES = {
            "空气中有轻微湿润感和远近雾气，整体安静而真实",
            "环境留下少量生活或使用痕迹，画面有具体时间感",
            "背景保持克制和留白，突出主体当下的情绪停顿",
            "增加微妙的风、尘、雾或空气流动感，但不添加新道具",
            "氛围偏安静和治愈，情绪通过距离、光线和材质呈现",
            "氛围偏克制和疏离，避免戏剧化表演与过度煽情",
            "让环境呈现真实的季节或天气触感，不堆叠无关细节",
            "画面像一个偶然捕捉到的真实瞬间，不要商业样板感"
    };

    private static final String[] MATERIALS = {
            "强化木、石、布料或皮肤等已有材质的细微纹理，保持自然",
            "材质表现偏哑光和真实，避免塑料、蜡像和过度锐化",
            "让粗糙与光滑的已有材质形成对比，但不新增物件",
            "保留细小磨损、褶皱、颗粒或水汽等可见环境痕迹",
            "细节清晰但不过度精修，保留真实摄影或手工质感",
            "让主体边缘、接触面和地面关系可信，不出现悬浮感",
            "材质与光线相互作用自然，避免统一的 AI 光泽",
            "只强化原提示词中已经出现且画面可见的材质"
    };

    private static final String[] FOCUSES = {
            "视觉焦点落在主体的眼神、轮廓或最关键的动作关系上",
            "视觉焦点落在主体与环境发生接触的部位",
            "视觉焦点落在主体和背景之间的距离与尺度关系上",
            "视觉焦点落在光线触及主体的边缘和表面细节上",
            "视觉焦点落在主体的姿态和画面留白形成的情绪上",
            "视觉焦点落在一个已有的象征性环境细节上，不新增象征物"
    };

    private BatchPromptPolicy() { }

    public static int clamp(int level) {
        return level < 0 ? 0 : (level > 100 ? 100 : level);
    }

    /** Compatibility overload used by callers that do not own a batch seed. */
    public static String compose(String original, int level, int imageIndex) {
        return compose(original, level, imageIndex, DEFAULT_SEED);
    }

    /**
     * imageIndex is zero-based. Index 0 is always byte-for-byte prompt text after trim.
     * Later images use a deterministic permutation so one batch does not repeat a profile.
     */
    public static String compose(String original, int level, int imageIndex, long batchSeed) {
        String base = original == null ? "" : original.trim();
        if (base.length() == 0 || imageIndex <= 0) return base;
        int value = clamp(level);
        if (value == 0) return base;

        int profile = profileIndex(batchSeed, imageIndex - 1);
        boolean cameraLocked = containsAny(base, "正面构图", "侧面构图", "正面视角", "侧面视角",
                "特写镜头", "近景镜头", "中景镜头", "远景镜头", "全景镜头", "全身构图",
                "俯拍镜头", "仰拍镜头", "固定机位", "保持平视", "保持俯视", "保持仰视",
                "广角镜头", "长焦镜头");
        boolean lightLocked = containsAny(base, "保持逆光", "保持侧光", "保持顺光", "固定主光方向",
                "不要改变光线", "光线方向保持不变", "必须是夕阳", "必须是日出", "必须是日落",
                "固定光线", "光线必须");
        boolean colorLocked = containsAny(base, "固定配色", "配色必须", "色彩必须", "色调必须", "主色必须",
                "必须是红色", "必须是蓝色", "必须是绿色", "必须是黄色", "固定为黑白", "固定单色",
                "颜色不能改变", "色彩不能改变", "保持暖色", "保持冷色");
        boolean materialLocked = containsAny(base, "固定材质", "材质必须", "材质不能改变", "必须是纸纹",
                "必须是木纹", "必须是金属", "必须是玻璃", "固定水彩", "固定水墨", "固定油画");
        boolean emotionLocked = containsAny(base, "表情必须", "表情不能改变", "情绪必须", "情绪不能改变",
                "保持微笑", "保持哭泣", "保持悲伤", "保持愤怒", "保持平静", "保持开心", "保持忧郁");

        StringBuilder variation = new StringBuilder();
        variation.append("本批次第").append(imageIndex + 1)
                .append("张必须是同一主题的明显独立视觉方案；不能只改变纹理、锐度、微小细节或滤镜。只改变未被用户锁定的视觉表现，不改变主体、身份、数量、核心动作、关系、剧情、文字内容和画幅。 ");
        if (!cameraLocked) variation.append(COMPOSITIONS[profile % COMPOSITIONS.length]).append("；");
        if (value >= 20) variation.append(VIEWS[(profile * 3 + 1) % VIEWS.length]).append("；");
        if (value >= 20) variation.append(SPACES[(profile * 5 + 2) % SPACES.length]).append("；");
        if (!colorLocked && value >= 35) variation.append(COLORS[(profile * 7 + 3) % COLORS.length]).append("；");
        if (!lightLocked && value >= 35) variation.append(LIGHTING[(profile * 11 + 4) % LIGHTING.length]).append("；");
        if (value >= 50) variation.append(ATMOSPHERES[(profile * 13 + 5) % ATMOSPHERES.length]).append("；");
        if (!materialLocked && value >= 55) variation.append(MATERIALS[(profile * 17 + 6) % MATERIALS.length]).append("；");
        if (value >= 65) variation.append(FOCUSES[(profile * 19 + 7) % FOCUSES.length]).append("；");
        if (!emotionLocked && value >= 75) {
            variation.append("保留原动作不变，但选择不同的自然瞬间和情绪张力").append("；");
        }
        variation.append("必须让本张与同批其他图片在视点、空间组织或构图重心上有可见差异；不要新增角色、道具、文字、Logo或水印；不要把变化维度写成新的剧情。");
        return base + "。" + variation.toString();
    }

    private static int profileIndex(long seed, int offset) {
        long mixed = seed ^ (seed >>> 33);
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        int start = (int) Math.floorMod(mixed, 12);
        return (start + offset * 5) % 12;
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    public static String displayValue(int level) {
        return String.format(Locale.US, "%.2f", clamp(level) / 100f);
    }
}
