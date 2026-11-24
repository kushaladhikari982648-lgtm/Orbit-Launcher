"""Regression tests for the "Failed to download Minecraft!" fix (profile 26.1.1).

Covers the download/launch pipeline hardening:
  * version-manifest resolution fallbacks (sticky event -> cached file -> fresh download),
  * package-style (formatVersion 2) metadata normalization,
  * tolerant JNA .aar native-archive handling (partial/missing archives must not abort),
  * OS-rule and CPU-classifier filtering of libraries, checked for BOTH shipping ABIs
    (armeabi-v7a = 32-bit ARM, arm64-v8a = 64-bit ARM),
  * the raw Mojang 26.1.1 metadata the pipeline consumes (tests/fixtures/mojang_26.1.1.json).

The behavioral helpers below (check_rules / classifier_supported) are line-for-line
mirrors of Tools.checkRules and Tools.classifierSupportedByDevice; the static source
assertions pin the Java implementations to the same logic.
"""

import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'ZalithLauncher/src/main/java'
FIXTURE = ROOT / 'tests/fixtures/mojang_26.1.1.json'

ANDROID_OS_NAME = 'Linux'  # System.getProperty("os.name") on Android

ARMEABI = 'armeabi-v7a'
ARM64 = 'arm64-v8a'


def read(rel_path):
    return (ROOT / rel_path).read_text(encoding='utf-8')


# --- Spec mirrors of the Java logic (must stay in sync; pinned by static asserts) ---

def check_rules(rules, os_name=ANDROID_OS_NAME):
    """Mirror of Tools.checkRules: ordered rules, last match wins, default = !first."""
    if not rules:
        return True
    allowed = rules[0].get('action') != 'allow'
    current = os_name.lower()
    for rule in rules:
        if rule is None or rule.get('action') is None:
            continue
        os_rule = rule.get('os')
        if os_rule is not None and os_rule.get('name') is not None:
            if current != os_rule['name'].lower():
                continue
        elif os_rule is not None and os_rule.get('version') is not None:
            continue
        allowed = rule['action'] == 'allow'
    return allowed


def classifier_supported(lib_name, arch):
    """Mirror of Tools.classifierSupportedByDevice for the two shipping ABIs."""
    if lib_name is None:
        return True
    last_colon = lib_name.rfind(':')
    if last_colon < 0 or last_colon == len(lib_name) - 1:
        return True
    classifier = lib_name[last_colon + 1:]
    if not classifier or classifier[0].isdigit():
        return True
    lower = classifier.lower()
    if lower.startswith('linux-x86_64'):
        return arch == 'x86_64'
    if lower.startswith('linux-aarch_64') or lower.startswith('linux-arm64'):
        return arch == 'arm64'
    if lower.startswith('linux-x86'):
        return arch == 'x86'
    if lower.startswith('linux-arm'):
        return arch == 'arm'
    if lower.startswith(('osx', 'macos', 'darwin')):
        return False
    if lower.startswith('windows'):
        return False
    if lower.startswith(('natives-osx', 'natives-macos', 'natives-windows')):
        return False
    return True


def library_enabled(library, arch):
    return check_rules(library.get('rules')) and classifier_supported(library.get('name'), arch)


