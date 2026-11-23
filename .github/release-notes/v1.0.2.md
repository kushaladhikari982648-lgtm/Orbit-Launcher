# ver 1.0.2 — Orbit Launcher

This release is **Orbit Launcher ver 1.0.2** (`versionCode=100105`, `versionName="1.0.2"`).

## Highlights in ver 1.0.2
- **Fixed Custom Control & Virtual Keyboard Keybind Mismatches (Classic LWJGL3 & SDL3 / Minecraft 26.1+)**:
  - Restored the canonical 106-entry `EfficientAndroidLWJGLKeycode` mapping table so every control keycode index aligns with `KeyboardDialog` and existing custom control layouts (`LCtrl`, `RCtrl`, `Caps Lock`, `Esc`, `Pause`, `Home`, `End`, `Insert`, `F1`–`F12`, and Numpad keys no longer shift to neighbouring keys).
  - Rewrote `KeyboardDialog.init()` to bind every key button directly via `EfficientAndroidLWJGLKeycode.getIndexByValue(LwjglGlfwKeycode.GLFW_KEY_*)` instead of fragile positional loop increments.
  - Updated `EfficientAndroidLWJGLKeycode.getAndroidKeycode()` to map duplicate/special GLFW keycodes (`GLFW_KEY_ESCAPE`, `GLFW_KEY_HOME`, `GLFW_KEY_END`, `GLFW_KEY_2`, `GLFW_KEY_3`, `GLFW_KEY_COMMA`, `GLFW_KEY_KP_ADD`, `GLFW_KEY_EQUAL`, `GLFW_KEY_DELETE`, `GLFW_KEY_SCROLL_LOCK`, `GLFW_KEY_PRINT_SCREEN`, `GLFW_KEY_LEFT_SUPER`, `GLFW_KEY_RIGHT_SUPER`, `GLFW_KEY_MENU`, `GLFW_KEY_BACKSPACE`) to the exact Android `KeyEvent.KEYCODE_*` constants that `libSDL3.so` (`Android_OnKeyDown`) translates to desktop PC `SDL_Scancode`s.
- **Full MJ Launcher (`git.artdeell.mjlaunch` / MojoLauncher) Control Layout Import Support**:
  - Added `LayoutConverter.convertV9MojoLayout()`, `isMojoAndroidKeycodeLayout()`, and `mojoAndroidToGlfw()` to convert MJ Launcher `version = 9` layouts (which store positive Android `KeyEvent.KEYCODE_*` values and `-10` for `SPECIALBTN_KEYBOARDPAN`) into Orbit Launcher `version = 8` layouts with `LwjglGlfwKeycode` keycodes and populated `mControlInfoDataList` metadata.
  - Added `LayoutConverter.readLayoutJsonFromFile()` so ZIP-wrapped `.json` / `.zip` control packages from MJ Launcher (containing `layout.json` and optional button bitmaps) are transparently extracted and converted on import.
  - Relaxed `OpenDocumentWithExtension("json", ...)` and `ControlsListViewCreator` so Android's document picker and control manager accept all MJ Launcher `.json` and `.zip` control files.
- **Gameplay Recorder Quality (`720p` / `1080p`) & Frame Rate (`30 FPS` / `60 FPS`) Selection**:
  - Added pre-recording quality (`720p (HD)` or `1080p (Full HD)`) and frame rate (`30 FPS` for `720p`; `30 FPS` or `60 FPS` for `1080p`) selection in `MainActivity.toggleGameplayRecording()`.
  - Upgraded `MediaPolicy` and `GameplayRecorder` to support `720p` (`1280×720` @ 30 FPS, 4 Mbps) and `1080p` (`1920×1080` @ 30 FPS / 8 Mbps or 60 FPS / 12 Mbps) with automatic encoder profile fallback if a device's hardware AVC encoder does not support the highest tier.
- **Major FPS & Frame-Pacing Optimizations**:
  - **Tri-Cluster CPU Affinity Fix (`bigcoreaffinity.c`)**: Updated big-core selection (`core_freqs[i] > min_freq`) so tri-cluster SoCs (1 Prime + 3/4 Performance + 4 Efficiency cores) schedule the render and game threads across all Prime + Performance cores instead of pinning solely to a single Prime core.
  - **EGL Swap Interval & VSync Enforcement (`gl_bridge.c`, `ltw_sdl_egl.c`)**: Enforced `eglSwapInterval(dpy, force_vsync ? 1 : 0)` on `eglMakeCurrent` and intercepted `eglSwapInterval` in `libltw_sdl_egl.so` so neither Android's default swap interval (`1`) nor Minecraft/SDL swap interval calls cap FPS when *Force VSync* is off.
  - **OpenGL ES 3.2 / 3.1 Context Creation for LTW (`ltw_sdl_egl.c`)**: Removed forced `EGL_CONTEXT_MINOR_VERSION_KHR = 0` on primary context creation so ES 3.2 / 3.1 GPU drivers grant their highest OpenGL ES 3.x minor version, unlocking hardware `glDrawElementsBaseVertex` and compute shaders in LTW instead of falling back to slow per-draw indirect buffer allocations.
  - **Minecraft `options.txt` VSync / FPS Defaults & `SurfaceView` Startup (`MinecraftGLSurface.java`, `JREUtils.java`)**: Automatically disables `enableVsync` and sets a high default `maxFps` when *Force VSync* is off, fires `mOnRenderingStartedListener.isStarted()` on the `SurfaceView` path, and enables `LIBGL_USEVBO=1` and `LIBGL_FASTMATH=1` for `opengles2` (GL4ES).
