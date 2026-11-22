#include "window_slot.h"
#include <android/native_window.h>
#include <pthread.h>
#include <stdatomic.h>

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