class ManifestResolutionTests(unittest.TestCase):
    """Sticky-event-only lookup used to null out verInfo and abort the download."""

    def test_async_downloader_exposes_disk_and_network_fallbacks(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/AsyncMinecraftDownloader.java')
        self.assertIn('getCachedListedVersion', source)
        self.assertIn('fetchListedVersion', source)
        # getListedVersion must consult both before returning null
        body = source[source.index('public static JMinecraftVersionList.Version getListedVersion'):]
        body = body[:body.index('\n    }')]
        self.assertIn('getCachedListedVersion', body)
        # Version lookup must not NPE on null ids (package-style manifests use "version")
        self.assertIn('versionString.equals(version.id)', source)
        self.assertNotIn('version.id.equals(versionString)', source)
        # The network fallback refreshes the on-disk cache
        self.assertIn('Tools.write(PathManager.FILE_VERSION_LIST', source)

    def test_downloader_resolves_metadata_before_giving_up(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        body = source[source.index('private void downloadAndProcessMetadata'):]
        body = body[:body.index('\n    }')]
        self.assertIn('AsyncMinecraftDownloader.getListedVersion(versionName)', body)
        self.assertIn('AsyncMinecraftDownloader.getCachedListedVersion(versionName)', body)
        self.assertIn('AsyncMinecraftDownloader.fetchListedVersion(versionName)', body)
        self.assertIn('Tools.normalizeVersionInfo(verInfo, versionName)', body)
        # Corrupt/empty JSON must fail with an actionable message, not an NPE
        self.assertIn('parsedVersion.mainClass == null && parsedVersion.libraries == null', body)
        self.assertIn('Tools.isValidString(parsedVersion.inheritsFrom)', body)
        self.assertIn('missing, empty or corrupted', body)

    def test_inheritance_recursion_re_resolves_null_parents(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        self.assertIn('downloadAndProcessMetadata(inheritedVersion, verInfo.inheritsFrom)', source)


class PackageStyleMetadataTests(unittest.TestCase):
    """formatVersion 2 files (Prism/mojang packages) must download and launch identically."""

    def test_version_model_parses_package_style_fields(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/JMinecraftVersionList.java')
        self.assertIn('public String version;', source)
        self.assertIn('public DependentLibrary mainJar;', source)
        self.assertIn('public int[] compatibleJavaMajors;', source)
        self.assertIn('@SerializedName("+jvmArgs")', source)
        self.assertIn('public List<PackageRequirement> requires;', source)
        self.assertIn('class PackageRequirement', source)

    def test_normalize_maps_package_style_fields(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/Tools.java')
        body = source[source.index('public static void normalizeVersionInfo'):]
        body = body[:body.index('\n    }')]
        # id <- version (or the profile name for vanilla files)
        self.assertIn('versionInfo.version', body)
        self.assertIn('versionInfo.id = fallbackId', body)
        # client jar <- mainJar.downloads.artifact
        self.assertIn('versionInfo.mainJar.downloads.artifact', body)
        self.assertIn('downloads.put("client", clientInfo)', body)
        # assets <- assetIndex.id
        self.assertIn('versionInfo.assetIndex.id', body)
        # javaVersion <- compatibleJavaMajors
        self.assertIn('versionInfo.compatibleJavaMajors', body)
        self.assertIn('javaVersionInfo.majorVersion = major', body)
        # LWJGL marker synthesized from "requires: org.lwjgl3"
        self.assertIn('synthesizeLwjglMarker', source)
        self.assertIn('"org.lwjgl3"', source)
        self.assertIn('"org.lwjgl:lwjgl:" + suggested', source)

    def test_get_version_info_normalizes_both_jsons(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/Tools.java')
        body = source[source.index('public static JMinecraftVersionList.Version getVersionInfo(Version version, boolean skipInheriting)'):]
        body = body[:body.index('\n    }')]
        # custom (profile) JSON and inherited (vanilla) JSON both get normalized
        self.assertGreaterEqual(body.count('Tools.normalizeVersionInfo'), 2)
        self.assertIn('Version JSON is empty', body)

    def test_launch_args_pass_pinned_jvm_flags_only_on_java_25(self):
        source = read('ZalithLauncher/src/main/java/com/movtery/zalithlauncher/launch/LaunchArgs.kt')
        self.assertIn('versionInfo.plusJvmArgs', source)
        self.assertIn('runtime.javaVersion >= 25', source)


class NativeArchiveTests(unittest.TestCase):
    """A missing/corrupt JNA .aar must never surface as mc_download_failed."""

    def test_extract_natives_is_best_effort(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        body = source[source.index('private void extractNatives'):]
        body = body[:body.index('\n    }')]
        self.assertIn('source.isFile() && source.length() > 0', body)
        self.assertIn('nativesExtractor.extractFromAar(source)', body)
        self.assertIn('catch (IOException | RuntimeException e)', body)
        self.assertIn("using the APK's bundled libraries instead", body)

    def test_corrupt_aar_is_deleted_before_scheduling(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        self.assertIn('private static boolean isZipArchive(File file)', source)
        body = source[source.index('private void scheduleNativeLibraryDownload'):]
        body = body[:body.index('\n    }')]
        self.assertIn('isZipArchive(targetPath)', body)
        self.assertIn('targetPath.delete()', body)
        # The JNA special case (and its .aar) must stay wired up
        self.assertIn('scheduleNativeLibraryDownload(MAVEN_CENTRAL_REPO1, dependentLibrary)', source)

    def test_partial_skip_if_failed_downloads_are_removed(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        body = source[source.index('private void downloadFile()'):]
        body = body[:body.index('\n        }')]
        self.assertIn('if(!mSkipIfFailed) throw e;', body)
        self.assertIn('mTargetPath.delete()', body)

    def test_natives_extractor_guards_missing_inputs(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/NativesExtractor.java')
        self.assertIn('if (includedLibraryNames == null) return new ArrayList<>(0);', source)
        self.assertIn('if (!source.isFile() || source.length() == 0) return;', source)

    def test_natives_extractor_supports_both_shipping_abis(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/NativesExtractor.java')
        self.assertIn('return "armeabi-v7a";', source)
        self.assertIn('return "arm64-v8a";', source)


class OsArchFilteringTests(unittest.TestCase):
    """macOS/Windows jars and wrong-CPU classifiers must be dropped on both ARM ABIs."""

    def test_check_rules_is_no_longer_osx_only(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/Tools.java')
        body = source[source.index('static boolean checkRules'):]
        body = body[:body.index('\n    }')]
        self.assertIn('equalsIgnoreCase', body)  # generic OS-name matching, not equals("osx")
        self.assertNotIn('equals("osx")', body)
        self.assertIn('rules[0] != null && !"allow".equals(rules[0].action)', body)
        # pin the mirror above to the Java tokens
        for token in ('equalsIgnoreCase(os.name)', '"allow".equals(rule.action)'):
            self.assertIn(token, body)

    def test_classifier_filter_covers_both_abis(self):
        source = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/Tools.java')
        body = source[source.index('public static boolean classifierSupportedByDevice'):]
        body = body[:body.index('\n    }')]
        for token in ('linux-x86_64', 'linux-aarch_64', 'linux-arm64', 'linux-x86',
                      'linux-arm', 'ARCH_X86_64', 'ARCH_ARM64', 'ARCH_X86', 'ARCH_ARM'):
            self.assertIn(token, body)
        self.assertIn('startsWith("osx")', body)
        self.assertIn('startsWith("windows")', body)

    def test_download_and_classpath_use_the_device_filter(self):
        downloader = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java')
        self.assertIn('Tools.isLibraryEnabledForDevice(dependentLibrary)', downloader)
        tools = read('ZalithLauncher/src/main/java/net/kdt/pojavlaunch/Tools.java')
        self.assertIn('if (!isLibraryEnabledForDevice(libItem)) continue;', tools)

    def test_rule_semantics_spec(self):
        # macOS/Windows-only libraries are excluded on Android (both ABIs)
        for arch in (ARMEABI, ARM64):
            self.assertFalse(check_rules([{'action': 'allow', 'os': {'name': 'osx'}}]))
            self.assertFalse(check_rules([{'action': 'allow', 'os': {'name': 'osx-arm64'}}]))
            self.assertFalse(check_rules([{'action': 'allow', 'os': {'name': 'windows'}}]))
            self.assertFalse(check_rules([{'action': 'allow', 'os': {'name': 'windows-arm64'}}]))
            # Linux-allowed libraries stay enabled
            self.assertTrue(check_rules([{'action': 'allow', 'os': {'name': 'linux'}}]))
            # Mixed allow-lists stay enabled on Linux
            self.assertTrue(check_rules([
                {'action': 'allow', 'os': {'name': 'linux'}},
                {'action': 'allow', 'os': {'name': 'windows'}},
                {'action': 'allow', 'os': {'name': 'osx'}}]))
            # allow-everywhere-then-ban-osx stays enabled on Linux
            self.assertTrue(check_rules([
                {'action': 'allow'},
                {'action': 'disallow', 'os': {'name': 'osx'}}]))
            # allow-only-on-osx stays disabled on Linux
            self.assertFalse(check_rules([
                {'action': 'allow', 'os': {'name': 'osx'}}]))
            # no rules at all
            self.assertTrue(check_rules(None))

    def test_classifier_semantics_spec_for_arm32_and_arm64(self):
        epoll_x86 = 'io.netty:netty-transport-native-epoll:4.2.7.Final:linux-x86_64'
        epoll_arm64 = 'io.netty:netty-transport-native-epoll:4.2.7.Final:linux-aarch_64'
        # 64-bit ARM keeps only the aarch_64 natives
        self.assertTrue(classifier_supported(epoll_arm64, 'arm64'))
        self.assertFalse(classifier_supported(epoll_x86, 'arm64'))
        # 32-bit ARM has no matching netty natives classifier: both are dropped
        self.assertFalse(classifier_supported(epoll_arm64, 'arm'))
        self.assertFalse(classifier_supported(epoll_x86, 'arm'))
        # Desktop classifiers are dropped everywhere
        for arch in ('arm', 'arm64', 'x86', 'x86_64'):
            self.assertFalse(classifier_supported('io.netty:netty-transport-native-kqueue:4.2.7.Final:osx-aarch_64', arch))
            self.assertFalse(classifier_supported('some:lib:1.0:natives-windows', arch))
            self.assertFalse(classifier_supported('some:lib:1.0:windows-x86_64', arch))
            # Plain jars (version as last segment) are always kept
            self.assertTrue(classifier_supported('net.java.dev.jna:jna:5.17.0', arch))
            self.assertTrue(classifier_supported('at.yawk.lz4:lz4-java:1.10.1', arch))
            self.assertTrue(classifier_supported('com.mojang:jtracy-natives-windows:1.0.37', arch))


class MojangMetadataFixtureTests(unittest.TestCase):
    """Structural contract of the raw Mojang 26.1.1 JSON the downloader consumes."""

    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(FIXTURE.read_text(encoding='utf-8'))

    def test_classic_fields_present(self):
        d = self.data
        self.assertEqual(d['id'], '26.1.1')
        self.assertIn('client', d['downloads'])
        self.assertTrue(d['downloads']['client']['url'].startswith('https://piston-data.mojang.com/'))
        self.assertEqual(d['assets'], '30')
        self.assertEqual(d['javaVersion']['majorVersion'], 25)
        self.assertIn('arguments', d)

    def test_lwjgl_and_jna_entries_present(self):
        names = [lib['name'] for lib in self.data['libraries']]
        self.assertIn('org.lwjgl:lwjgl:3.4.1', names)          # SdlSupport detection
        self.assertIn('net.java.dev.jna:jna:5.17.0', names)     # .aar natives download
        self.assertIn('net.java.dev.jna:jna-platform:5.17.0', names)

    def test_every_library_has_a_download_url(self):
        for lib in self.data['libraries']:
            artifact = (lib.get('downloads') or {}).get('artifact') or {}
            self.assertTrue(artifact.get('url'), f"missing url for {lib.get('name')}")

    def test_device_filter_keeps_only_usable_libraries(self):
        libs = self.data['libraries']
        for arch in ('arm', 'arm64'):
            kept = [lib for lib in libs if library_enabled(lib, arch)]
            self.assertTrue(kept, f'no libraries kept for {arch}')
            kept_names = {lib['name'] for lib in kept}
            # Core requirements always survive on both ABIs
            self.assertIn('net.java.dev.jna:jna:5.17.0', kept_names)
            self.assertIn('org.lwjgl:lwjgl:3.4.1', kept_names)
            # Desktop-only jars never survive
            self.assertNotIn('ca.weblite:java-objc-bridge:1.1', kept_names)
            self.assertNotIn('com.mojang:jtracy-natives-windows:1.0.37', kept_names)
            # Wrong-CPU netty natives never survive
            self.assertNotIn(
                'io.netty:netty-transport-native-epoll:4.2.7.Final:linux-x86_64', kept_names)

    def test_arm64_keeps_aarch_64_netty_natives_arm32_drops_both(self):
        libs = self.data['libraries']
        epoll_arm64 = 'io.netty:netty-transport-native-epoll:4.2.7.Final:linux-aarch_64'
        epoll_x86 = 'io.netty:netty-transport-native-epoll:4.2.7.Final:linux-x86_64'
        kept64 = {lib['name'] for lib in libs if library_enabled(lib, 'arm64')}
        kept32 = {lib['name'] for lib in libs if library_enabled(lib, 'arm')}
        self.assertIn(epoll_arm64, kept64)
        self.assertNotIn(epoll_x86, kept64)
        self.assertNotIn(epoll_arm64, kept32)
        self.assertNotIn(epoll_x86, kept32)

    def test_filter_shrinks_the_download_surface(self):
        libs = self.data['libraries']
        kept = [lib for lib in libs if library_enabled(lib, 'arm64')]
        # At least the 25 windows-rule + 21 osx-rule entries must be gone (minus any that
        # are also linux-allowed, and the netty x86_64 classifier).
        self.assertLess(len(kept), len(libs))
        self.assertGreaterEqual(len(libs) - len(kept), 40)


class WorkflowCleanupTests(unittest.TestCase):
    def test_screenshot_fetch_step_removed(self):
        workflow = read('.github/workflows/android.yml')
        self.assertNotIn('cdn.discordapp.com', workflow)
        self.assertIn('BuildArm32And64Only', workflow)
        self.assertIn('PublishPreRelease103', workflow)


if __name__ == '__main__':
    unittest.main()
