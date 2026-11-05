package com.sugar.weather.data

import com.google.gson.JsonSyntaxException
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.CitySearchResult
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.OpenMeteoApi
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 天气数据仓库(DESIGN.md §9):唯一的数据出入口。
 *
 * - 所有网络访问收敛在本类(§1.4):DTO 只存在于 data/remote/dto/,换数据源 = 新增 DTO 包 + 改 Mapper;
 * - 暴露 suspend 函数与 StateFlow,内存层 `reports` + DataStore 缓存(经 CityStore);
 * - 异常纪律(ask 约束):对外只出现 [WeatherDataException],经 [Result] 或直接抛出;
 *   协程取消(CancellationException)原样重抛,不吞。
 *
 * 刷新失败语义(§9):forecast 失败 → 整体失败;air-quality 失败 → 保留旧 pm25,卡片显示「—」。
 */
class WeatherRepository(
    private val cityStore: CityStore,
    private val forecastApi: OpenMeteoApi,
    private val airQualityApi: OpenMeteoApi,
    private val geocodingApi: OpenMeteoApi,
    externalScope: CoroutineScope
) {

    private val _reports = MutableStateFlow<Map<String, WeatherReport>>(emptyMap())

    /** cityId → 最近一次成功抓取的天气报告(内存层;启动时从 DataStore 缓存预热)。 */
    val reports: StateFlow<Map<String, WeatherReport>> = _reports.asStateFlow()

    /** 城市列表(displayOrder 与列表下标一致,定位城市固定第 0 位)。 */
    val cities: StateFlow<List<City>> = cityStore.cities
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    init {
        // 进程重启后把磁盘缓存预热进内存,离线场景(15 天页/城市管理页)才有数据(§9)。
        externalScope.launch {
            val cached = cityStore.loadAllCachedWeather()
            _reports.update { current -> current + cached.filterKeys { it !in current } }
        }
    }

    /**
     * 刷新一个城市的天气:forecast + air-quality 并行。
     * 成功 → 更新内存并落盘缓存,返回 [Result.success];
     * 失败 → [Result.failure],异常为 [WeatherDataException] 子类型。
     */
    suspend fun refreshWeather(cityId: String): Result<WeatherReport> = try {
        val city = resolveCity(cityId)
            ?: return Result.failure(WeatherDataException.CityNotFound(cityId))
        val previous = _reports.value[cityId]
        coroutineScope {
            val airDeferred = async {
                try {
                    airQualityApi.airQuality(city.latitude, city.longitude)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    null // air-quality 失败不拖垮整体(§9),pm25 沿用旧值
                }
            }
            // 中国境内并行请求 CMA GRAPES 前 5 天;失败静默回退 best_match。
            // 国内官方 App 通常使用 CMA 数据,优先它可以显著减少降水相态(雨/雪)偏差。
            val cmaDeferred = if (isInChina(city.latitude, city.longitude)) {
                async {
                    try {
                        forecastApi.forecast(
                            latitude = city.latitude,
                            longitude = city.longitude,
                            forecastDays = CMA_FORECAST_DAYS,
                            models = OpenMeteoApi.CMA_MODEL
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        null
                    }
                }
            } else {
                null
            }
            val fallbackForecast = forecastApi.forecast(city.latitude, city.longitude)
            val air = airDeferred.await()
            val cmaForecast = cmaDeferred?.await()
            val pm25 = air?.current?.pm2_5 ?: previous?.current?.pm25
            val fetchedAt = System.currentTimeMillis()
            val fallbackReport = fallbackForecast.toDomain(cityId, fetchedAt, pm25)
            val report = cmaForecast
                ?.toDomain(cityId, fetchedAt, pm25)
                ?.let { mergeReports(fallback = fallbackReport, cma = it) }
                ?: fallbackReport
            _reports.update { it + (cityId to report) }
            try {
                cityStore.saveWeather(cityId, report)
            } catch (storeError: Exception) {
                // 缓存写失败不影响本次展示;内存层已更新,下次刷新重试落盘
                if (storeError is CancellationException) throw storeError
            }
            Result.success(report)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(WeatherDataException.wrap(error))
    }

    /**
     * 『15 天天气预报』页的数据入口(§3:应用内详情页,零额外网络请求):
     * 优先返回内存缓存(含启动时预热的磁盘缓存),无缓存才现场 refreshWeather。
     * daily 共 16 条(今天 + 未来 15 天),UI 取 [WeatherReport.daily] 前 6 条画趋势、[1..15] 画详情。
     */
    suspend fun cachedOrRefresh(cityId: String): Result<WeatherReport> {
        _reports.value[cityId]?.let { return Result.success(it) }
        return refreshWeather(cityId)
    }

    /** 城市搜索(§6.4):population 非空且大的排前,同人口按 name 长度升序。 */
    suspend fun searchCity(query: String): List<CitySearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            geocodingApi.searchCity(name = trimmed).sortedResults()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw WeatherDataException.wrap(error)
        }
    }

    /** 追加城市(搜索页/热门城市);同 id 或同坐标(±1e-4°)视为重复,忽略。 */
    suspend fun addCity(city: City) {
        mutateCities { list ->
            if (list.any { it.id == city.id || isSamePlace(it, city) }) {
                list
            } else {
                list + city.copy(addedAtMillis = System.currentTimeMillis())
            }
        }
    }

    /** 删除城市;定位城市不可删除(§2.4 第 4 条),静默忽略。成功删除时清其天气缓存。 */
    suspend fun removeCity(cityId: String) {
        val removed = mutateCities { list ->
            val target = list.firstOrNull { it.id == cityId }
            if (target == null || target.isLocated) list else list.filterNot { it.id == cityId }
        }
        if (removed) cityStore.removeWeatherCache(cityId)
    }

    /** 拖拽排序(城市管理页):按 orderedIds 重排;缺失 id 的城市追加在末尾,定位城市仍钉在 0 位。 */
    suspend fun reorderCities(orderedIds: List<String>) {
        mutateCities { list ->
            val byId = list.associateBy { it.id }
            orderedIds.mapNotNull { byId[it] } + list.filter { it.id !in orderedIds.toSet() }
        }
    }

    /**
     * 插入或更新定位城市(§2.4 回退链第 2/3/4 条):
     * 已有定位城市 → 只更新名称与坐标;同坐标普通城市 → 就地转正并置顶;
     * 否则新建。[name] 为 null(Geocoder 失败)→ 使用回退名「我的位置」。
     * 结果固定 displayOrder=0,不可删除、不参与拖拽。
     */
    suspend fun upsertLocatedCity(name: String?, latitude: Double, longitude: Double): City {
        // name == null(Geocoder 失败)时:已有城市沿用旧名,新建城市用回退名「我的位置」
        val resolvedName = name?.takeIf { it.isNotBlank() }
        val current = cityStore.cities.first()
        val existingLocated = current.firstOrNull { it.isLocated }
        val samePlace = current.firstOrNull {
            !it.isLocated && isSamePlace(it.latitude, it.longitude, latitude, longitude)
        }
        val target = when {
            existingLocated != null -> existingLocated.copy(
                name = resolvedName ?: existingLocated.name,
                latitude = latitude,
                longitude = longitude
            )
            samePlace != null -> samePlace.copy(
                isLocated = true,
                name = resolvedName ?: samePlace.name,
                latitude = latitude,
                longitude = longitude
            )
            else -> City(
                id = UUID.randomUUID().toString(),
                name = resolvedName ?: LOCATED_CITY_FALLBACK_NAME,
                latitude = latitude,
                longitude = longitude,
                isLocated = true
            )
        }
        val updated = normalize(listOf(target) + current.filterNot { it.id == target.id })
        if (updated != current) cityStore.saveCities(updated)
        return target
    }

    // ———————————————————————— 内部实现 ————————————————————————

    /**
     * 合并中国区 CMA 与 best_match 报告:
     * - 实况/时区优先 CMA(更贴近国内官方预报);
     * - 逐日:CMA 覆盖的前 5 天优先,其余日期保留 best_match;
     * - 逐时:按时间合并,CMA 优先(其 5 天完整覆盖首页 24h 窗口)。
     */
    private fun mergeReports(fallback: WeatherReport, cma: WeatherReport): WeatherReport {
        val cmaDates = cma.daily.mapTo(HashSet()) { it.date }
        val daily = (cma.daily + fallback.daily.filter { it.date !in cmaDates }).sortedBy { it.date }
        val hourly = (cma.hourly + fallback.hourly)
            .distinctBy { it.time }
            .sortedBy { it.time }
        return WeatherReport(
            cityId = fallback.cityId,
            fetchedAtMillis = fallback.fetchedAtMillis,
            timezone = cma.timezone ?: fallback.timezone,
            observedAt = cma.observedAt ?: fallback.observedAt,
            current = cma.current ?: fallback.current,
            daily = daily,
            hourly = hourly
        )
    }

    /**
     * 城市列表读-改-写:以 DataStore 的最新值为基准(避免 cities StateFlow 尚未就绪时的竞态),
     * 写前经 [normalize] 归一化。返回列表是否发生实际变化。
     */
    private suspend fun mutateCities(transform: (List<City>) -> List<City>): Boolean {
        val current = cityStore.cities.first()
        val updated = normalize(transform(current))
        if (updated == current) return false
        cityStore.saveCities(updated)
        return true
    }

    /** 归一化:定位城市钉在 0 位(最多一个),其余保持相对顺序;displayOrder = 列表下标。 */
    private fun normalize(list: List<City>): List<City> {
        val located = list.firstOrNull { it.isLocated }
        val others = list.filter { !it.isLocated }
        return (if (located != null) listOf(located) else emptyList())
            .plus(others)
            .mapIndexed { index, city -> city.copy(displayOrder = index) }
    }

    /** 优先内存,回退 DataStore;都无 → null(触发 CityNotFound)。 */
    private suspend fun resolveCity(cityId: String): City? =
        cities.value.firstOrNull { it.id == cityId }
            ?: cityStore.cities.first().firstOrNull { it.id == cityId }

    private fun isSamePlace(a: City, b: City): Boolean =
        isSamePlace(a.latitude, a.longitude, b.latitude, b.longitude)

    private fun isSamePlace(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean =
        kotlin.math.abs(lat1 - lat2) < COORDINATE_EPSILON &&
            kotlin.math.abs(lon1 - lon2) < COORDINATE_EPSILON

    companion object {
        /** Geocoder 反地理失败的定位城市回退名(§2.4 第 3 条)。 */
        const val LOCATED_CITY_FALLBACK_NAME = "我的位置"

        /** 同坐标判重的经纬度容差(约 11m)。 */
        private const val COORDINATE_EPSILON = 1e-4

        /** CMA GRAPES 可用预报天数;超出部分回退 best_match。 */
        private const val CMA_FORECAST_DAYS = 5
    }
}
