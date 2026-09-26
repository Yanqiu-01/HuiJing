package app.inkbench.studio;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import java.io.*;

/** Scoped-storage export with failed pending entries removed. */
public final class Album {
    private Album() { }
    public static synchronized Uri save(Context context, File source, String name) throws Exception {
        android.content.SharedPreferences saved=context.getSharedPreferences("album_exports", Context.MODE_PRIVATE);
        String previous=saved.getString(name, "");
        if (!previous.isEmpty()) {
            Uri uri=Uri.parse(previous);
            if ("file".equals(uri.getScheme())) {
                if (new File(uri.getPath()).exists()) return uri;
            } else {
                try(android.content.res.AssetFileDescriptor fd=context.getContentResolver().openAssetFileDescriptor(uri,"r")) {
                    if(fd!=null) return uri;
                } catch(Exception ignored) { }
            }
        }
        Uri uri=write(context,source,name);
        saved.edit().putString(name,uri.toString()).commit();
        return uri;
    }
    private static Uri write(Context context, File source, String name) throws Exception {
        if(Build.VERSION.SDK_INT>=29) return modern(context,source,name);
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"绘境");
        if(!dir.exists() && !dir.mkdirs()) throw new IOException("无法建立相册目录");
        File dest=new File(dir,name);
        try { copy(source,new FileOutputStream(dest)); }
        catch(Exception e) { dest.delete(); throw e; }
        MediaScannerConnection.scanFile(context,new String[]{dest.getAbsolutePath()},new String[]{"image/png"},null);
        return Uri.fromFile(dest);
    }
    private static Uri modern(Context context,File source,String name) throws Exception {
        ContentResolver r=context.getContentResolver();
        ContentValues v=new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME,name);
        v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/绘境");
        v.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        if(uri==null) throw new IOException("系统相册拒绝写入");
        try {
            OutputStream out=r.openOutputStream(uri);
            if(out==null) throw new IOException("无法打开相册文件");
            copy(source,out);
            v.clear(); v.put(MediaStore.Images.Media.IS_PENDING,0);
            if(r.update(uri,v,null,null)==0) throw new IOException("无法完成相册写入");
            return uri;
        } catch(Exception e) {
            try { r.delete(uri,null,null); } catch(Exception ignored) { }
            throw e;
        }
    }
    private static void copy(File file,OutputStream stream) throws Exception {
        try(OutputStream out=stream; InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[16384]; int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
        }
    }
}
