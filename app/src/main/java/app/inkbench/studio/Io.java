package app.inkbench.studio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

final class Io {
    private Io() {}

    static String readText(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
        reader.close();
        return sb.toString();
    }

    static void writeText(File file, String text) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        writer.write(text);
        writer.close();
    }
}
