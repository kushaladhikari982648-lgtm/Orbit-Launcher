# Orbit Launcher 1.0.3 Pre-Release (32-bit & 64-bit ARM Only)

### Highlights
- **RustGL Renderer `0.3.0-gurkha.15` Sync (Compute Shaders on GLES 3.1+)**:
  - Merged `kushaladhikari982648-lgtm/RustGL` `2b40467bb4b149343487bc6f27083312e48d5b34` into the vendored renderer: compute shaders translate to ESSL 3.10/3.20 when the device reports GLES 3.1/3.2 (GLES 3.0 gets a clear unsupported-feature error instead of a blank shader), legalized fragment outputs flush before inline and bare `return` statements, and the shader-translation cache keys on the measured GLES version.
  - arm32/arm64 packages now ship the freshly built RustGL/LTW/renderer-core natives from CI rather than the committed library copies.
- **Un-smushed, Main-Screen-Proportioned UI Layouts (`144dp` sidebar + `31dp` controls + spacious content grids)**:
  - Redesigned the **Mods, Modpacks, Resource Packs, Worlds, and Shaders** download browser (`fragment_download.xml`, `fragment_download_resource.xml`, `fragment_mod_download.xml`, `item_download_info.xml`) so filter sidebars no longer crush the results pane and mod/modpack cards display clean titles, descriptions, categories, and tags without clipping.
  - Redesigned **Launcher Settings** (`fragment_settings.xml` and all five category pages: Video, Control, Game, Launcher, Experimental) with a compact `144dp` category rail and full-width setting cards.
  - Updated **Installed Mods, Version List, Version Manager, Version Config, Files, Accounts, and Custom Controls** screens (`fragment_mods.xml`, `fragment_versions_list.xml`, `fragment_version_manager.xml`, `fragment_version_config.xml`, `fragment_files.xml`, `fragment_account.xml`, `fragment_control_manager.xml`) to match the Main home screen proportions.
- **Uncapped FPS in-game (`force_vsync` Off)**:
  - The swap-interval policy is now enforced at the BufferQueue level for every renderer backend (LTW, RustGL, GL4ES, Zink): drivers that ignore `eglSwapInterval(0)` no longer leave the game vsync-locked, while `force_vsync` On still pins the interval to 1.
  - The RustGL front now applies the policy on every `eglMakeCurrent` and clamps its `eglSwapInterval` export, matching the LTW front — previously it left the EGL default interval of 1.
- **Download reliability (`Failed to download Minecraft!` regression)**:
  - Downloads now use connect **and** read timeouts, reject non-200 responses (429/5xx error pages are no longer saved as file content), retry transient failures with backoff in both hashed and no-hash paths, and delete partial files instead of trusting them.
  - Default max download threads lowered from 64 to 16 so mobile NAT/CDN budgets are not saturated into connection resets (still configurable in Settings).
- **Modpack installs no longer end in an "invalid version"**:
  - Failures that used to abort silently (manifest verification, unsupported or absent mod loader — including Quilt on CurseForge) now show the real reason, and the incomplete version folder is removed instead of lingering as a version the launcher only offers to delete.
  - Modrinth downloads pick the version's primary file instead of blindly taking the first one, so extra files (changelogs etc.) can no longer be downloaded in place of the pack.
- **Portrait layouts no longer smushed**:
  - Added portrait variants for Settings (category strip above the pager instead of a `144dp` side rail), Version Manager, Controller Remapper, Download (category strip above content), and the Mod/Resource download screens (action panels become bottom sheets).
- **LWJGL Mapping, Native Loading & Runtime Overhead Hardening**:
  - Guarded GLFW window-size/framebuffer callbacks, mouse/cursor coordinates, and keycode array bounds across `GLFW.java`, `CallbackBridge.java`, `EfficientAndroidLWJGLKeycode.java`, and `input_bridge_v3.c`.
  - Fixed native library path splitting, renderer fallbacks, and hook byte restoration in `JREUtils.java` and `lwjgl_dlopen_hook.c`.
  - Eliminated per-event allocations, unbounded `stdio_is.c` line buffering, and background Discord RPC / memory-polling overhead when backgrounded or hidden.

### Packages Included (ARM 32-bit & 64-bit Only)
- `Orbit Launcher 1.0.3.apk` — 64-bit ARM (`arm64-v8a`)
- `Orbit Launcher 1.0.3 (32-bit).apk` — 32-bit ARM (`armeabi-v7a`)
