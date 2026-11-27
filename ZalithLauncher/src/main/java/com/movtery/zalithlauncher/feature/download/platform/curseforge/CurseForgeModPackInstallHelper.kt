package com.movtery.zalithlauncher.feature.download.platform.curseforge

import com.kdt.mcgui.ProgressLayout
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.download.enums.ModLoader
import com.movtery.zalithlauncher.feature.download.install.InstallHelper
import com.movtery.zalithlauncher.feature.download.item.ModLoaderWrapper
import com.movtery.zalithlauncher.feature.download.item.VersionItem
import com.movtery.zalithlauncher.feature.download.platform.curseforge.CurseForgeCommonUtils.Companion.getDownloadSha1
import com.movtery.zalithlauncher.feature.download.platform.curseforge.CurseForgeCommonUtils.Companion.stringOrNull
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.modpack.install.ModPackUtils
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.file.FileTools
import com.movtery.zalithlauncher.utils.stringutils.StringUtils
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModDownloader
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest.CurseMinecraft
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest.CurseModLoader
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.tasks.SpeedCalculator
import net.kdt.pojavlaunch.utils.FileUtils
import net.kdt.pojavlaunch.utils.ZipUtils
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.math.max

class CurseForgeModPackInstallHelper {
    companion object {
        @Throws(Exception::class)
        fun startInstall(api: ApiHandler, versionItem: VersionItem, customName: String): ModLoaderWrapper? {
            return InstallHelper.installModPack(versionItem, customName) { modpackFile, targetPath ->
                installZip(api, modpackFile, targetPath)
            }
        }

        @Throws(Exception::class)
        fun installZip(api: ApiHandler, zipFile: File, targetPath: File): ModLoaderWrapper? {
            ZipFile(zipFile).use { modpackZipFile ->
                val curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest::class.java
                )
                if (!ModPackUtils.verifyManifest(curseManifest)) {
                    // Throwing (instead of returning null) surfaces the failure: a null
                    // here used to abort the install silently and leave a version folder
                    // without a JSON file, which the version list only offers to delete.
                    Logging.i("CurseForgeModPackInstallHelper", "manifest verification failed")
                    throw IOException(
                        "The downloaded file failed CurseForge modpack verification — " +
                            "it is not a valid modpack archive"
                    )
                }
                var progressUpdateTime = 0L
                val speedCalculator = SpeedCalculator()
                val modDownloader: ModDownloader = getModDownloader(api, targetPath, curseManifest)
                modDownloader.awaitFinish { count: Int, totalCount: Int, downloadedSize: Long ->
                    val currentTime = ZHTools.getCurrentTimeMillis()
                    if (currentTime - progressUpdateTime < 150) return@awaitFinish
                    progressUpdateTime = currentTime

                    ProgressKeeper.submitProgress(
                        ProgressLayout.INSTALL_RESOURCE,
                        max((count.toFloat() / totalCount * 100).toDouble(), 0.0).toInt(),
                        R.string.modpack_download_downloading_mods_fc,
                        count,
                        FileTools.formatFileSize(downloadedSize),
                        totalCount,
                        FileTools.formatFileSize(speedCalculator.feed(downloadedSize))
                    )
                }
                val overridesDir: String = curseManifest.overrides ?: "overrides"
                ZipUtils.zipExtract(modpackZipFile, overridesDir, targetPath)
                return createInfo(curseManifest.minecraft)
            }
        }

        @Throws(Exception::class)
        private fun getModDownloader(
            api: ApiHandler,
            instanceDestination: File,
            curseManifest: CurseManifest
        ): ModDownloader {
            val modDownloader = ModDownloader(File(instanceDestination, "mods"), true)
            // Download link, file name and SHA-1 of all the files with one request per 100 files (POST
            // /v1/mods/files) instead of two or three requests per file, which made big modpacks slow to start
            // downloading and ran into the API's rate limit. Files missing from the answer use the single requests.
            val knownFiles = CurseForgeCommonUtils.getFilesByIds(api, curseManifest.files.map { it.fileID })
            Logging.i("CurseForgeModPackInstallHelper", "File info of ${knownFiles.size}/${curseManifest.files.size} files loaded in batches")
            for (curseFile in curseManifest.files) {
                modDownloader.submitDownload {
                    val known = knownFiles[curseFile.fileID]
                    val url = known?.let { CurseForgeCommonUtils.getDownloadUrl(it) }
                        ?: CurseForgeCommonUtils.getDownloadUrl(api, curseFile.projectID, curseFile.fileID)
                    if (url == null && curseFile.required) throw IOException(
                        "Failed to obtain download URL for ${StringUtils.insertSpace(curseFile.projectID, curseFile.fileID)}"
                    )
                    else if (url == null) return@submitDownload null
                    val fileName = known?.stringOrNull("fileName")?.takeIf { it.isNotBlank() } ?: FileUtils.getFileName(url)
                    val sha1 = if (known != null) CurseForgeCommonUtils.getSha1FromData(known)
                        else getDownloadSha1(api, curseFile.projectID, curseFile.fileID)
                    ModDownloader.FileInfo(url, fileName, sha1)
                }
            }
            return modDownloader
        }

        private fun createInfo(minecraft: CurseMinecraft): ModLoaderWrapper {
            var primaryModLoader: CurseModLoader? = null
            for (modLoader in minecraft.modLoaders) {
                if (modLoader.primary) {
                    primaryModLoader = modLoader
                    break
                }
            }
            if (primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0]
            val modLoaderId = primaryModLoader!!.id
            val dashIndex = modLoaderId.indexOf('-')
            if (dashIndex <= 0) throw IOException("Invalid mod loader id in modpack manifest: $modLoaderId")
            val modLoaderName = modLoaderId.substring(0, dashIndex)
            val modLoaderVersion = modLoaderId.substring(dashIndex + 1)
            Logging.i("CurseForgeModPackInstallHelper",
                StringUtils.insertSpace(modLoaderId, modLoaderName, modLoaderVersion)
            )
            // A null here used to end the install silently: the mods were downloaded but
            // no mod loader step ran, so the version never got its JSON file and the
            // version list called it invalid. Quilt used to fall into this hole.
            val modloader: ModLoader = when (modLoaderName) {
                "forge" -> {
                    Logging.i("ModLoader", "Forge, or Quilt? ...")
                    ModLoader.FORGE
                }
                "neoforge" -> {
                    Logging.i("ModLoader", "NeoForge")
                    ModLoader.NEOFORGE
                }
                "fabric" -> {
                    Logging.i("ModLoader", "Fabric")
                    ModLoader.FABRIC
                }
                "quilt" -> {
                    Logging.i("ModLoader", "Quilt")
                    ModLoader.QUILT
                }
                else -> throw IOException(
                    "Unsupported mod loader \"$modLoaderName\" in the modpack manifest " +
                        "(supported: Forge, NeoForge, Fabric, Quilt)"
                )
            }
            return ModLoaderWrapper(modloader, modLoaderVersion, minecraft.version)
        }
    }
}