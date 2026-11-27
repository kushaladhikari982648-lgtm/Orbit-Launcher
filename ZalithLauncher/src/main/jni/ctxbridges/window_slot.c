#include "window_slot.h"
#include <android/native_window.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

/* From swap_interval_no_egl.c, same pojavexec library. Ungated FORCE_VSYNC poke. */
void setNativeWindowSwapIntervalForced(struct ANativeWindow *window, int interval);

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *published;
static _Atomic uint64_t generation;

void bridge_publish_window(ANativeWindow *window) {
    pthread_mutex_lock(&lock);
    ANativeWindow *old = published;
    published = window;
    atomic_fetch_add_explicit(&generation, 1, memory_order_release);
    pthread_mutex_unlock(&lock);
    if (old) ANativeWindow_release(old);
    if (window) {
        /* Every renderer backend (LTW, RustGL, GL4ES, Zink) draws into this very
         * window, and some drivers clamp eglSwapInterval(0) while leaving the
         * BufferQueue itself vsync-locked — that is the "FPS capped on some
         * devices" case. Setting the ANativeWindow-level interval here is the
         * one place all backends share. FORCE_VSYNC pins it back to 1. */
        const char *forceVsync = getenv("FORCE_VSYNC");
        setNativeWindowSwapIntervalForced(window,
                (forceVsync && !strcmp(forceVsync, "true")) ? 1 : 0);
    }
}
ANativeWindow *bridge_acquire_window(uint64_t *version) {
    pthread_mutex_lock(&lock);
    ANativeWindow *window = published;
    if (window) ANativeWindow_acquire(window);
    if (version) *version = atomic_load_explicit(&generation, memory_order_relaxed);
    pthread_mutex_unlock(&lock);
    return window;
}
uint64_t bridge_window_generation(void) {
    return atomic_load_explicit(&generation, memory_order_acquire);
}
