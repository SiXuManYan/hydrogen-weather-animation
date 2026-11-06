package com.sugar.weather.widget

import com.sugar.weather.data.CityStorage
import com.sugar.weather.data.WeatherRepository
import com.sugar.weather.data.isStale
import com.sugar.weather.data.model.City
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 连接天气仓库、系统桌面与后台任务，所有实例跟随城市列表首项。
 * @param host 桌面渲染和系统调度边界。
 * @param cityStore 提供已加载的城市快照，避免把共享流的初始空值当成真实空列表。
 * @param repository 与应用页面共用的缓存和网络边界。
 * @param scope 持有缓存同步观察器的应用作用域。
 */
internal class WeatherWidgetController(
    private val host: WeatherWidgetHost,
    private val cityStore: CityStorage,
    private val repository: WeatherRepository,
    private val scope: CoroutineScope
) {
    /** 自动联网和用户刷新串行，缓存重绘无需等待网络请求。 */
    private val refreshMutex = Mutex()

    /** 读取最新快照和提交 RemoteViews 共用一把锁，避免旧渲染覆盖新状态。 */
    private val renderMutex = Mutex()

    /** 临时状态绑定城市和坐标，定位更新后不沿用上一地点的请求状态。 */
    private val refreshStatus = MutableStateFlow(WidgetRefreshStatus())

    /** 应用初始化时调用一次；前台刷新和城市变更都自动反映到桌面。 */
    fun start() {
        scope.launch {
            if (host.hasWidgets) host.ensureScheduled()
            var initialized = false
            var previousCityIdentity: WidgetCityIdentity? = null
            combine(cityStore.cities, repository.reports, refreshStatus) { cities, reports, status ->
                val city = cities.firstOrNull()
                Triple(city, city?.let { reports[it.id] }, status)
            }.distinctUntilChanged().collect { (city, _, _) ->
                val identity = city?.widgetIdentity()
                if (host.hasWidgets) {
                    renderCached()
                    if (initialized && identity != previousCityIdentity) {
                        host.requestRefresh()
                    }
                }
                previousCityIdentity = identity
                initialized = true
            }
        }
    }

    /** 周期任务与即时入口只重绘缓存；无微件时停止后续联网工作。 */
    suspend fun redrawCached(): Boolean {
        if (!host.hasWidgets) {
            host.cancel()
            return false
        }
        renderCached()
        return host.hasWidgets
    }

    /** Worker 持有后台执行额度；缓存先显示，过期才联网，失败不清空已有报告。 */
    suspend fun refresh(force: Boolean): Boolean = refreshMutex.withLock {
        if (!host.hasWidgets) {
            host.cancel()
            return@withLock true
        }
        var forceCurrentCity = force
        // 请求期间首城变化时在当前 Worker 内追上新城市，不能依赖 KEEP 队列再次入队。
        repeat(3) {
            if (!host.hasWidgets) return@withLock true
            val city = cityStore.cities.first().firstOrNull()
            val cached = city?.let { repository.cachedReport(it.id) }
            renderCached()
            if (city == null) return@withLock true
            val identity = city.widgetIdentity()
            val freshCache = cached?.takeUnless { it.isStale() }?.let {
                WeatherWidgetState.from(city = city, report = it).hasWeather
            } == true
            if (!forceCurrentCity && freshCache) {
                if (cityStore.cities.first().firstOrNull()?.widgetIdentity() == identity) return@withLock true
            } else {
                refreshStatus.value = WidgetRefreshStatus(
                    cityIdentity = identity, stage = WidgetRefreshStage.REFRESHING
                )
                renderCached()
                try {
                    val result = repository.refreshWeather(city.id)
                    val success = result.getOrNull()?.let {
                        WeatherWidgetState.from(city = city, report = it).hasWeather
                    } == true
                    refreshStatus.value = WidgetRefreshStatus(
                        cityIdentity = identity,
                        stage = if (success) WidgetRefreshStage.IDLE else WidgetRefreshStage.FAILED,
                        reportFetchedAtMillis = result.getOrNull()?.fetchedAtMillis ?: cached?.fetchedAtMillis
                    )
                    renderCached()
                    if (cityStore.cities.first().firstOrNull()?.widgetIdentity() == identity) return@withLock success
                } finally {
                    // 取消不能留下永久“正在更新”；下次广播仍会从缓存恢复。
                    if (refreshStatus.value.stage == WidgetRefreshStage.REFRESHING) {
                        refreshStatus.value = WidgetRefreshStatus()
                    }
                }
            }
            forceCurrentCity = false
        }
        // 连续多次重排时有界退出，让 WorkManager 退避重试，避免后台无界联网。
        false
    }

    /** 缓存预热后重新读取首城，保证删除、排序和冷启动时不串城。 */
    private suspend fun renderCached() = renderMutex.withLock {
        if (!host.hasWidgets) return@withLock
        val city = cityStore.cities.first().firstOrNull()
        val report = city?.let { repository.cachedReport(it.id) }
        val state = WeatherWidgetState.from(city = city, report = report)
        val status = refreshStatus.value.takeIf {
            it.cityIdentity == city?.widgetIdentity() && (it.stage != WidgetRefreshStage.FAILED ||
                it.reportFetchedAtMillis == report?.fetchedAtMillis)
        }?.stage ?: WidgetRefreshStage.IDLE
        host.show(state = state, stage = status)
    }
}

/** 微件的临时请求状态；进程重启后以持久天气缓存为准。 */
internal enum class WidgetRefreshStage { IDLE, REFRESHING, FAILED }

/**
 * 请求地点绑定临时状态，避免城市或坐标变化时沿用旧请求的错误提示。
 * @property cityIdentity 发起请求时的城市标识与坐标。
 * @property stage 当前请求阶段。
 * @property reportFetchedAtMillis 失败时对应的旧报告；前台成功更新后不再沿用该失败标记。
 */
private data class WidgetRefreshStatus(
    val cityIdentity: WidgetCityIdentity? = null,
    val stage: WidgetRefreshStage = WidgetRefreshStage.IDLE,
    val reportFetchedAtMillis: Long? = null
)

/**
 * 城市 id 不随重新定位改变，坐标也必须参与缓存检查与请求追踪。
 * @property id 城市的持久标识。
 * @property latitude 请求地点的纬度。
 * @property longitude 请求地点的经度。
 */
private data class WidgetCityIdentity(val id: String, val latitude: Double, val longitude: Double)

/** 只比较影响天气归属的字段，重命名或排序不必重复请求。 */
private fun City.widgetIdentity(): WidgetCityIdentity =
    WidgetCityIdentity(id = id, latitude = latitude, longitude = longitude)
