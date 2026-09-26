import app.inkbench.studio.PromptHistory;
public class PromptHistoryTest {
 static void eq(String a,String b){if(a==null?b!=null:!a.equals(b))throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  PromptHistory h=new PromptHistory();
  if(h.canUndo())throw new AssertionError();
  h.beforeReplace("first","enhanced");h.beforeReplace("enhanced","again");
  eq(h.undo(),"enhanced");eq(h.restoreOriginal(),"first");
  if(h.canUndo())throw new AssertionError();
  h.beforeReplace("","suggestion");eq(h.undo(),"");
  h.beforeReplace("same","same");if(h.canUndo())throw new AssertionError();
  for(int i=0;i<12;i++)h.beforeReplace("p"+i,"p"+(i+1));
  if(h.steps().size()!=8)throw new AssertionError("unbounded");
  PromptHistory loaded=new PromptHistory();loaded.load(h.original(),h.steps());
  eq(loaded.restoreOriginal(),"p0");
  for(int i=0;i<8;i++)h.undo();eq(h.undo(),"p0");
  if(h.canUndo())throw new AssertionError();
  System.out.println("PASS: successive enhancement undo, restore original, blank original, unchanged reply, bounded history and reload");
 }
}
