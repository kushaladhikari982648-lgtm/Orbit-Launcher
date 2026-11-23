package com.movtery.zalithlauncher.utils.platform

import android.app.ActivityManager
import android.content.Context

class MemoryUtils {
    companion object {
        private var activityManager: ActivityManager? = null

        @Volatile
        private var cachedTotalMemory: Long = 0L

        @Volatile
        private var cachedMemoryInfo: ActivityManager.MemoryInfo? = null

        @Volatile
        private var lastRefreshElapsedMs: Long = 0L

        private const val MEMORY_CACHE_TTL_MS = 1500L

        private fun init(context: Context) {
            activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        }

        private fun getMemoryInfoCached(context: Context): ActivityManager.MemoryInfo {
            val now = android.os.SystemClock.elapsedRealtime()
            val existing = cachedMemoryInfo
            if (existing != null && (now - lastRefreshElapsedMs) < MEMORY_CACHE_TTL_MS) {
                return existing
            }
            activityManager ?: run { init(context) }
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            cachedMemoryInfo = memInfo
            if (cachedTotalMemory <= 0L && memInfo.totalMem > 0L) {
                cachedTotalMemory = memInfo.totalMem
            }
            lastRefreshElapsedMs = now
            return memInfo
        }

        @JvmStatic
        fun getTotalDeviceMemory(context: Context): Long {
            val cached = cachedTotalMemory
            if (cached > 0L) return cached
            return getMemoryInfoCached(context).totalMem
        }

        @JvmStatic
        fun getUsedDeviceMemory(context: Context): Long {
            val memInfo = getMemoryInfoCached(context)
            return memInfo.totalMem - memInfo.availMem
        }

        @JvmStatic
        fun getFreeDeviceMemory(context: Context): Long {
            return getMemoryInfoCached(context).availMem
        }
    }
}