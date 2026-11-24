package net.kdt.pojavlaunch;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

import net.kdt.pojavlaunch.value.DependentLibrary;
import net.kdt.pojavlaunch.value.MinecraftClientInfo;

import java.util.List;
import java.util.Map;

@Keep
@SuppressWarnings("unused") // all unused fields here are parts of JSON structures
public class JMinecraftVersionList {
    public Map<String, String> latest;
    public Version[] versions;

    @Keep
    public static class FileProperties {
        public String id, sha1, url;
        public long size;
    }

    @Keep
    public static class Version extends FileProperties {
        // Since 1.13, so it's one of ways to check
        public Arguments arguments;
        public AssetIndex assetIndex;

        public String assets;
        public Map<String, MinecraftClientInfo> downloads;
        public String inheritsFrom;
        public JavaVersionInfo javaVersion;
        public DependentLibrary[] libraries;
        public String mainClass;
        public String minecraftArguments;
        public int minimumLauncherVersion;
        public String releaseTime;
        public String time;
        public String type;

        // ---- Package-style metadata (formatVersion 2, e.g. Prism/Mojang package manifests) ----
        // Those files carry the version id in "version", the client jar under "mainJar", the
        // required Java in "compatibleJavaMajors" and LWJGL in a separate "requires" package.
        // Tools.normalizeVersionInfo() folds all of this back into the classic fields above so
        // the downloader and the launcher can treat both formats identically.
        public String version;
        public DependentLibrary mainJar;
        public int[] compatibleJavaMajors;
        @SerializedName("+jvmArgs")
        public String[] plusJvmArgs;
        public List<PackageRequirement> requires;
    }

    /** A reference to another metadata package, from the "requires" array of package-style files. */
    @Keep
    public static class PackageRequirement {
        public String uid;
        public String suggests;
    }
    @Keep
    public static class JavaVersionInfo {
        public String component;
        public int majorVersion;
        public int version; // parameter used by LabyMod 4
    }
    // Since 1.13
    @Keep
    public static class Arguments {
        public Object[] game;
        public Object[] jvm;

        @Keep
        public static class ArgValue {
            public ArgRules[] rules;
            public String value;

            // TLauncher styled argument...
            public String[] values;

            @Keep
            public static class ArgRules {
                public String action;
                public String features;
                public ArgOS os;

                @Keep
                public static class ArgOS {
                    public String name;
                    public String version;
                }
            }
        }
    }
    @Keep
    public static class AssetIndex extends FileProperties {
        public long totalSize;
    }
}

