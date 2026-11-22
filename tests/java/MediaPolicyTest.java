import com.movtery.zalithlauncher.feature.media.MediaPolicy;

public class MediaPolicyTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        for (String ext : new String[]{"mp4", "M4V", "WebM", "mkv", "3gp", "mov"}) check(MediaPolicy.isVideo("background." + ext));
        for (String name : new String[]{"a.mp4.exe", "a.png", "mp4", ""}) check(!MediaPolicy.isVideo(name));
        for (int w=64; w<=1024; w*=2) {
            check(MediaPolicy.validTexture(w,w,false));
            check(MediaPolicy.validTexture(w,w/2,false));
            check(MediaPolicy.validTexture(w,w/2,true));
            check(!MediaPolicy.validTexture(w,w,true));
        }
        for (int[] size : new int[][]{{0,0},{-1,64},{32,32},{65,65},{2048,2048},{64,48}})
            check(!MediaPolicy.validTexture(size[0],size[1],false));
        for (String name : new String[]{"Player_123", "A", "1234567890123456"}) check(MediaPolicy.validPlayerName(name));
        for (String name : new String[]{"../Player", "", "a/b", "a\\b", "player name", "12345678901234567", "a\n"}) check(!MediaPolicy.validPlayerName(name));
        for (int[] source : new int[][]{{1920,1080},{1280,720},{3840,2160},{640,360},{1080,1920},{2,2},{853,479}}) {
            int[] dest=MediaPolicy.recordingSize(source[0],source[1]);
            check(dest[0]<=854 && dest[1]<=480 && dest[0]<=source[0] && dest[1]<=source[1]);
            check(dest[0]>=2 && dest[1]>=2 && dest[0]%2==0 && dest[1]%2==0);

            int[] dest720=MediaPolicy.recordingSize(source[0],source[1],MediaPolicy.QUALITY_720P);
            check(dest720[0]<=1280 && dest720[1]<=720 && dest720[0]<=source[0] && dest720[1]<=source[1]);
            check(dest720[0]>=2 && dest720[1]>=2 && dest720[0]%2==0 && dest720[1]%2==0);

            int[] dest1080=MediaPolicy.recordingSize(source[0],source[1],MediaPolicy.QUALITY_1080P);
            check(dest1080[0]<=1920 && dest1080[1]<=1080 && dest1080[0]<=source[0] && dest1080[1]<=source[1]);
            check(dest1080[0]>=2 && dest1080[1]>=2 && dest1080[0]%2==0 && dest1080[1]%2==0);
        }
        check(MediaPolicy.normalizeRecordingFps(MediaPolicy.QUALITY_720P, 30) == 30);
        check(MediaPolicy.normalizeRecordingFps(MediaPolicy.QUALITY_720P, 60) == 30);
        check(MediaPolicy.normalizeRecordingFps(MediaPolicy.QUALITY_1080P, 30) == 30);
        check(MediaPolicy.normalizeRecordingFps(MediaPolicy.QUALITY_1080P, 60) == 60);
        check(MediaPolicy.recordingBitrate(MediaPolicy.QUALITY_720P, 30) == 4_000_000);
        check(MediaPolicy.recordingBitrate(MediaPolicy.QUALITY_1080P, 30) == 8_000_000);
        check(MediaPolicy.recordingBitrate(MediaPolicy.QUALITY_1080P, 60) == 12_000_000);
        check(MediaPolicy.frameDelayMs(30) == 33L);
        check(MediaPolicy.frameDelayMs(60) == 17L);
        try { MediaPolicy.recordingSize(0,10); throw new AssertionError(); } catch (IllegalArgumentException expected) {}
        System.out.println("PASS: actual Java media policy");
    }
}
