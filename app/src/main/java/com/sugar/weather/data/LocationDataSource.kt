package com.sugar.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** 定位结果(DESIGN.md §10.2):成功坐标 / 无权限 / 不可用,三选一。 */
sealed class LocationFix {
    /** 定位成功,城市级坐标。 */
    data class Fixed(val latitude: Double, val longitude: Double) : LocationFix()

    /** 无定位权限。 */
    object Denied : LocationFix()

    /** 有权限但拿不到修复:超时、无可用提供方或系统服务缺失。 */
    object Unavailable : LocationFix()
}

/**
 * 定位数据源(DESIGN.md §2.4,决策 D7):框架 LocationManager,不依赖 GMS。
 *
 * 数据层只取坐标;权限申请(ActivityResultContracts.RequestMultiplePermissions)留给 UI 层,
 * 本类只做检查 —— 无权限直接返回 [LocationFix.Denied]。
 *
 * 一次性定位策略(minSdk 29 上 `getCurrentLocation` 不可用,故统一):
 * 1. `getLastKnownLocation(NETWORK|GPS)` 若 5 分钟内 → 立即返回;
 * 2. 否则注册 `requestLocationUpdates`,首个满足精度阈值的修复后注销;
 * 3. 超时([timeoutMs],默认 10s)未得修复 → 失败([LocationFix.Unavailable],§2.4「超时视为失败」)。
 */
class LocationDataSource(private val context: Context) {

    /** 已授予 FINE 或 COARSE 之一即视为可用(用户拒绝 fine 但同意 coarse 也可定位,§2.4)。 */
    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 一次性定位。不抛权限异常;返回 [LocationFix.Fixed] / [LocationFix.Denied] / [LocationFix.Unavailable]。
     */
    suspend fun currentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationFix? {
        if (!hasPermission()) return LocationFix.Denied
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationFix.Unavailable

        val providers = PROVIDERS.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return LocationFix.Unavailable

        lastKnown(manager, providers)?.takeIf { isFresh(it) }?.let { last ->
            return LocationFix.Fixed(last.latitude, last.longitude)
        }

        val fix = withTimeoutOrNull(timeoutMs) { awaitGoodFix(manager, providers) }
        return if (fix != null) {
            LocationFix.Fixed(fix.latitude, fix.longitude)
        } else {
            LocationFix.Unavailable // 超时未得修复 → 视为失败(§2.4)
        }
    }

    // ———————————————————————— 内部实现 ————————————————————————

    @SuppressLint("MissingPermission") // 权限已由 hasPermission() 检查,lint 无法识别自研检查
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun awaitGoodFix(
        manager: LocationManager,
        providers: List<String>
    ): Location? = suspendCancellableCoroutine { continuation ->
        val thread = HandlerThread("location-one-shot").apply { start() }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.accuracy <= ACCURACY_THRESHOLD_M && continuation.isActive) {
                    // 拿到满足阈值的修复:立即注销自己并结束后台线程(一次性定位,不常驻)
                    runCatching { manager.removeUpdates(this) }
                    thread.quitSafely()
                    continuation.resumeWith(Result.success(location))
                }
            }

            // minSdk 29:平台 30+ 才为这三个回调提供默认实现,必须显式空实现,
            // 否则 API 29 设备在提供方状态变化时抛 AbstractMethodError
            @Deprecated("API 29 上仍为抽象方法,必须显式空实现")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        continuation.invokeOnCancellation {
            for (provider in providers) {
                runCatching { manager.removeUpdates(listener) }
            }
            thread.quitSafely()
        }

        var requested = false
        for (provider in providers) {
            // 用 Looper 变体(Executor 变体是 API 30+,minSdk 29 不可用);
            // 回调线程只做 continuation.resume,与协程线程无关
            val ok = runCatching {
                manager.requestLocationUpdates(provider, MIN_INTERVAL_MS, 0f, listener, thread.looper)
            }.isSuccess
            if (ok) requested = true
        }
        if (!requested) {
            for (provider in providers) {
                runCatching { manager.removeUpdates(listener) }
            }
            thread.quitSafely()
            if (continuation.isActive) continuation.resumeWith(Result.success(null))
        }
    }

    private fun isFresh(location: Location): Boolean =
        System.currentTimeMillis() - location.time <= FRESH_WINDOW_MS

    companion object {
        private val PROVIDERS = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )

        /** 默认超时(§2.4:10 秒)。 */
        private const val DEFAULT_TIMEOUT_MS = 10_000L

        /** lastKnown 视为新鲜的窗口(城市级天气场景 5 分钟足够)。 */
        private const val FRESH_WINDOW_MS = 5 * 60_000L

        /** 精度阈值:首个 accuracy ≤ 100m 的修复被接受(§2.4「满足精度阈值」)。 */
        private const val ACCURACY_THRESHOLD_M = 100f

        private const val MIN_INTERVAL_MS = 1_000L
    }
}
