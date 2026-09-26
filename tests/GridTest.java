import java.util.*;
public class GridTest {
 static class View { }
 static class LinearLayout extends View {
  static class LayoutParams { LayoutParams(int a,int b) {} }
  List<View> children=new ArrayList<>(); Object tag;
  Object getTag(){return tag;} int getChildCount(){return children.size();}
  View getChildAt(int i){return children.get(i);}
  void addView(View v,Object lp){children.add(v);}
 }
 LinearLayout chipRow(){return new LinearLayout();}
 Object gridCellLp(){return null;}
    private void addToGrid(LinearLayout wrap, View child) {
        int columns = (Integer) wrap.getTag();
        LinearLayout row = wrap.getChildCount() == 0 ? null : (LinearLayout) wrap.getChildAt(wrap.getChildCount()-1);
        if (row == null || row.getChildCount() == columns) {
            row = chipRow();
            wrap.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView(child, gridCellLp());
    }
    private void padGrid(LinearLayout wrap) {
        if (wrap.getChildCount()==0) return;
        int columns = (Integer)wrap.getTag();
        LinearLayout row = (LinearLayout)wrap.getChildAt(wrap.getChildCount()-1);
        while(row.getChildCount()<columns) row.addView(new View(), gridCellLp());
    }


 public static void main(String[] args) {
  GridTest t=new GridTest();
  for(int cols=2;cols<=4;cols++) for(int n=1;n<=25;n++) {
   LinearLayout grid=new LinearLayout();grid.tag=cols;
   for(int i=0;i<n;i++) t.addToGrid(grid,new View());
   if(grid.getChildCount()!=(n+cols-1)/cols)throw new AssertionError("row count");
   t.padGrid(grid);
   for(View row:grid.children) if(((LinearLayout)row).getChildCount()!=cols)throw new AssertionError("unequal row");
  }
  System.out.println("PASS: actual grid methods, 75 layouts (2-4 columns, 1-25 items)");
 }
}
