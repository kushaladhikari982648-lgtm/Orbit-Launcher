package net.kdt.pojavlaunch.tasks;

import com.movtery.zalithlauncher.event.sticky.MinecraftVersionValueEvent;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.path.UrlManager;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class AsyncMinecraftDownloader {
    /**
     * Look up a version in an in-memory manifest, without NPE-ing on null entries/ids.
     */
    private static JMinecraftVersionList.Version findVersion(JMinecraftVersionList versionList, String versionString) {
        if (versionList == null || versionList.versions == null || versionString == null) return null;
        for (JMinecraftVersionList.Version version : versionList.versions) {
            if (version != null && versionString.equals(version.id)) return version;
        }
        return null;
    }

    /**
     * Resolve a listed version without touching the network: first the sticky event posted by
     * LauncherActivity, then the manifest cached on disk. The sticky event may be missing when
     * the version list refresh failed or the user launches very early, so the file fallback is
     * what keeps "install/launch 26.1.1" working offline-of-the-event-bus.
     */
    public static JMinecraftVersionList.Version getListedVersion(String versionString) {
        JMinecraftVersionList.Version version = findVersion(getJMinecraftVersionList(), versionString);
        if (version != null) return version;
        return getCachedListedVersion(versionString);
    }

    /** Read {@link PathManager#FILE_VERSION_LIST} synchronously; returns null when unusable. */
    public static JMinecraftVersionList.Version getCachedListedVersion(String versionString) {
        try {
            File versionFile = new File(PathManager.FILE_VERSION_LIST);
            if (!versionFile.canRead() || versionFile.length() == 0) return null;
            JMinecraftVersionList cachedList = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(versionFile), JMinecraftVersionList.class);
            return findVersion(cachedList, versionString);
        } catch (Exception e) {
            Logging.w("AsyncMinecraftDownloader", "Failed to read the cached version list", e);
            return null;
        }
    }

    /**
     * Download a fresh version manifest and look the version up there (refreshing the on-disk
     * cache on success). Last resort for versions missing from a stale cache.
     */
    public static JMinecraftVersionList.Version fetchListedVersion(String versionString) {
        try {
            Logging.i("AsyncMinecraftDownloader", "Refreshing the version list to find " + versionString);
            String manifestJson = DownloadUtils.downloadString(UrlManager.URL_MINECRAFT_VERSION_REPOS);
            JMinecraftVersionList freshList = Tools.GLOBAL_GSON.fromJson(manifestJson, JMinecraftVersionList.class);
            JMinecraftVersionList.Version version = findVersion(freshList, versionString);
            if (version != null) {
                try {
                    Tools.write(PathManager.FILE_VERSION_LIST, manifestJson);
                } catch (Exception e) {
                    Logging.w("AsyncMinecraftDownloader", "Failed to cache the refreshed version list", e);
                }
            }
            return version;
        } catch (Exception e) {
            Logging.w("AsyncMinecraftDownloader", "Failed to refresh the version list", e);
            return null;
        }
    }

    private static JMinecraftVersionList getJMinecraftVersionList() {
        MinecraftVersionValueEvent event = EventBus.getDefault().getStickyEvent(MinecraftVersionValueEvent.class);

        if (event != null) return event.getList();
        else return null;
    }

    public interface DoneListener{
        void onDownloadDone();
        void onDownloadFailed(Throwable throwable);
    }
}
