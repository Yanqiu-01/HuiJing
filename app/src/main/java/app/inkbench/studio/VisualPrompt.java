package app.inkbench.studio;

/**
 * Two-layer visual prompt skill.
 *
 * The prompt-engineering layer is the primary quality layer: it is sent to
 * the text model that converts a user's idea into a usable image prompt.
 * The execution layer is deliberately short and is appended only when the
 * final image request is sent to the gateway.
 */
public final class VisualPrompt {
    private VisualPrompt() {}

    /**
     * Primary layer for prompt compilation. The model should apply this
     * internally, not repeat it in the returned image prompt.
     */
    public static String promptEngineeringRules() {
        return "提示词工程层（只用于改写需求，不要原样输出这段规则）："
                + "先在内部建立事实清单和画面层级。事实清单包括：主体与身份、数量、动作与瞬间、地点与时代、画幅与景别、机位与视线、光线与色彩、材质与限制。"
                + "用户明确说出的内容是硬约束：不改主体、不改数量、不改关系、不改剧情、不擅自添加关键人物、道具或文字；缺失信息只做克制补全。"
                + "从硬约束中选出一个主视觉焦点，再安排主体、次要环境和背景；不要让背景细节、装饰或气氛词抢走主体。"
                + "把抽象情绪翻译成画面证据：用表情、姿态、人物距离、动作结果、环境痕迹和光影表达，不要只堆‘孤独、治愈、震撼、氛围感’。"
                + "构图、动作和景别必须互相匹配：近景不塞全身叙事，全身图不要求同时看清脸部微纹理；只写一个瞬间、一个视觉焦点和一个明确主光源。"
                + "只为画面中可见且与主体相关的部位补细节：人物特写优先脸、眼睛、发丝和衣领；出现手部时才写手部结构；不要强行加入不可见的手、鞋或复杂道具。"
                + "每个层级只加入少量高信息细节，用发丝走向、布料折痕、纸张纤维、湿润反光、墙面旧痕等可见证据替代‘精致、唯美、高级、细腻、超精细、8K、杰作’。"
                + "光线写清方向、软硬和落在主体上的结果；允许环境反光和补光，但不要安排互相冲突的多个主光源。镜头术语只有在改变构图时才使用，不硬塞相机型号。"
                + "降低模板感时只加入与场景相符的一两处自然变化，例如轻微不对称、自然边缘或使用痕迹；清透二次元风不要被强行加脏、加颗粒、加胶片噪点或夸张瑕疵。"
                + "负面限制只保留与当前画面相关的高风险项，不堆一长串否定词；没有明确要求文字时补充无可读文字、无Logo、无水印。"
                + "最终输出一段连贯、可直接生图的中文提示词，约160至320字；不要标题、解释、Markdown、参数、权重或提示词工程术语。";
    }

    /** Extra focus for the explicit prompt-enhancement action. */
    public static String enhanceRules() {
        return promptEngineeringRules()
                + "增强时先复述并保留用户的核心名词，再按‘主体与动作—环境—构图—光线—材质—限制’补全；不要把普通短句改成脱离原意的海报文案。";
    }

    /** Rules for long-text-to-image extraction. */
    public static String summaryRules() {
        return promptEngineeringRules()
                + "长文提炼时先筛选原文中最明确、最具画面性的一个人物关系或事件，不把多个情节拼进一张图；抽象主题用一个可见的象征物承载。";
    }

    /** Stable alias used by tests and external prompt-compilation checks. */
    public static String gatewayRules() {
        return gatewayGuardrail();
    }

    /** Rules used when the text model creates the three inspiration directions. */
    public static String ideaRules() {
        return "灵感方向要先理解主题自身的视觉可能性，再做有意义的分化；不要套用固定的叙事、特写、环境三分法，也不要默认加入人物、动作或生活场景。"
                + "三条方向至少在两个相关维度上明显不同，差异可以来自题材处理、媒介/风格、主体关系、空间尺度、视角、构图、时间状态、色彩光线、材质或抽象程度；选择与当前主题最匹配的维度，不机械轮换景别。"
                + "每条只写一个画面状态或瞬间，但不强迫非叙事主题编造动作；静物、建筑、自然、食物、产品、图案和抽象主题应通过形态、比例、结构、材料、环境关系或可见象征产生变化。"
                + "把情绪和概念翻译成可见证据：用姿态、距离、形状、空间、颜色、光影、材质、使用痕迹或象征物表达；不要用‘精致、唯美、高级、细腻、电影感’代替画面。"
                + "只加入与主题相关的具体细节。人物方向避免塑料皮肤、巨大玻璃眼、完美对称脸和过度磨皮；其他题材也不要为了显得丰富而添加无关人物、道具、文字或复杂背景。";
    }

    /**
     * Short execution-only guardrail. It must not invent details that the
     * prompt-engineering layer deliberately left out.
     */
    public static String gatewayGuardrail() {
        return "网关执行约束：忠实执行前面的主体、数量、剧情、文字和构图，不新增未要求的角色、道具或Logo。"
                + "保持一个明确瞬间和视觉焦点；主光源方向与前文一致，不用互相冲突的光影。"
                + "只强化前文已经出现且画面可见的细节，避免塑料皮肤、过度磨皮、巨大玻璃眼、完美对称脸、虚假发光、过度锐化和多余肢体。";
    }

    /** Quality is still a prompt hint; the gateway has no independent quality protocol. */
    public static String qualitySuffix(String quality) {
        String guardrail = gatewayGuardrail();
        if ("ultra".equals(quality)) {
            return guardrail
                    + "极致档：在不改变构图的前提下，优先保证主体轮廓、脸部或用户指定焦点清楚，材质层次自然，边缘干净；不要用噪点冒充细节。";
        }
        if ("high".equals(quality)) {
            return guardrail
                    + "精细档：在不改变构图的前提下，保证主体清楚、边缘稳定、前文指定的材质可辨，避免明显模糊和变形。";
        }
        return guardrail;
    }
}
