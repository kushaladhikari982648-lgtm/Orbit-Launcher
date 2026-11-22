from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'ZalithLauncher/src/main/java'

class MojoRustGLTests(unittest.TestCase):
    def test_actual_jni_backend_separation(self):
        javac = shutil.which('javac')
        if not javac or not shutil.which('cc'):
            self.skipTest('JDK and C compiler required; run by CompatibilityChecks CI')
        java_home = Path(javac).resolve().parents[1]
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', '-Wno-unused-parameter', '-shared', '-fPIC',
                '-I' + str(java_home / 'include'), '-I' + str(java_home / 'include/linux'),
                '-I' + str(ROOT / 'ZalithLauncher/src/main/jni'), str(ROOT / 'tests/native/bridge_probe.c'),
                '-o', str(tmp / 'libbridgeprobe.so')], check=True)
            subprocess.run([javac, '--release', '8', '-d', str(tmp), str(ROOT / 'tests/java/BridgeProbe.java')], check=True)
            def probe(sdl, expected):
                subprocess.run(['java', '-Xcheck:jni', '-Djava.library.path=' + str(tmp), '-cp', str(tmp),
                                'BridgeProbe', str(sdl).lower(), str(expected).lower()], check=True)
            probe(True, True)  # NO GLFW class at all; must never look it up.
            probe(False, False)  # Missing shim fails safely, never GetMethodID(null).
            fixture = tmp / 'GLFW.java'
            for fields, expected in [('', False),
                ('public static java.nio.ByteBuffer keyDownBuffer = null, mouseDownBuffer = null;', False),
                ('public static java.nio.ByteBuffer keyDownBuffer = java.nio.ByteBuffer.allocate(317), mouseDownBuffer = java.nio.ByteBuffer.allocate(8);', False),
                ('public static java.nio.ByteBuffer keyDownBuffer = java.nio.ByteBuffer.allocateDirect(317), mouseDownBuffer = java.nio.ByteBuffer.allocateDirect(8);', True)]:
                fixture.write_text('package org.lwjgl.glfw; public class GLFW { public static void glfwSetWindowAttrib(long w,int a,int v) {} public static void internalWindowSizeChanged(long w,int x,int y) {} ' + fields + '}')
                subprocess.run([javac, '--release', '8', '-d', str(tmp), str(fixture)], check=True)
                probe(False, expected)
                probe(True, True)

    def test_runtime_backend_is_set_and_checked(self):
        java = (SRC / 'net/kdt/pojavlaunch/utils/JREUtils.java').read_text()
        self.assertIn('"POJAV_WINDOW_BACKEND", MinecraftGLSurface.sdlVersion ? "sdl" : "glfw"', java)
        native = (ROOT / 'ZalithLauncher/src/main/jni/input_bridge_v3.c').read_text()
        self.assertIn('if (!gurkha_init_runtime_bridge', native)
        self.assertIn('jboolean ensureGlfwRuntimeBridgeInitialized(JNIEnv *env)', native)
        self.assertIn('return JNI_ERR;', native)
        sdl = (SRC / 'com/movtery/zalithlauncher/launch/SdlSupport.kt').read_text()
        sdl_classpath = sdl.split('fun getClassPath(): String {')[1].split('fun getGlfwClassPath()')[0]
        self.assertNotIn('file.name != "lwjgl-glfw.jar"', sdl_classpath)
        # Official GLFW types may still be referenced by mods; avoiding their
        # initialization is different from deleting them from the classpath.

    def test_rustgl_is_real_opt_in_gles_and_sdl_capable(self):
        renderer = (SRC / 'com/movtery/zalithlauncher/renderer/renderers/RustGLRenderer.kt').read_text()
        self.assertIn('"RENDERER_BACKEND" to "gles"', renderer)
        self.assertIn('getRendererEGL() = LIBRARY', renderer)
        self.assertNotIn('JAVA_TOOL_OPTIONS', renderer)
        self.assertNotIn('NoChecks', renderer)
        policy = (SRC / 'com/movtery/zalithlauncher/launch/RendererPolicy.kt').read_text()
        self.assertIn('current !is RustGLRenderer', policy)
        self.assertTrue((ROOT / 'rustgl/crates/gl-compat/src/lib.rs').is_file())
        workflow = (ROOT / '.github/workflows/android.yml').read_text()
        self.assertIn('python3 rustgl/pack_aar.py', workflow)
        self.assertIn('MESA_GLES_VERSION_OVERRIDE=3.0', workflow)

    def test_fps_and_rustgl_gurkha14_optimizations(self):
        import json
        lock = json.loads((ROOT / 'tools/rustgl-lock.json').read_text())
        self.assertEqual(lock['commit'], 'aa3fa35cf5a2144ae64bea4b438ce3abe9507c52')
        self.assertEqual(lock['version'], '0.3.0-gurkha.14')

        shader_lib = (ROOT / 'rustgl/crates/shader-translate/src/lib.rs').read_text()
        self.assertIn('fn legalize_output_arrays', shader_lib)
        fixtures = (ROOT / 'rustgl/crates/shader-translate/tests/mc_shader_fixtures.rs').read_text()
        self.assertIn('OIT_ADD_TRANSMITTANCE_FSH_26_3', fixtures)

        gl_compat = (ROOT / 'rustgl/crates/gl-compat/src/lib.rs').read_text()
        self.assertIn('DRIVER_CACHE_L1', gl_compat)
        self.assertIn('EGL_SYM_CACHE', gl_compat)
        self.assertIn('STATIC_IMMUTABLE_COUNT', gl_compat)

        fixed_draw = (ROOT / 'rustgl/crates/gl-compat/src/fixed_draw.rs').read_text()
        self.assertIn('!fixed_func::any_client_array_enabled()', fixed_draw)

        jre = (SRC / 'net/kdt/pojavlaunch/utils/JREUtils.java').read_text()
        self.assertIn('-Dorg.lwjgl.system.allocator=system', jre)
        self.assertIn('System.gc();', jre)

        gl_bridge = (ROOT / 'ZalithLauncher/src/main/jni/ctxbridges/gl_bridge.c').read_text()
        self.assertIn('bigcore_set_affinity();', gl_bridge)
        self.assertIn('ANativeWindow_setBuffersGeometry(next, bufW, bufH, bundle->format)', gl_bridge)

        window_slot = (ROOT / 'ZalithLauncher/src/main/jni/ctxbridges/window_slot.c').read_text()
        self.assertIn('_Atomic uint64_t generation;', window_slot)
        self.assertIn('atomic_load_explicit(&generation, memory_order_acquire)', window_slot)

    def test_keybinds_mojo_control_import_and_fps_pipeline(self):
        import re
        keycode_src = (SRC / 'net/kdt/pojavlaunch/EfficientAndroidLWJGLKeycode.java').read_text()
        self.assertIn('private static final int KEYCODE_COUNT = 106;', keycode_src)
        adds = re.findall(r'add\(KeyEvent\.(KEYCODE_[A-Z0-9_]+),\s*LwjglGlfwKeycode\.(GLFW_KEY_[A-Z0-9_]+)', keycode_src)
        self.assertEqual(len(adds), 106)
        self.assertIn('sAndroidByLwjgl.put(LwjglGlfwKeycode.GLFW_KEY_ESCAPE, KeyEvent.KEYCODE_ESCAPE);', keycode_src)
        self.assertIn('sAndroidByLwjgl.put(LwjglGlfwKeycode.GLFW_KEY_HOME, KeyEvent.KEYCODE_MOVE_HOME);', keycode_src)
        self.assertIn('sAndroidByLwjgl.put(LwjglGlfwKeycode.GLFW_KEY_KP_ADD, KeyEvent.KEYCODE_NUMPAD_ADD);', keycode_src)
        self.assertIn('sAndroidByLwjgl.put(LwjglGlfwKeycode.GLFW_KEY_DELETE, KeyEvent.KEYCODE_FORWARD_DEL);', keycode_src)

        kbd_dialog = (SRC / 'com/movtery/zalithlauncher/ui/dialog/KeyboardDialog.java').read_text()
        self.assertIn('bindKeyboardKey(binding.keyboardLeftCtrl, LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, specialOffset);', kbd_dialog)
        self.assertIn('bindKeyboardKey(binding.keyboardCapslock, LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, specialOffset);', kbd_dialog)
        self.assertIn('bindKeyboardKey(binding.keyboardEsc, LwjglGlfwKeycode.GLFW_KEY_ESCAPE, specialOffset);', kbd_dialog)
        self.assertIn('int keycodeIndex = EfficientAndroidLWJGLKeycode.getIndexByValue(glfwKeycode);', kbd_dialog)

        converter = (SRC / 'net/kdt/pojavlaunch/customcontrols/LayoutConverter.java').read_text()
        self.assertIn('public static String readLayoutJsonFromFile(File file)', converter)
        self.assertIn('public static CustomControls convertV9MojoLayout(JSONObject oldLayoutJson, String jsonPath)', converter)
        self.assertIn('public static int mojoAndroidToGlfw(int androidKeycode)', converter)
        self.assertIn('MOJO_SPECIALBTN_KEYBOARDPAN = -10', converter)

        edit_ctrl = (SRC / 'com/movtery/zalithlauncher/ui/subassembly/customcontrols/EditControlData.kt').read_text()
        self.assertIn('LayoutConverter.readLayoutJsonFromFile(file)', edit_ctrl)

        bigcore = (ROOT / 'ZalithLauncher/src/main/jni/bigcoreaffinity.c').read_text()
        self.assertIn('if (core_freqs[i] > min_freq)', bigcore)

        gl_bridge = (ROOT / 'ZalithLauncher/src/main/jni/ctxbridges/gl_bridge.c').read_text()
        self.assertIn('eglSwapInterval_p(g_EglDisplay, pojav_environ->force_vsync ? 1 : 0);', gl_bridge)

        ltw_egl = (ROOT / 'ZalithLauncher/src/main/jni/ltw_sdl_egl.c').read_text()
        self.assertIn('host_eglSwapInterval(dpy, is_force_vsync_enabled() ? 1 : 0);', ltw_egl)
        self.assertIn('{ "eglSwapInterval", (proc_t) eglSwapInterval }', ltw_egl)


