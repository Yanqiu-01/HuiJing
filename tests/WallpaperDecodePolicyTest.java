import app.inkbench.studio.WallpaperDecodePolicy;

public class WallpaperDecodePolicyTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) {
        check(WallpaperDecodePolicy.targetDimension(1080, 2400, 1080, 2400) == 2400, "uses view/display size");
        check(WallpaperDecodePolicy.targetDimension(0, 0, 0, 0) == 1280, "safe fallback target");
        check(WallpaperDecodePolicy.targetDimension(5000, 4000, 5000, 4000) == 4096, "caps target dimension");

        check(WallpaperDecodePolicy.maxPixels(32L * 1024 * 1024) == 1_398_101L, "low memory budget");
        check(WallpaperDecodePolicy.maxPixels(128L * 1024 * 1024) == 5_592_405L, "dynamic pixel budget");
        check(WallpaperDecodePolicy.maxPixels(1024L * 1024 * 1024) == 8_000_000L, "pixel budget cap");

        check(WallpaperDecodePolicy.sampleFor(1080, 1920, 2400, 8_000_000) == 1, "phone-sized source stays full resolution");
        check(WallpaperDecodePolicy.sampleFor(1440, 3200, 3200, 8_000_000) == 1, "tall phone source stays full resolution");
        check(WallpaperDecodePolicy.sampleFor(4000, 6000, 2400, 4_000_000) == 4, "large portrait source respects both limits");
        check(WallpaperDecodePolicy.sampleFor(8000, 4500, 2048, 2_000_000) == 8, "large landscape source respects both limits");
        check(WallpaperDecodePolicy.sampleFor(0, 100, 1000, 1_000_000) == 1, "invalid bounds are harmless");
        System.out.println("PASS: wallpaper target, memory budget, power-of-two sampling and invalid bounds");
    }
}
