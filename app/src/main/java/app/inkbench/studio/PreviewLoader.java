package app.inkbench.studio;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.util.concurrent.*;

/** Bounded bitmap cache and background decoder; stale view requests are discarded. */
public final class PreviewLoader {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(80),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache = new LruCache<String,Bitmap>((int)Math.min(24*1024*1024L,Runtime.getRuntime().maxMemory()/12)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private volatile boolean closed;
    public void load(ImageView view,File file,int target,boolean animate) {
        final String key=file.getAbsolutePath()+"|"+target+"|"+file.lastModified();
        view.setTag(key);
        Bitmap hit=cache.get(key);
        if(hit!=null) { view.setImageBitmap(hit); return; }
        view.setImageDrawable(null);
        if(closed) return;
        worker.execute(()->{
            Bitmap bitmap=null;
            try {
                BitmapFactory.Options b=new BitmapFactory.Options(); b.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(file.getAbsolutePath(),b);
                BitmapFactory.Options o=new BitmapFactory.Options(); o.inSampleSize=1;
                while(Math.max(b.outWidth,b.outHeight)/(o.inSampleSize*2)>=Math.max(1,target)) o.inSampleSize*=2;
                bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),o);
                if(bitmap!=null) cache.put(key,bitmap);
            } catch (RuntimeException | OutOfMemoryError ignored) { }
            final Bitmap result=bitmap;
            main.post(()->{
                if(!closed && key.equals(view.getTag()) && result!=null) {
                    view.setImageBitmap(result);
                    if(animate) Motion.appear(view);
                }
            });
        });
    }
    public void close() { closed=true; worker.shutdownNow(); main.removeCallbacksAndMessages(null); cache.evictAll(); }
}
