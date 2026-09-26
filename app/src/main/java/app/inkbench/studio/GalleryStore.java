package app.inkbench.studio;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Local gallery. Image bytes live as files; the index is one JSON document. */
public final class GalleryStore {

    public static final class Entry {
        public final String id;
        public final String fileName;
        public final String prompt;
        public final String size;
        public final String mode;
        public final String conversationId;
        public final long createdAt;

        public Entry(String id, String fileName, String prompt, String size, String mode,
                     String conversationId, long createdAt) {
            this.id = id;
            this.fileName = fileName;
            this.prompt = prompt;
            this.size = size;
            this.mode = mode;
            this.conversationId = conversationId;
            this.createdAt = createdAt;
        }
    }

    private static final Object LOCK = new Object();
    private final File dir;
    private final File index;

    public GalleryStore(Context context) {
        dir = new File(context.getFilesDir(), "gallery");
        if (!dir.exists()) dir.mkdirs();
        index = new File(dir, "index.json");
    }

    public synchronized Entry save(byte[] bytes, String prompt, String size, String mode, String conversationId) throws Exception {
        synchronized (LOCK) {
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toHexString((int) (Math.random() * 0xFFFF));
        String fileName = id + ".png";
        File out = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(bytes);
        fos.close();
        Entry entry = new Entry(id, fileName, prompt, size, mode, conversationId == null ? "" : conversationId, System.currentTimeMillis());
        List<Entry> all = read();
        all.add(0, entry);
        write(all);
        return entry;
    
        }
    }

    public synchronized List<Entry> list() {
        synchronized (LOCK) {
        return read();
    
        }
    }

    public synchronized void delete(String id) {
        synchronized (LOCK) {
        List<Entry> all = read();
        List<Entry> kept = new ArrayList<Entry>();
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            if (e.id.equals(id)) {
                new File(dir, e.fileName).delete();
            } else {
                kept.add(e);
            }
        }
        write(kept);
    
        }
    }

    public File fileOf(Entry entry) {
        return new File(dir, entry.fileName);
    }

    public File directory() {
        return dir;
    }

    private List<Entry> read() {
        List<Entry> out = new ArrayList<Entry>();
        if (!index.exists()) return out;
        try {
            String text = new String(new android.util.AtomicFile(index).readFully(), java.nio.charset.StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(
                        o.optString("id"),
                        o.optString("file"),
                        o.optString("prompt"),
                        o.optString("size"),
                        o.optString("mode"),
                        o.optString("conversationId"),
                        o.optLong("createdAt")));
            }
        } catch (Exception ignored) {
            return new ArrayList<Entry>();
        }
        return out;
    }

    private void write(List<Entry> entries) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            JSONObject o = new JSONObject();
            try {
                o.put("id", e.id);
                o.put("file", e.fileName);
                o.put("prompt", e.prompt);
                o.put("size", e.size);
                o.put("mode", e.mode);
                o.put("conversationId", e.conversationId);
                o.put("createdAt", e.createdAt);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        android.util.AtomicFile atomic = new android.util.AtomicFile(index);
        FileOutputStream stream = null;
        try {
            stream = atomic.startWrite();
            stream.write(arr.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            atomic.finishWrite(stream);
        } catch (Exception e) {
            if (stream != null) atomic.failWrite(stream);
            throw new IllegalStateException("无法保存画册索引", e);
        }
    }

}
