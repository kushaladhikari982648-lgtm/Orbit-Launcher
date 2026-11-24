package net.kdt.pojavlaunch;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static com.movtery.zalithlauncher.setting.AllStaticSettings.notchSize;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.movtery.zalithlauncher.InfoDistributor;
import com.movtery.zalithlauncher.launch.SdlSupport;
import com.movtery.zalithlauncher.R;
import com.movtery.zalithlauncher.context.ContextExecutor;
import com.movtery.zalithlauncher.utils.LauncherProfiles;
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.feature.version.Version;
import com.movtery.zalithlauncher.task.Task;
import com.movtery.zalithlauncher.ui.activity.BaseActivity;
import com.movtery.zalithlauncher.ui.dialog.EditTextDialog;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.runtime.SelectRuntimeUtils;
import com.movtery.zalithlauncher.utils.stringutils.StringUtils;

import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.lifecycle.ContextExecutorTask;
import net.kdt.pojavlaunch.memory.MemoryHoleFinder;
import net.kdt.pojavlaunch.memory.SelfMapsParser;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftClientInfo;
import net.kdt.pojavlaunch.value.MinecraftLibraryArtifact;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("IOStreamConstructor")
public final class Tools {
    public static final String NOTIFICATION_CHANNEL_DEFAULT = "channel_id";
    public static final float BYTE_TO_MB = 1024 * 1024;
    public static final Gson GLOBAL_GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String LAUNCHERPROFILES_RTPREFIX = "pojav://";
    private final static boolean isClientFirst = false;
    /** Legacy name: the running APK/process ABI, not the physical CPU capabilities. */
    public static int DEVICE_ARCHITECTURE;
    // New since 3.0.0
    public static String DIRNAME_HOME_JRE = "lib";

    /**
     * Checks if the Pojav's storage root is accessible and read-writable
     * @return true if storage is fine, false if storage is not accessible
     */
    public static boolean checkStorageRoot() {
        File externalFilesDir = new File(PathManager.DIR_GAME_HOME);
        //externalFilesDir == null when the storage is not mounted if it was obtained with the context call
        return Environment.getExternalStorageState(externalFilesDir).equals(Environment.MEDIA_MOUNTED);
    }

