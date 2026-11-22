# ver 1.0.1 — Orbit Launcher

This APK is **Orbit Launcher ver 1.0.1** (`versionCode=100104`, `versionName="1.0.1"`).

## Highlights
- **RustGL `0.3.0-gurkha.14` Sync (Minecraft 26.3 OIT & Strict GLES 3.x Support)**:
  - Synced upstream `kushaladhikari982648-lgtm/RustGL-for-Gurkha-launcher` (`aa3fa35cf5a2144ae64bea4b438ce3abe9507c52`), legalizing desktop GLSL 330 fragment output arrays (`out vec4 fragColor[2]`) into numbered GLSL ES 3.00 outputs (`layout(location = 0) out vec4 fragColor_0`, `layout(location = 1) out vec4 fragColor_1`) for Minecraft 26.3 Order-Independent Transparency (`core/oit_add_transmittance.fsh`) on Mali, Adreno, and PowerVR GPUs.
- **RustGL (`gl-compat`) Zero-Lock Fast-Paths & Per-Function Atomic Dispatch**:
  - Added lock-free atomics (`CLIENT_ARRAY_MASK`, `ACTIVE_TEX_UNIT`) so `fixed_draw::try_draw_arrays` and `try_draw_elements` return immediately on modern VBO/VAO draw calls without locking mutexes or querying `glGetIntegerv(GL_CURRENT_PROGRAM)` / `GL_VERTEX_ARRAY_BINDING` from the GLES driver.
  - Upgraded `forward_all!`, `egl_export!`, and `gl_passthrough!` with per-function static `(CACHE_GEN, CACHE_PTR)` atomic caches, added a lock-free L1 symbol cache (`DRIVER_CACHE_L1`) and `EGL_SYM_CACHE`, guarded `is_immutable_buffer` / `has_static_immutable_storage` with atomic counters, added `TEXTURE_TARGETS_FAST` for DSA texture lookups, and skipped redundant `scoped_fbo` rebinds.
- **Hardware `ANativeWindow` Resolution Scaling, Lock-Free Window Slot, & Big-Core Affinity**:
  - Fixed `gl_bridge.c`, `osm_bridge.c`, `egl_bridge.c`, and `MinecraftGLSurface.java` to pass the scaled `savedWidth × savedHeight` to `ANativeWindow_setBuffersGeometry` and SDL3 (`SDLSurface.surfaceChanged`) so Android's hardware Composer (`SurfaceFlinger` / HWC) upscales lower-resolution framebuffers in hardware.
  - Made `gurkha_window_generation()` lock-free via `_Atomic uint64_t generation` (`memory_order_acquire`) on every `gl_swap_buffers()`.
  - Upgraded `bigcoreaffinity.c` to pin the render and game threads across all performance cores (`freq >= 75%` of max CPU frequency) via `pthread_once`, enabled by default.
  - Cached `libSDL3.so` handles/symbols in `nativeEnsureSdlKeyboardFocus`, added an `s_sdlFocusAndSizeSynced` fast path, removed per-sample `nativeEnsureSdlKeyboardFocus` calls from `CallbackBridge.sendCursorPos()`, and skipped `pojavRecordFrameIfNoExplicitSwap()` in `proxySdlPollEvent` once `sSawExplicitSwap` is set.
- **JVM, LWJGL System Allocator, & Renderer Tuning**:
  - Added `-Dorg.lwjgl.system.allocator=system`, `LIBGL_NOERROR=1` across all `opengles*` renderers, pre-launch `System.gc()`, conditional background `logcat` spawning (`AllSettings.enableLogOutput`), and JVM GC flags (`-XX:+PerfDisableSharedMem`, `-XX:+DisableExplicitGC`, `-XX:MaxGCPauseMillis=50`, `-XX:+UseStringDeduplication`, `-XX:+UseCompactObjectHeaders`).
- **Orbit Launcher Rebrand, Black Theme, Escape/Input Fixes, & Discord Rich Presence**:
  - Includes all `ver 1.0.0` improvements: Orbit Launcher branding & icons, obsidian-black liquid-glass theme, Escape/keyboard/mouse fixes in Minecraft 26.3+, VulkanMod compatibility, and Discord Rich Presence (`1571705435713437726`).
  - Official Discord community: **https://discord.gg/7FaUJf4R9**.
