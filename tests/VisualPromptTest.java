import app.inkbench.studio.VisualPrompt;
public class VisualPromptTest {
 static void has(String text,String part){if(!text.contains(part))throw new AssertionError("missing: "+part);}
 static void notHas(String text,String part){if(text.contains(part))throw new AssertionError("unexpected: "+part);}
 public static void main(String[] args){
  String engineering=VisualPrompt.promptEngineeringRules();
  has(engineering,"事实清单");
  has(engineering,"不改主体");
  has(engineering,"一个视觉焦点");
  has(engineering,"只为画面中可见");
  has(engineering,"不要强行加入不可见的手");
  has(engineering,"不要被强行加脏");
  has(engineering,"无可读文字、无Logo、无水印");

  String enhance=VisualPrompt.enhanceRules();
  has(enhance,"主体与动作—环境—构图—光线—材质—限制");
  String summary=VisualPrompt.summaryRules();
  has(summary,"不把多个情节拼进一张图");
  has(summary,"一个可见的象征物");

  String ideas=VisualPrompt.ideaRules();
  has(ideas,"明显不同");has(ideas,"不要套用固定的叙事、特写、环境三分法");
  has(ideas,"不强迫非叙事主题编造动作");has(ideas,"静物、建筑、自然、食物、产品、图案和抽象主题");
  has(ideas,"具体细节");
  if(ideas.contains("强行加入人物") || ideas.contains("每条只写一个瞬间、一个明确景别和一个主光源"))
    throw new AssertionError("idea rules still force a narrow scene template");

  String simple=VisualPrompt.ideaComplexityRule(20);
  String balanced=VisualPrompt.ideaComplexityRule(50);
  String rich=VisualPrompt.ideaComplexityRule(85);
  has(simple, "极简短句");
  has(balanced, "均衡");
  has(rich, "细节丰富");

  String gateway=VisualPrompt.gatewayRules();
  has(gateway,"不新增未要求的角色、道具或Logo");
  has(gateway,"主光源方向与前文一致");
  has(gateway,"多余肢体");
  notHas(gateway,"必须加入手部");

  String standard=VisualPrompt.qualitySuffix("standard");
  String high=VisualPrompt.qualitySuffix("high");
  String ultra=VisualPrompt.qualitySuffix("ultra");
  has(standard,"网关执行约束");has(high,"精细档");has(ultra,"极致档");
  if(high.equals(ultra))throw new AssertionError("quality levels collapsed");

  System.out.println("PASS: layered prompt engineering, scene-aware detail selection and execution guardrails");
 }
}