    public static void buildNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_DEFAULT,
                context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.createNotificationChannel(channel);
    }

    public static void disableSplash(File dir) {
        File configDir = new File(dir, "config");
        if(FileUtils.ensureDirectorySilently(configDir)) {
            File forgeSplashFile = new File(dir, "config/splash.properties");
            String forgeSplashContent = "enabled=true";
            try {
                if (forgeSplashFile.exists()) {
                    forgeSplashContent = Tools.read(forgeSplashFile.getAbsolutePath());
                }
                if (forgeSplashContent.contains("enabled=true")) {
                    Tools.write(forgeSplashFile.getAbsolutePath(),
                            forgeSplashContent.replace("enabled=true", "enabled=false"));
                }
            } catch (IOException e) {
                Logging.w(InfoDistributor.LAUNCHER_NAME, "Could not disable Forge 1.12.2 and below splash screen!", e);
            }
        } else {
            Logging.w(InfoDistributor.LAUNCHER_NAME, "Failed to create the configuration directory");
        }
    }

    public static String fromStringArray(String[] strArr) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < strArr.length; i++) {
            if (i > 0) builder.append(" ");
            builder.append(strArr[i]);
        }

        return builder.toString();
    }

    public static String artifactToPath(DependentLibrary library) {
        if (library.downloads != null &&
            library.downloads.artifact != null &&
            library.downloads.artifact.path != null)
            return library.downloads.artifact.path;
        String[] libInfos = library.name.split(":");

        if (libInfos.length < 3) {
            Logging.e("Tools_artifactToPath", "Invalid library name format: " + library.name);
            return null;
        }

        String groupId = libInfos[0].replace('.', '/');
        String artifactId = libInfos[1];
        String version = libInfos[2];

        String classifier = (libInfos.length > 3) ? "-" + libInfos[3] : "";

        return String.format("%s/%s/%s/%s-%s%s.jar", groupId, artifactId, version, artifactId, version, classifier);
    }

    public static String getClientClasspath(Version version) {
        return new File(version.getVersionPath(), version.getVersionName() + ".jar").getAbsolutePath();
    }

    public static String getLWJGL3ClassPath() {
        StringBuilder libStr = new StringBuilder();
        File lwjgl3Folder = new File(PathManager.DIR_GAME_HOME, "lwjgl3");
        File[] lwjgl3Files = lwjgl3Folder.listFiles();
        if (lwjgl3Files != null) {
            for (File file: lwjgl3Files) {
                // The GLFW stub alone is for the LWJGL 3.4.1 classpath (SdlSupport.getGlfwClassPath), its classes
                // are part of lwjgl-glfw-classes.jar already
                if (file.getName().endsWith(".jar") && !file.getName().equals(SdlSupport.GLFW_STUB_JAR)) {
                    libStr.append(file.getAbsolutePath()).append(":");
                }
            }
        }
        // Remove the ':' at the end
        libStr.setLength(libStr.length() - 1);
        return libStr.toString();
    }

    public static String generateLaunchClassPath(JMinecraftVersionList.Version info, Version minecraftVersion) {
        StringBuilder finalClasspath = new StringBuilder(); //versnDir + "/" + version + "/" + version + ".jar:";

        String[] classpath = generateLibClasspath(info);

        String clientClasspath = getClientClasspath(minecraftVersion);

        if (isClientFirst) {
            finalClasspath.append(clientClasspath);
        }
        for (String jarFile : classpath) {
            if (!FileUtils.exists(jarFile)) {
                Logging.d(InfoDistributor.LAUNCHER_NAME, "Ignored non-exists file: " + jarFile);
                continue;
            }
            finalClasspath.append((isClientFirst ? ":" : "")).append(jarFile).append(!isClientFirst ? ":" : "");
        }
        if (!isClientFirst) {
            finalClasspath.append(clientClasspath);
        }

        return finalClasspath.toString();
    }


    public static DisplayMetrics getDisplayMetrics(BaseActivity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();

        if (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode()) {
            //For devices with free form/split screen, we need window size, not screen size.
            displayMetrics = activity.getResources().getDisplayMetrics();
        } else {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                activity.getDisplay().getRealMetrics(displayMetrics);
            } else { // Removed the clause for devices with unofficial notch support, since it also ruins all devices with virtual nav bars before P
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
            }
            if (!activity.shouldIgnoreNotch()) {
                //Remove notch width when it isn't ignored.
                if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
                    displayMetrics.heightPixels -= notchSize;
                else
                    displayMetrics.widthPixels -= notchSize;
            }
        }
        currentDisplayMetrics = displayMetrics;
        return displayMetrics;
    }

    public static void setFullscreen(Activity activity) {
        final View decorView = activity.getWindow().getDecorView();
        View.OnSystemUiVisibilityChangeListener visibilityChangeListener = visibility -> {
            boolean multiWindowMode = activity.isInMultiWindowMode();
            // When in multi-window mode, asking for fullscreen makes no sense (cause the launcher runs in a window)
            // So, ignore the fullscreen setting when activity is in multi window mode
            if (!multiWindowMode) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                }
            } else {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }

        };
        decorView.setOnSystemUiVisibilityChangeListener(visibilityChangeListener);
        visibilityChangeListener.onSystemUiVisibilityChange(decorView.getSystemUiVisibility()); //call it once since the UI state may not change after the call, so the activity wont become fullscreen
    }

    public static DisplayMetrics currentDisplayMetrics;

    public static void updateWindowSize(BaseActivity activity) {
        currentDisplayMetrics = getDisplayMetrics(activity);

        CallbackBridge.physicalWidth = currentDisplayMetrics.widthPixels;
        CallbackBridge.physicalHeight = currentDisplayMetrics.heightPixels;
    }

    public static float dpToPx(float dp) {
        //Better hope for the currentDisplayMetrics to be good
        return dp * currentDisplayMetrics.density;
    }

    public static float pxToDp(float px){
        //Better hope for the currentDisplayMetrics to be good
        return px / currentDisplayMetrics.density;
    }

    public static void copyAssetFile(Context ctx, String fileName, String output, boolean overwrite) throws IOException {
        copyAssetFile(ctx, fileName, output, new File(fileName).getName(), overwrite);
    }

    public static void copyAssetFile(Context ctx, String fileName, String output, String outputName, boolean overwrite) throws IOException {
        File parentFolder = new File(output);
        FileUtils.ensureDirectory(parentFolder);
        File destinationFile = new File(output, outputName);
        if(!destinationFile.exists() || overwrite){
            try(InputStream inputStream = ctx.getAssets().open(fileName)) {
                try (OutputStream outputStream = new FileOutputStream(destinationFile)){
                    IOUtils.copy(inputStream, outputStream);
                }
            }
        }
    }

    public static String printToString(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.close();
        return stringWriter.toString();
    }

    public static void showError(Context ctx, Throwable e) {
        showError(ctx, e, false);
    }

    public static void showError(final Context ctx, final Throwable e, final boolean exitIfOk) {
        showError(ctx, R.string.generic_error, null ,e, exitIfOk, false);
    }
    public static void showError(final Context ctx, final int rolledMessage, final Throwable e) {
        showError(ctx, R.string.generic_error, ctx.getString(rolledMessage), e, false, false);
    }
    public static void showError(final Context ctx, final String rolledMessage, final Throwable e) {
        showError(ctx, R.string.generic_error, rolledMessage, e, false, false);
    }
    public static void showError(final Context ctx, final String rolledMessage, final Throwable e, boolean exitIfOk) {
        showError(ctx, R.string.generic_error, rolledMessage, e, exitIfOk, false);
    }
    public static void showError(final Context ctx, final int titleId, final Throwable e, final boolean exitIfOk) {
        showError(ctx, titleId, null, e, exitIfOk, false);
    }

    private static void showError(final Context ctx, final int titleId, final String rolledMessage, final Throwable e, final boolean exitIfOk, final boolean showMore) {
        if(e instanceof ContextExecutorTask) {
            ContextExecutor.executeTask((ContextExecutorTask) e);
            return;
        }
        Logging.e("ShowError", printToString(e));

        Runnable runnable = () -> {
            final String errMsg = showMore ? printToString(e) : rolledMessage != null ? rolledMessage : e.getMessage();
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx, R.style.CustomAlertDialogTheme)
                    .setTitle(titleId)
                    .setMessage(errMsg)
                    .setPositiveButton(android.R.string.ok, (p1, p2) -> {
                        if(exitIfOk) {
                            if (ctx instanceof MainActivity) {
                                ZHTools.killProcess();
                            } else if (ctx instanceof Activity) {
                                ((Activity) ctx).finish();
                            }
                        }
                    })
                    .setNegativeButton(showMore ? R.string.error_show_less : R.string.error_show_more, (p1, p2) -> showError(ctx, titleId, rolledMessage, e, exitIfOk, !showMore))
                    .setNeutralButton(android.R.string.copy, (p1, p2) -> {
                        StringUtils.copyText("error", printToString(e), ctx);
                        if(exitIfOk) {
                            if (ctx instanceof MainActivity) {
                                ZHTools.killProcess();
                            } else {
                                ((Activity) ctx).finish();
                            }
                        }
                    })
                    .setCancelable(!exitIfOk);
            try {
                builder.show();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        };

        if (ctx instanceof Activity) {
            ((Activity) ctx).runOnUiThread(runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Show the error remotely in a context-aware fashion. Has generally the same behaviour as
     * Tools.showError when in an activity, but when not in one, sends a notification that opens an
     * activity and calls Tools.showError().
     * NOTE: If the Throwable is a ContextExecutorTask and when not in an activity,
     * its executeWithApplication() method will never be called.
     * @param e the error (throwable)
     */
    public static void showErrorRemote(Throwable e) {
        showErrorRemote(null, e);
    }
    public static void showErrorRemote(Context context, int rolledMessage, Throwable e) {
        showErrorRemote(context.getString(rolledMessage), e);
    }
    public static void showErrorRemote(String rolledMessage, Throwable e) {
        // I WILL embrace layer violations because Android's concept of layers is STUPID
        // We live in the same process anyway, why make it any more harder with this needless
        // abstraction?

        // Add your Context-related rage here
        ContextExecutor.executeTask(new ShowErrorActivity.RemoteErrorTask(e, rolledMessage));
    }

    /**
     * Evaluate a library's OS rules for the device we run on (Android reports os.name "Linux").
     * Rules are applied in order and the last matching rule wins; when no rule matches, the
     * default is the opposite of the first rule (Mojang writes either [{allow}, {disallow, osx}]
     * or [{allow, osx}] — "allow there only"). Names like osx-arm64 or windows-arm64 simply do
     * not match "Linux", so desktop-only libraries are excluded on every Android ABI.
     */
    static boolean checkRules(JMinecraftVersionList.Arguments.ArgValue.ArgRules[] rules) {
        if (rules == null || rules.length == 0) return true; // always allow
        boolean allowed = rules[0] != null && !"allow".equals(rules[0].action);
        String osName = System.getProperty("os.name", "linux").toLowerCase(Locale.ROOT);
        for (JMinecraftVersionList.Arguments.ArgValue.ArgRules rule : rules) {
            if (rule == null || rule.action == null) continue;
            JMinecraftVersionList.Arguments.ArgValue.ArgRules.ArgOS os = rule.os;
            if (os != null && os.name != null) {
                if (!osName.equalsIgnoreCase(os.name)) continue; // rule targets another OS
            } else if (os != null && os.version != null) {
                // Version guards are written for desktop OS builds; Android can never match
                // them, and they carry no OS name, so they are ignored here.
                continue;
            }
            allowed = "allow".equals(rule.action);
        }
        return allowed;
    }

    /**
     * Whether the Maven classifier of a library name (the part after the 4th colon, e.g.
     * {@code io.netty:netty-transport-native-epoll:4.2.7.Final:linux-x86_64}) matches this
     * device's CPU architecture and OS. This keeps the x86_64 netty natives out of 32-bit and
     * 64-bit ARM builds (and vice versa) and drops mac/windows classifier artifacts everywhere.
     */
    public static boolean classifierSupportedByDevice(String libName) {
        if (libName == null) return true;
        int lastColon = libName.lastIndexOf(':');
        if (lastColon < 0 || lastColon == libName.length() - 1) return true;
        String classifier = libName.substring(lastColon + 1);
        // No classifier: the last segment is the version (versions start with a digit).
        if (classifier.isEmpty() || Character.isDigit(classifier.charAt(0))) return true;
        String lower = classifier.toLowerCase(Locale.ROOT);
        if (lower.startsWith("linux-x86_64")) return DEVICE_ARCHITECTURE == Architecture.ARCH_X86_64;
        if (lower.startsWith("linux-aarch_64") || lower.startsWith("linux-arm64")) return DEVICE_ARCHITECTURE == Architecture.ARCH_ARM64;
        if (lower.startsWith("linux-x86")) return DEVICE_ARCHITECTURE == Architecture.ARCH_X86;
        if (lower.startsWith("linux-arm")) return DEVICE_ARCHITECTURE == Architecture.ARCH_ARM;
        if (lower.startsWith("osx") || lower.startsWith("macos") || lower.startsWith("darwin")) return false;
        if (lower.startsWith("windows")) return false;
        if (lower.startsWith("natives-osx") || lower.startsWith("natives-macos")
                || lower.startsWith("natives-windows")) return false;
        return true;
    }

    /** Whether a library may be downloaded and put on the classpath on this device. */
    public static boolean isLibraryEnabledForDevice(DependentLibrary library) {
        if (library == null) return false;
        return checkRules(library.rules) && classifierSupportedByDevice(library.name);
    }

    /**
     * Fold package-style (formatVersion 2) metadata into the classic fields the launcher
     * consumes, so version JSONs from either format download and launch identically.
     * Safe to call on already-classic JSON — every step is a no-op when the field exists.
     */
    public static void normalizeVersionInfo(JMinecraftVersionList.Version versionInfo, String fallbackId) {
        if (versionInfo == null) return;
        // "version" replaces "id" in package-style files.
        if (!isValidString(versionInfo.id)) {
            if (isValidString(versionInfo.version)) {
                versionInfo.id = versionInfo.version;
            } else if (!isValidString(versionInfo.inheritsFrom)) {
                // Vanilla profile: the id doubles as the launch version name. Loader profiles
                // (inheritsFrom set) keep a null id so inheritance keeps using the parent's.
                versionInfo.id = fallbackId;
            }
        }
        // The client jar lives under mainJar instead of downloads.client.
        if ((versionInfo.downloads == null || versionInfo.downloads.get("client") == null)
                && versionInfo.mainJar != null && versionInfo.mainJar.downloads != null
                && versionInfo.mainJar.downloads.artifact != null) {
            MinecraftLibraryArtifact artifact = versionInfo.mainJar.downloads.artifact;
            if (isValidString(artifact.url)) {
                MinecraftClientInfo clientInfo = new MinecraftClientInfo();
                clientInfo.url = artifact.url;
                clientInfo.sha1 = artifact.sha1;
                clientInfo.size = artifact.size;
                if (versionInfo.downloads == null) versionInfo.downloads = new HashMap<>();
                versionInfo.downloads.put("client", clientInfo);
            }
        }
        // The top-level "assets" name is omitted; its value equals the asset index id.
        if (!isValidString(versionInfo.assets) && versionInfo.assetIndex != null
                && isValidString(versionInfo.assetIndex.id)) {
            versionInfo.assets = versionInfo.assetIndex.id;
        }
        // javaVersion is replaced by the compatibleJavaMajors list.
        if (versionInfo.javaVersion == null && versionInfo.compatibleJavaMajors != null) {
            int major = 0;
            for (int candidate : versionInfo.compatibleJavaMajors) major = Math.max(major, candidate);
            if (major > 0) {
                JMinecraftVersionList.JavaVersionInfo javaVersionInfo = new JMinecraftVersionList.JavaVersionInfo();
                javaVersionInfo.majorVersion = major;
                versionInfo.javaVersion = javaVersionInfo;
            }
        }
        synthesizeLwjglMarker(versionInfo);
    }

    /**
     * Package-style metadata references LWJGL through "requires: org.lwjgl3" instead of listing
     * it in libraries. Re-add the marker entry so SdlSupport's LWJGL version detection and the
     * classpath filters (which skip org.lwjgl) keep working unchanged.
     */
    private static void synthesizeLwjglMarker(JMinecraftVersionList.Version versionInfo) {
        if (versionInfo.requires == null) return;
        String suggested = null;
        for (JMinecraftVersionList.PackageRequirement requirement : versionInfo.requires) {
            if (requirement != null && "org.lwjgl3".equals(requirement.uid) && isValidString(requirement.suggests)) {
                suggested = requirement.suggests;
                break;
            }
        }
        if (!isValidString(suggested)) return;
        DependentLibrary[] libraries = versionInfo.libraries;
        if (libraries != null) {
            for (DependentLibrary library : libraries) {
                if (library != null && library.name != null && library.name.startsWith("org.lwjgl:lwjgl:")) {
                    return; // already listed explicitly
                }
            }
        }
        DependentLibrary marker = new DependentLibrary();
        marker.name = "org.lwjgl:lwjgl:" + suggested;
        if (libraries == null) {
            versionInfo.libraries = new DependentLibrary[]{marker};
        } else {
            DependentLibrary[] extended = Arrays.copyOf(libraries, libraries.length + 1);
            extended[libraries.length] = marker;
            versionInfo.libraries = extended;
        }
    }

    public static void preProcessLibraries(DependentLibrary[] libraries) {
        if (libraries == null) return;
        for (DependentLibrary libItem : libraries) {
            if (libItem == null || libItem.name == null) continue;
            String[] nameSegments = libItem.name.split(":");
            if (nameSegments.length < 3) continue;
            String[] version = nameSegments[2].split("\\.");
            int majorVersion, minorVersion;
            try {
                majorVersion = Integer.parseInt(version[0]);
                minorVersion = version.length > 1 ? Integer.parseInt(version[1]) : 0;
            } catch (NumberFormatException e) {
                continue; // non-numeric (e.g. snapshot) version segment: nothing to rewrite
            }
            if (libItem.name.startsWith("net.java.dev.jna:jna:")) {
                // Special handling for LabyMod 1.8.9, Forge 1.12.2(?) and oshi
                // we have libjnidispatch 5.13.0 in jniLibs directory
                if (majorVersion >= 5 && minorVersion >= 13)
                    continue;
                Logging.d(InfoDistributor.LAUNCHER_NAME, "Library " + libItem.name + " has been changed to version 5.13.0");
                createLibraryInfo(libItem);
                libItem.name = "net.java.dev.jna:jna:5.13.0";
                libItem.downloads.artifact.path = "net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar";
                libItem.downloads.artifact.sha1 = "1200e7ebeedbe0d10062093f32925a912020e747";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar";
            } else if (libItem.name.startsWith("com.github.oshi:oshi-core:")) {
                //if (Integer.parseInt(version[0]) >= 6 && Integer.parseInt(version[1]) >= 3) return;
                // FIXME: ensure compatibility

                if (majorVersion != 6 || minorVersion != 2)
                    continue;
                Logging.d(InfoDistributor.LAUNCHER_NAME, "Library " + libItem.name + " has been changed to version 6.3.0");
                createLibraryInfo(libItem);
                libItem.name = "com.github.oshi:oshi-core:6.3.0";
                libItem.downloads.artifact.path = "com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar";
                libItem.downloads.artifact.sha1 = "9e98cf55be371cafdb9c70c35d04ec2a8c2b42ac";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/com/github/oshi/oshi-core/6.3.0/oshi-core-6.3.0.jar";
            } else if (libItem.name.startsWith("org.ow2.asm:asm-all:")) {
                // Early versions of the ASM library get repalced with 5.0.4 because Pojav's LWJGL is compiled for
                // Java 8, which is not supported by old ASM versions. Mod loaders like Forge, which depend on this
                // library, often include lwjgl in their class transformations, which causes errors with old ASM versions.
                if (majorVersion >= 5) continue;
                Logging.d(InfoDistributor.LAUNCHER_NAME, "Library " + libItem.name + " has been changed to version 5.0.4");
                createLibraryInfo(libItem);
                libItem.name = "org.ow2.asm:asm-all:5.0.4";
                libItem.url = null;
                libItem.downloads.artifact.path = "org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar";
                libItem.downloads.artifact.sha1 = "e6244859997b3d4237a552669279780876228909";
                libItem.downloads.artifact.url = "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/5.0.4/asm-all-5.0.4.jar";
            }
        }
    }

    private static void createLibraryInfo(DependentLibrary library) {
        if(library.downloads == null || library.downloads.artifact == null)
            library.downloads = new DependentLibrary.LibraryDownloads(new MinecraftLibraryArtifact());
    }

    public static String[] generateLibClasspath(JMinecraftVersionList.Version info) {
        List<String> libDir = new ArrayList<>();
        if (info.libraries == null) return new String[0];
        for (DependentLibrary libItem : info.libraries) {
            if (!isLibraryEnabledForDevice(libItem)) continue;

            String libName = libItem.name;
            if (libName == null) continue;

            if (libName.contains("org.lwjgl") ||
                libName.contains("jinput-platform") ||
                libName.contains("twitch-platform")
            ) {
                Logging.d(InfoDistributor.LAUNCHER_NAME, "Ignored unusable dependency: " + libName);
                continue;
            }

            String libArtifactPath = artifactToPath(libItem);
            if (libArtifactPath == null) continue;
            libDir.add(ProfilePathHome.getLibrariesHome() + "/" + libArtifactPath);
        }
        return libDir.toArray(new String[0]);
    }

    public static JMinecraftVersionList.Version getVersionInfo(Version version) {
        return getVersionInfo(version, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JMinecraftVersionList.Version getVersionInfo(Version version, boolean skipInheriting) {
        try {
            JMinecraftVersionList.Version customVer = Tools.GLOBAL_GSON.fromJson(read(new File(version.getVersionPath(), version.getVersionName() + ".json")), JMinecraftVersionList.Version.class);
            if (customVer == null) {
                throw new RuntimeException("Version JSON is empty for " + version.getVersionName());
            }
            Tools.normalizeVersionInfo(customVer, version.getVersionName());
            if (skipInheriting || customVer.inheritsFrom == null || customVer.inheritsFrom.equals(customVer.id)) {
                preProcessLibraries(customVer.libraries);
            } else {
                JMinecraftVersionList.Version inheritsVer;
                //If it won't download, just search for it
                try {
                    inheritsVer = Tools.GLOBAL_GSON.fromJson(read(version.getVersionsFolder() + "/" + customVer.inheritsFrom + "/" + customVer.inheritsFrom + ".json"), JMinecraftVersionList.Version.class);
                } catch (IOException e) {
                    throw new RuntimeException("Can't find the source version for " + version.getVersionName() + " (req version=" + customVer.inheritsFrom + ")");
                }
                if (inheritsVer == null) {
                    throw new RuntimeException("Source version JSON is empty for " + customVer.inheritsFrom);
                }
                Tools.normalizeVersionInfo(inheritsVer, customVer.inheritsFrom);
                //inheritsVer.inheritsFrom = inheritsVer.id;
                insertSafety(inheritsVer, customVer,
                        "assetIndex", "assets", "id",
                        "mainClass", "minecraftArguments",
                        "releaseTime", "time", "type"
                );

                // Go through the libraries, remove the ones overridden by the custom version
                if (inheritsVer.libraries == null) inheritsVer.libraries = new DependentLibrary[0];
                if (customVer.libraries == null) customVer.libraries = new DependentLibrary[0];
                List<DependentLibrary> inheritLibraryList = new ArrayList<>(Arrays.asList(inheritsVer.libraries));
                outer_loop:
                for(DependentLibrary library : customVer.libraries){
                    if (library == null || library.name == null || !library.name.contains(":")) continue;
                    // Clean libraries overridden by the custom version
                    String libName = library.name.substring(0, library.name.lastIndexOf(":"));

                    for(DependentLibrary inheritLibrary : inheritLibraryList) {
                        if (inheritLibrary == null || inheritLibrary.name == null || !inheritLibrary.name.contains(":")) continue;
                        String inheritLibName = inheritLibrary.name.substring(0, inheritLibrary.name.lastIndexOf(":"));

                        if(libName.equals(inheritLibName)){
                            Logging.d(InfoDistributor.LAUNCHER_NAME, "Library " + libName + ": Replaced version " +
                                    libName.substring(libName.lastIndexOf(":") + 1) + " with " +
                                    inheritLibName.substring(inheritLibName.lastIndexOf(":") + 1));

                            // Remove the library , superseded by the overriding libs
                            inheritLibraryList.remove(inheritLibrary);
                            continue outer_loop;
                        }
                    }
                }

                // Fuse libraries
                inheritLibraryList.addAll(Arrays.asList(customVer.libraries));
                inheritsVer.libraries = inheritLibraryList.toArray(new DependentLibrary[0]);
                preProcessLibraries(inheritsVer.libraries);


                // Inheriting Minecraft 1.13+ with append custom args
                if (inheritsVer.arguments != null && customVer.arguments != null) {
                    List totalArgList = new ArrayList(Arrays.asList(inheritsVer.arguments.game));

                    int nskip = 0;
                    for (int i = 0; i < customVer.arguments.game.length; i++) {
                        if (nskip > 0) {
                            nskip--;
                            continue;
                        }

                        Object perCustomArg = customVer.arguments.game[i];
                        if (perCustomArg instanceof String) {
                            String perCustomArgStr = (String) perCustomArg;
                            // Check if there is a duplicate argument on combine
                            if (perCustomArgStr.startsWith("--") && totalArgList.contains(perCustomArgStr)) {
                                perCustomArg = customVer.arguments.game[i + 1];
                                if (perCustomArg instanceof String) {
                                    perCustomArgStr = (String) perCustomArg;
                                    // If the next is argument value, skip it
                                    if (!perCustomArgStr.startsWith("--")) {
                                        nskip++;
                                    }
                                }
                            } else {
                                totalArgList.add(perCustomArgStr);
                            }
                        } else if (!totalArgList.contains(perCustomArg)) {
                            totalArgList.add(perCustomArg);
                        }
                    }

                    inheritsVer.arguments.game = totalArgList.toArray(new Object[0]);
                }

                customVer = inheritsVer;
            }

            // LabyMod 4 sets version instead of majorVersion
            if (customVer.javaVersion != null && customVer.javaVersion.majorVersion == 0) {
                customVer.javaVersion.majorVersion = customVer.javaVersion.version;
            }
            return customVer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Prevent NullPointerException
    private static void insertSafety(JMinecraftVersionList.Version targetVer, JMinecraftVersionList.Version fromVer, String... keyArr) {
        for (String key : keyArr) {
            Object value = null;
            try {
                Field fieldA = fromVer.getClass().getDeclaredField(key);
                value = fieldA.get(fromVer);
                if (((value instanceof String) && !((String) value).isEmpty()) || value != null) {
                    Field fieldB = targetVer.getClass().getDeclaredField(key);
                    fieldB.set(targetVer, value);
                }
            } catch (Throwable th) {
                Logging.w(InfoDistributor.LAUNCHER_NAME, "Unable to insert " + key + "=" + value, th);
            }
        }
    }

    public static String read(InputStream is) throws IOException {
        String readResult = IOUtils.toString(is, StandardCharsets.UTF_8);
        is.close();
        return readResult;
    }

    public static String read(String path) throws IOException {
        return read(new FileInputStream(path));
    }

    public static String read(File path) throws IOException {
        return read(new FileInputStream(path));
    }

    public static void write(String path, String content) throws IOException {
        File file = new File(path);
        FileUtils.ensureParentDirectory(file);
        try(FileOutputStream outStream = new FileOutputStream(file)) {
            IOUtils.write(content, outStream);
        }
    }

    public interface DownloaderFeedback {
        void updateProgress(long curr, long max);
    }


    public static boolean compareSHA1(File f, String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = new FileInputStream(f)) {
                sha1_dst = new String(Hex.encodeHex(org.apache.commons.codec.digest.DigestUtils.sha1(is)));
            }
            if(sourceSHA != null) {
                return sha1_dst.equalsIgnoreCase(sourceSHA);
            } else{
                return true; // fake match
            }
        }catch (IOException e) {
            Logging.i("SHA1","Fake-matching a hash due to a read error",e);
            return true;
        }
    }

    public static void ignoreNotch(boolean shouldIgnore, BaseActivity activity){
        if (SDK_INT >= P) {
            if (shouldIgnore) {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } else {
                activity.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            }
            activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            Tools.updateWindowSize(activity);
        }
    }

    public static int getTotalDeviceMemory(Context ctx){
        ActivityManager actManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return (int) (memInfo.totalMem / 1048576L);
    }

    public static int getFreeDeviceMemory(Context ctx){
        ActivityManager actManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return (int) (memInfo.availMem / 1048576L);
    }

    private static int internalGetMaxContinuousAddressSpaceSize() throws Exception{
        MemoryHoleFinder memoryHoleFinder = new MemoryHoleFinder();
        new SelfMapsParser(memoryHoleFinder).run();
        long largestHole = memoryHoleFinder.getLargestHole();
        if(largestHole == -1) return -1;
        else return (int)(largestHole / 1048576L);
    }

    public static int getMaxContinuousAddressSpaceSize() {
        try {
            return internalGetMaxContinuousAddressSpaceSize();
        }catch (Exception e){
            Logging.w("Tools", "Failed to find the largest uninterrupted address space");
            return -1;
        }
    }

    public static int getDisplayFriendlyRes(int displaySideRes, float scaling) {
        int display = (int) (displaySideRes * scaling);
        if (display % 2 != 0) display --;
        return display;
    }

    public static String getFileName(Context ctx, Uri uri) {
        Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
        if(c == null) return uri.getLastPathSegment(); // idk myself but it happens on asus file manager
        c.moveToFirst();
        int columnIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(columnIndex == -1) return uri.getLastPathSegment();
        String fileName = c.getString(columnIndex);
        c.close();
        return fileName;
    }

    public static void backToMainMenu(FragmentActivity fragmentActivity) {
        fragmentActivity.getSupportFragmentManager().popBackStack(MainMenuFragment.TAG, 0);
    }

    /** Remove the current fragment */
    public static void removeCurrentFragment(FragmentActivity fragmentActivity){
        fragmentActivity.getSupportFragmentManager().popBackStack();
    }

    public static void installMod(Activity activity, boolean customJavaArgs) {
        if (MultiRTUtils.getExactJreName(8) == null) {
            Toast.makeText(activity, R.string.multirt_nojava8rt, Toast.LENGTH_LONG).show();
            return;
        }

        if(!customJavaArgs){ // Launch the intent to get the jar file
            if(!(activity instanceof LauncherActivity))
                throw new IllegalStateException("Cannot start Mod Installer without LauncherActivity");
            LauncherActivity launcherActivity = (LauncherActivity)activity;
            launcherActivity.modInstallerLauncher.launch(null);
            return;
        }

        // install mods with custom arguments
        new EditTextDialog.Builder(activity)
                .setTitle(R.string.dialog_select_jar)
                .setHintText("-jar/-cp /path/to/file.jar ...")
                .setAsRequired()
                .setConfirmListener((editBox, checked) -> {
                    Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
                    intent.putExtra("javaArgs", editBox.getText().toString());
                    SelectRuntimeUtils.selectRuntime(activity, null, jreName -> {
                        intent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName);
                        activity.startActivity(intent);
                    });

                    return true;
                }).showDialog();
    }

    /** Launch the mod installer activity. The Uri must be from our own content provider or
     * from ACTION_OPEN_DOCUMENT
     */
    public static void launchModInstaller(Activity activity, @NonNull Uri uri){
        Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
        intent.putExtra("modUri", uri);
        SelectRuntimeUtils.selectRuntime(activity, null, jreName -> {
            LauncherProfiles.generateLauncherProfiles();
            intent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName);
            activity.startActivity(intent);
        });
    }


    public static void installRuntimeFromUri(Context context, Uri uri) {
        Task.runTask(() -> {
            String name = getFileName(context, uri);
            MultiRTUtils.installRuntimeNamed(
                    PathManager.DIR_NATIVE_LIB,
                    context.getContentResolver().openInputStream(uri),
                    name);

            MultiRTUtils.postPrepare(name);
            return null;
        }).onThrowable(e -> Tools.showError(context, e))
                .execute();
    }

    public static String extractUntilCharacter(String input, String whatFor, char terminator) {
        int whatForStart = input.indexOf(whatFor);
        if(whatForStart == -1) return null;
        whatForStart += whatFor.length();
        int terminatorIndex = input.indexOf(terminator, whatForStart);
        if(terminatorIndex == -1) return null;
        return input.substring(whatForStart, terminatorIndex);
    }

    public static boolean isValidString(String string) {
        return string != null && !string.isEmpty();
    }

    public static boolean checkVulkanSupport(PackageManager packageManager) {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
    }

    public static <T> T getWeakReference(WeakReference<T> weakReference) {
        if(weakReference == null) return null;
        return weakReference.get();
    }
}
