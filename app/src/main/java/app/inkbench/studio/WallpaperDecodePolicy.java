package app.inkbench.studio;

/** Safe source-size policy for the full-screen wallpaper decoder. */
public final class WallpaperDecodePolicy {
    private static final int MIN_PIXELS = 1_000_000;
    private static final int MAX_PIXELS = 8_000_000;
    private WallpaperDecodePolicy() { }

    public static int targetDimension(int viewWidth, int viewHeight, int displayWidth, int displayHeight) {
        int width = viewWidth > 0 ? viewWidth : displayWidth;
        int height = viewHeight > 0 ? viewHeight : displayHeight;
        int largest = Math.max(width, height);
        if (largest <= 0) return 1280;
        return Math.max(720, Math.min(4096, largest));
    }

    public static long maxPixels(long maxMemoryBytes) {
        long dynamic = maxMemoryBytes > 0 ? maxMemoryBytes / 24L : MIN_PIXELS;
        return Math.max(MIN_PIXELS, Math.min(MAX_PIXELS, dynamic));
    }

    public static int sampleFor(int width, int height, int maxDimension, long pixelLimit) {
        if (width <= 0 || height <= 0) return 1;
        int safeDimension = Math.max(1, maxDimension);
        long safePixels = Math.max(1L, pixelLimit);
        int sample = 1;
        while (sample < (1 << 30)) {
            long sampledWidth = (width + sample - 1L) / sample;
            long sampledHeight = (height + sample - 1L) / sample;
            if (Math.max(sampledWidth, sampledHeight) <= safeDimension
                    && sampledWidth * sampledHeight <= safePixels) return sample;
            sample <<= 1;
        }
        return 1 << 30;
    }
}
