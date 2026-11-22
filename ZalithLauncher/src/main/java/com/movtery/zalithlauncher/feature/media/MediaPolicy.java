package com.movtery.zalithlauncher.feature.media;

import java.util.Locale;

/** Small, testable limits for memory-constrained phones. */
public final class MediaPolicy {
    public static final int QUALITY_480P = 480;
    public static final int QUALITY_720P = 720;
    public static final int QUALITY_1080P = 1080;

    public static final int FPS_30 = 30;
    public static final int FPS_60 = 60;

    private MediaPolicy() {}
    public static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp4") || n.endsWith(".m4v") || n.endsWith(".webm")
                || n.endsWith(".mkv") || n.endsWith(".3gp") || n.endsWith(".mov");
    }
    public static boolean validTexture(int width, int height, boolean cape) {
        if (width < 64 || width > 1024 || (width & (width - 1)) != 0) return false;
        return cape ? height == width / 2 : height == width || height == width / 2;
    }
    public static boolean validPlayerName(String name) { return name.matches("[A-Za-z0-9_]{1,16}"); }
    /** 480p / at most 854 wide, even dimensions for AVC. Never upscale. */
    public static int[] recordingSize(int width, int height) {
        return recordingSize(width, height, QUALITY_480P);
    }

    /**
     * Cap to the requested quality tier (480p = 854x480, 720p = 1280x720, 1080p = 1920x1080),
     * even dimensions for AVC. Never upscale.
     */
    public static int[] recordingSize(int width, int height, int qualityHeight) {
        if (width < 2 || height < 2) throw new IllegalArgumentException("Game surface is not ready");
        int tier = normalizeRecordingQuality(qualityHeight);
        double maxW;
        double maxH;
        if (tier >= QUALITY_1080P) {
            maxW = 1920.0;
            maxH = 1080.0;
        } else if (tier >= QUALITY_720P) {
            maxW = 1280.0;
            maxH = 720.0;
        } else {
            maxW = 854.0;
            maxH = 480.0;
        }
        double scale = Math.min(1.0, Math.min(maxW / width, maxH / height));
        return new int[]{Math.max(2, ((int)(width * scale)) & ~1), Math.max(2, ((int)(height * scale)) & ~1)};
    }

    public static int normalizeRecordingQuality(int qualityHeight) {
        if (qualityHeight >= QUALITY_1080P) return QUALITY_1080P;
        if (qualityHeight >= QUALITY_720P) return QUALITY_720P;
        return QUALITY_480P;
    }

    /**
     * Enforces supported FPS per quality tier:
     * - 720p supports 30 FPS.
     * - 1080p supports 30 FPS and 60 FPS.
     */
    public static int normalizeRecordingFps(int qualityHeight, int requestedFps) {
        int tier = normalizeRecordingQuality(qualityHeight);
        if (tier >= QUALITY_1080P) {
            return requestedFps >= FPS_60 ? FPS_60 : FPS_30;
        }
        if (tier >= QUALITY_720P) {
            return FPS_30;
        }
        return requestedFps > 0 ? Math.min(requestedFps, FPS_30) : 15;
    }

    public static int recordingBitrate(int qualityHeight, int fps) {
        int tier = normalizeRecordingQuality(qualityHeight);
        int normalizedFps = normalizeRecordingFps(tier, fps);
        if (tier >= QUALITY_1080P) {
            return normalizedFps >= FPS_60 ? 12_000_000 : 8_000_000;
        }
        if (tier >= QUALITY_720P) {
            return 4_000_000;
        }
        return 1_500_000;
    }

    public static long frameDelayMs(int fps) {
        int safeFps = Math.max(1, Math.min(fps, FPS_60));
        return Math.max(16L, Math.round(1000.0 / safeFps));
    }
}
