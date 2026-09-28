package app.inkbench.studio;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Private, bounded request snapshots for diagnosing gateway failures. */
public final class DiagnosticLog {
    private static final String FILE = "image-diagnostics.ndjson";
    private DiagnosticLog() {}

    public static String record(Context context, String phase, String path, int status,
                                String type, String message, long elapsedMs, int attempt,
                                String model, String size, String responseType) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String line = "{" + field("id", id)
                + "," + field("time", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()))
                + "," + field("phase", phase)
                + "," + field("path", path)
                + ",\"status\":" + status
                + "," + field("type", type)
                + "," + field("message", message)
                + "," + field("model", model)
                + "," + field("size", size)
                + "," + field("responseType", responseType)
                + ",\"elapsedMs\":" + Math.max(0, elapsedMs)
                + ",\"attempt\":" + Math.max(0, attempt) + "}\n";
        try {
            File file = new File(context.getFilesDir(), FILE);
            FileOutputStream out = new FileOutputStream(file, true);
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.close();
            trim(file);
        } catch (Exception ignored) {}
        return id;
    }

    public static String recent(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) return "暂无生图失败诊断";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.addLast(line);
                if (lines.size() > 10) lines.removeFirst();
            }
            StringBuilder out = new StringBuilder();
            for (String item : lines) out.append(item).append('\n');
            return out.length() == 0 ? "暂无生图失败诊断" : out.toString();
        } catch (Exception e) { return "诊断读取失败"; }
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":\"" + sanitize(value) + "\"";
    }

    static String sanitize(String value) {
        if (value == null) return "";
        String s = value.replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1[redacted]")
                .replaceAll("(?i)(api[-_ ]?key\\s*[:=]\\s*)[^\\s,;]+", "$1[redacted]")
                .replaceAll("(?i)(token\\s*[:=]\\s*)[^\\s,;]+", "$1[redacted]")
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
        if (s.length() > 900) s = s.substring(0, 900) + "...";
        return s;
    }

    private static void trim(File file) {
        if (!file.exists() || file.length() <= 96 * 1024) return;
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            int count = in.read(bytes);
            in.close();
            if (count < bytes.length) {
                byte[] copy = new byte[Math.max(0, count)];
                System.arraycopy(bytes, 0, copy, 0, copy.length);
                bytes = copy;
            }
            int start = Math.max(0, bytes.length - 72 * 1024);
            while (start < bytes.length && bytes[start] != '\n') start++;
            FileOutputStream out = new FileOutputStream(file, false);
            int offset = start < bytes.length ? start + 1 : start;
            out.write(bytes, offset, bytes.length - offset);
            out.close();
        } catch (Exception ignored) {}
    }
}
