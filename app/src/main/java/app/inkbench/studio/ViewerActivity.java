package app.inkbench.studio;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class ViewerActivity extends Activity {
    private PreviewLoader previews;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        previews=new PreviewLoader();
        Motion.refresh(this,getSharedPreferences("inkbench",MODE_PRIVATE).getBoolean("highRefresh",true));
        getWindow().getDecorView().setSystemUiVisibility(0);
        String path=getIntent().getStringExtra("path"), prompt=getIntent().getStringExtra("prompt");
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF101210);
        root.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom()); return i;
        });
        TextView back=new TextView(this);
        back.setText("‹  返回画册"); back.setTextColor(0xFFF3F1EA); back.setTextSize(16);
        back.setTypeface(Typeface.create("sans-serif",0)); back.setPadding(dp(20),dp(16),dp(20),dp(16));
        back.setOnClickListener(v->finish()); root.addView(back);
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        ImageView image=new ImageView(this); image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if(path!=null) previews.load(image,new java.io.File(path),2400,true);
        body.addView(image,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=new TextView(this); caption.setText(prompt==null?"":prompt);
        caption.setTextColor(0xFFF3F1EA); caption.setTextSize(15); caption.setTypeface(Typeface.create("sans-serif",0));
        caption.setTextIsSelectable(true); caption.setPadding(dp(20),dp(16),dp(20),dp(24));
        body.addView(caption); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); root.requestApplyInsets();
    }
    @Override protected void onDestroy() {
        if(previews!=null) previews.close();
        super.onDestroy();
    }
    private int dp(int x) { return Math.round(x*getResources().getDisplayMetrics().density); }
}
