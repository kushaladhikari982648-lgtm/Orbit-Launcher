# Orbit Launcher 1.0.3 Pre-Release (32-bit & 64-bit ARM Only)

### Highlights
- **Un-smushed, Main-Screen-Proportioned UI Layouts (`144dp` sidebar + `31dp` controls + spacious content grids)**:
  - Redesigned the **Mods, Modpacks, Resource Packs, Worlds, and Shaders** download browser (`fragment_download.xml`, `fragment_download_resource.xml`, `fragment_mod_download.xml`, `item_download_info.xml`) so filter sidebars no longer crush the results pane and mod/modpack cards display clean titles, descriptions, categories, and tags without clipping.
  - Redesigned **Launcher Settings** (`fragment_settings.xml` and all five category pages: Video, Control, Game, Launcher, Experimental) with a compact `144dp` category rail and full-width setting cards.
  - Updated **Installed Mods, Version List, Version Manager, Version Config, Files, Accounts, and Custom Controls** screens (`fragment_mods.xml`, `fragment_versions_list.xml`, `fragment_version_manager.xml`, `fragment_version_config.xml`, `fragment_files.xml`, `fragment_account.xml`, `fragment_control_manager.xml`) to match the Main home screen proportions.
- **LWJGL Mapping, Native Loading & Runtime Overhead Hardening**:
  - Guarded GLFW window-size/framebuffer callbacks, mouse/cursor coordinates, and keycode array bounds across `GLFW.java`, `CallbackBridge.java`, `EfficientAndroidLWJGLKeycode.java`, and `input_bridge_v3.c`.
  - Fixed native library path splitting, renderer fallbacks, and hook byte restoration in `JREUtils.java` and `lwjgl_dlopen_hook.c`.
  - Eliminated per-event allocations, unbounded `stdio_is.c` line buffering, and background Discord RPC / memory-polling overhead when backgrounded or hidden.

### Packages Included (ARM 32-bit & 64-bit Only)
- `OrbitLauncher-1.0.3-debug-arm64-v8a.apk` — 64-bit ARM (`arm64-v8a`)
- `OrbitLauncher-1.0.3-debug-armeabi-v7a.apk` — 32-bit ARM (`armeabi-v7a`)
