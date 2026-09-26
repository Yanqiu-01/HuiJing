package app.inkbench.studio;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.List;

/** User-started image batch. Never replays a possibly billed request after process death. */
public final class JobService extends Service {
    static final String STATE = "image_job";
    static final String EXTRA_BASE="base", EXTRA_KEY="key", EXTRA_PROMPT="prompt",
            EXTRA_SIZE="size", EXTRA_COUNT="count", EXTRA_TIMEOUT="timeout", EXTRA_QUALITY="quality";
    private static final String CHANNEL="inkbench-gen";
    private static final int NOTE=41;
    private static volatile boolean running;
    private volatile boolean stopAfterCurrent, terminating;
    private PowerManager.WakeLock wakeLock;
    private long started;
    private String lastId="";
    private int made, total;
    private int currentImage;
    private int albumSaved, albumFailed;
    private String albumError="";

    public static boolean isRunning() { return running; }
    public IBinder onBind(Intent i) { return null; }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_AFTER_CURRENT".equals(intent.getAction())) {
            stopAfterCurrent=true;
            if (running) state(true,"已请求停止：当前请求结束后不再生成下一张");
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        if (intent==null) { stopSelf(); return START_NOT_STICKY; }
        total=Math.max(1,Math.min(4,intent.getIntExtra(EXTRA_COUNT,1)));
        final String base=intent.getStringExtra(EXTRA_BASE), key=intent.getStringExtra(EXTRA_KEY),
                prompt=intent.getStringExtra(EXTRA_PROMPT), size=intent.getStringExtra(EXTRA_SIZE),
                quality=intent.getStringExtra(EXTRA_QUALITY);
        final int timeout=intent.getIntExtra(EXTRA_TIMEOUT,360);
        started=System.currentTimeMillis();
        running=true;
        try {
            if(Build.VERSION.SDK_INT>=26) {
                NotificationChannel channel=new NotificationChannel(CHANNEL,"图片生成",NotificationManager.IMPORTANCE_LOW);
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            }
            Notification n=notification("准备生成",true);
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTE,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTE,n);
            wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Inkbench:image-batch");
            wakeLock.acquire(125L*60L*1000L);
        } catch(Exception e) {
            state(false,"启动任务失败："+detail(e)); running=false; release(); stopSelf(); return START_NOT_STICKY;
        }
        state(true,"正在生成 1/"+total);
        new Thread(() -> {
            String message;
            try {
                GatewayClient client=new GatewayClient(base,key,timeout).withProgress(updateText -> {
                    if (!terminating) state(true, "第 " + currentImage + "/" + total + " 张 · " + updateText);
                });
                GalleryStore gallery=new GalleryStore(getApplicationContext());
                for(int i=1;i<=total;i++) {
                    if(stopAfterCurrent || terminating) break;
                    currentImage=i;
                    state(true,"正在生成 "+i+"/"+total+" · 已保存 "+made+" 张");
                    List<GatewayClient.ImageItem> images=client.generate(prompt,size,quality);
                    GatewayClient.ImageItem first=images.get(0);
                    GalleryStore.Entry entry=gallery.save(first.bytes,prompt,size,"generate",first.conversationId);
                    lastId=entry.id; made++;
                    try {
                        Album.save(getApplicationContext(), gallery.fileOf(entry), "inkbench-"+entry.id+".png");
                        albumSaved++;
                    } catch(Exception exportError) {
                        albumFailed++;
                        albumError=detail(exportError);
                    }
                    if(!terminating) state(true,"已保存 "+made+"/"+total+" 张");
                }
                message=(stopAfterCurrent?"批次已停止":"生成完成")+" · 已保存 "+made+"/"+total+" 张";
            } catch(Exception e) {
                message="已保存 "+made+"/"+total+" 张；任务中断："+detail(e);
            }
            message += " · 系统相册「绘境」已存 " + albumSaved + " 张";
            if(albumFailed>0) message += "；"+albumFailed+" 张相册保存失败，应用内原图保留，可点补存。"+albumError;
            if(!terminating) state(false,message);
            running=false; release(); stopForeground(false);
            try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE,notification(message,false)); }
            catch(Exception ignored) { }
            stopSelf();
        },"inkbench-batch").start();
        return START_NOT_STICKY;
    }

    private void state(boolean active,String message) {
        getSharedPreferences(STATE,MODE_PRIVATE).edit().putBoolean("active",active)
                .putString("message",message).putString("entry",lastId).putInt("made",made)
                .putInt("albumSaved",albumSaved).putInt("albumFailed",albumFailed).putString("albumError",albumError).putInt("total",total).putLong("started",started).putLong("updated",System.currentTimeMillis()).commit();
        if(active) try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTE,notification(message,true)); }
        catch(Exception ignored) { }
    }
    private Notification notification(String message,boolean active) {
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23) flags|=PendingIntent.FLAG_IMMUTABLE;
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("绘境 · 图片生成")
                .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(PendingIntent.getActivity(this,0,open,flags)).setOngoing(active).setAutoCancel(!active)
                .setOnlyAlertOnce(true);
        if(active) b.setProgress(total,made,true);
        return b.build();
    }
    private static String detail(Exception e) {
        if(e instanceof GatewayClient.ApiException) {
            GatewayClient.ApiException a=(GatewayClient.ApiException)e;
            return (a.status>0?"HTTP "+a.status+" · ":"")+a.getMessage();
        }
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
    private void release() { if(wakeLock!=null && wakeLock.isHeld()) wakeLock.release(); }
    @Override public void onTimeout(int startId,int type) {
        terminating=true; stopAfterCurrent=true;
        state(false,"系统限制了后台运行时间；已保存的图片保留，请检查画册，不会自动重发请求");
        running=false; release(); stopForeground(true); stopSelf();
    }
    @Override public void onDestroy() {
        if(running) {
            terminating=true; stopAfterCurrent=true;
            state(false,"服务已停止；已保存的图片保留，请检查画册");
        }
        running=false; release(); super.onDestroy();
    }
}
