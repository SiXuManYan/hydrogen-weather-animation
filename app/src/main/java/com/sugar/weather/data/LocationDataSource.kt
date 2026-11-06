package com.sugar.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/** 定位结果(DESIGN.md §10.2):成功坐标 / 无权限 / 不可用,三选一。 */
sealed class LocationFix {
    /**
     * 定位成功，精度供前台判断是否发生了足够大的位移。
     * @property latitude 城市级纬度。
     * @property longitude 城市级经度。
     * @property accuracyMeters 系统报告的水平精度，单位米。
     */
    data class Fixed(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float
    ) : LocationFix()

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
 * 1. 5 分钟内的历史位置达到当前权限的精度门槛时立即返回；
 * 2. 精确权限等待 100m 内的位置，大致权限接受 5km 内的城市级位置；
 * 3. 最多等待 10 秒，期间记录最佳有效位置，超时可回退到 10km 内的候选。
 * @property context 用于检查权限和取得系统定位服务的应用上下文。
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
    suspend fun currentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationFix {
        if (!hasPermission()) return LocationFix.Denied
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val accuracyThreshold = if (fineGranted) FINE_ACCURACY_M else COARSE_ACCURACY_M
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationFix.Unavailable

        val providers = PROVIDERS.filter { provider ->
            (fineGranted || provider == LocationManager.NETWORK_PROVIDER) &&
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return LocationFix.Unavailable

        val best = AtomicReference(lastKnown(manager = manager, providers = providers))
        best.get()?.takeIf { it.accuracy <= accuracyThreshold }?.let { last ->
            return last.toFix()
        }

        val fix = withTimeoutOrNull(timeoutMs) {
            // 监听回调只做候选比较；复用主 Looper，避免为每次定位常驻一个线程。
            withContext(Dispatchers.Main.immediate) {
                awaitGoodFix(
                    manager = manager,
                    providers = providers,
                    accuracyThreshold = accuracyThreshold,
                    best = best
                )
            }
        }
        return (fix ?: best.get()?.takeIf(::isUsable))?.toFix() ?: LocationFix.Unavailable
    }

    // ———————————————————————— 内部实现 ————————————————————————

    /** 在新鲜、精度可用的历史位置中优先选择更准确的候选。 */
    @SuppressLint("MissingPermission") // 权限已由 hasPermission() 检查,lint 无法识别自研检查
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter(::isUsable).minWithOrNull(
            compareBy<Location> { it.accuracy }.thenByDescending { it.elapsedRealtimeNanos }
        )

    /** 达标位置立即结束；未达理想精度的有效候选留给超时回退。 */
    @SuppressLint("MissingPermission")
    private suspend fun awaitGoodFix(
        manager: LocationManager,
        providers: List<String>,
        accuracyThreshold: Float,
        best: AtomicReference<Location?>
    ): Location? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            /** 丢弃过期或无精度位置，理想候选则一次性完成并注销。 */
            override fun onLocationChanged(location: Location) {
                if (!continuation.isActive || !isUsable(location)) return
                best.updateAndGet { previous ->
                    if (previous == null || !isUsable(previous) ||
                        location.accuracy < previous.accuracy ||
                        (location.accuracy == previous.accuracy &&
                            location.elapsedRealtimeNanos > previous.elapsedRealtimeNanos)
                    ) location else previous
                }
                if (location.accuracy <= accuracyThreshold) {
                    runCatching { manager.removeUpdates(this) }
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
            // 超时和页面离开都走同一个注销入口，不留下后台定位监听。
            runCatching { manager.removeUpdates(listener) }
        }

        var requested = false
        for (provider in providers) {
            // 在同一个主 Looper 注册与回调，避免尚未完成注册就提前注销的竞争。
            val ok = runCatching {
                manager.requestLocationUpdates(
                    provider, MIN_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
                )
            }.isSuccess
            if (ok) requested = true
        }
        if (!requested) {
            runCatching { manager.removeUpdates(listener) }
            if (continuation.isActive) continuation.resumeWith(Result.success(null))
        }
    }

    /** 使用单调时钟计算年龄，设备校时不会把陈旧或未来位置当作新鲜位置。 */
    private fun isUsable(location: Location): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return ageNanos in 0..FRESH_WINDOW_NANOS &&
            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0 &&
            location.hasAccuracy() && location.accuracy.isFinite() &&
            location.accuracy in 0f..FALLBACK_ACCURACY_M
    }

    /** 保留水平精度，避免把大致定位的正常抖动当作跨城移动。 */
    private fun Location.toFix(): LocationFix.Fixed = LocationFix.Fixed(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy
    )

    companion object {
        /** 精确权限可同时使用网络和 GPS，大致权限仅使用网络提供方。 */
        private val PROVIDERS = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )

        /** 默认超时(§2.4:10 秒)。 */
        private const val DEFAULT_TIMEOUT_MS = 10_000L

        /** lastKnown 视为新鲜的窗口(城市级天气场景 5 分钟足够)。 */
        private const val FRESH_WINDOW_NANOS = 5 * 60_000_000_000L

        /** 精确定位优先等待百米内位置。 */
        private const val FINE_ACCURACY_M = 100f

        /** Android 的大致定位适用于城市天气，不要求达到精确权限的百米门槛。 */
        private const val COARSE_ACCURACY_M = 5_000f

        /** 超时只回退到仍适合城市级天气的位置，拒绝无界的粗糙候选。 */
        private const val FALLBACK_ACCURACY_M = 10_000f

        /** 每秒至多请求一次新位置。 */
        private const val MIN_INTERVAL_MS = 1_000L
    }
}
