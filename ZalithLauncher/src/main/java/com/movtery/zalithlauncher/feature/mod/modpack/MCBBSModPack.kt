package com.movtery.zalithlauncher.feature.mod.modpack

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.download.enums.ModLoader
import com.movtery.zalithlauncher.feature.download.item.ModLoaderWrapper
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.models.MCBBSPackMeta
import com.movtery.zalithlauncher.feature.mod.models.MCBBSPackMeta.MCBBSAddons
import com.movtery.zalithlauncher.feature.mod.modpack.install.ModPackUtils
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.ProgressDialog
import com.movtery.zalithlauncher.utils.file.FileTools
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.FileUtils
import net.kdt.pojavlaunch.utils.ZipUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

class MCBBSModPack(private val context: Context, private val zipFile: File?) {
    private var installDialog: ProgressDialog? = null
    private var isCanceled = false

    @Throws(IOException::class)
    fun install(versionFolder: File): ModLoaderWrapper? {
        zipFile?.let {
            ZipFile(this.zipFile).use { modpackZipFile ->
                val mcbbsPackMeta = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "mcbbs.packmeta")),
                    MCBBSPackMeta::class.java
                )
                if (!ModPackUtils.verifyMCBBSPackMeta(mcbbsPackMeta)) {
                    // Throwing (instead of returning null) surfaces the failure: a null
                    // here used to abort the install silently and leave a version folder
                    // without a JSON file, which the version list only offers to delete.
                    Logging.i("MCBBSModPack", "manifest verification failed")
                    throw IOException(
                        "The downloaded file failed MCBBS modpack verification — " +
                            "it is not a valid modpack archive"
                    )
                }

                initDialog()

                val overridesDir = "overrides" + File.separatorChar
                val dirNameLen = overridesDir.length

                val fileCounters = AtomicInteger() //文件数量计数
                val length = mcbbsPackMeta.files.size

                for (file in mcbbsPackMeta.files) {
                    if (isCanceled) {
                        cancel(versionFolder)
                        return null
                    }

                    val entry = modpackZipFile.getEntry(overridesDir + file.path)
                    if (entry != null) {
                        val entryName = entry.name
                        val zipDestination = File(versionFolder, entryName.substring(dirNameLen))
                        if (zipDestination.exists() && !file.force) continue

                        val fileHash = FileTools.calculateFileHash(modpackZipFile.getInputStream(entry), "SHA-1")
                        val equals = file.hash == fileHash

                        if (equals) {
                            //如果哈希值一致，则复制文件（文件已存在则根据“强制”设定来决定是否覆盖文件）
                            FileUtils.ensureParentDirectory(zipDestination)

                            modpackZipFile.getInputStream(entry).use { entryInputStream ->
                                Files.newOutputStream(zipDestination.toPath())
                                    .use { outputStream ->
                                        IOUtils.copy(entryInputStream, outputStream)
                                    }
                            }
                            val fileCount = fileCounters.getAndIncrement()
                            TaskExecutors.runInUIThread {
                                installDialog?.updateText(
                                    context.getString(
                                        R.string.select_modpack_local_installing_files,
                                        fileCount,
                                        length
                                    )
                                )
                                installDialog?.updateProgress(
                                    fileCount.toDouble(),
                                    length.toDouble()
                                )
                            }
                        }
                    }
                }

                closeDialog()
                return createInfo(mcbbsPackMeta.addons)
            }
        }
        return null
    }

    private fun initDialog() {
        TaskExecutors.runInUIThread {
            installDialog = ProgressDialog(context) {
                isCanceled = true
                true
            }
            installDialog?.show()
        }
    }

    private fun closeDialog() {
        TaskExecutors.runInUIThread { installDialog?.dismiss() }
    }

    private fun cancel(instanceDestination: File) {
        org.apache.commons.io.FileUtils.deleteQuietly(instanceDestination)
    }

    private fun createInfo(addons: Array<MCBBSAddons?>): ModLoaderWrapper {
        var version = ""
        var modLoader = ""
        var modLoaderVersion = ""
        // "until" (not ".."): the old inclusive range read addons[size] and crashed.
        for (i in 0 until addons.size) {
            val addon = addons[i] ?: continue
            if (addon.id == "game") {
                version = addon.version
                continue
            }
            modLoader = addon.id
            modLoaderVersion = addon.version
            break
        }
        // Returning null here used to end the install silently: mods were extracted
        // but no version JSON was ever written, so the version list called it invalid.
        val modloader = when (modLoader) {
            "forge" -> ModLoader.FORGE
            "neoforge" -> ModLoader.NEOFORGE
            "fabric" -> ModLoader.FABRIC
            else -> throw IOException(
                "Unsupported mod loader \"$modLoader\" in the modpack manifest " +
                    "(supported: Forge, NeoForge, Fabric)"
            )
        }
        return ModLoaderWrapper(modloader, modLoaderVersion, version)
    }
}
