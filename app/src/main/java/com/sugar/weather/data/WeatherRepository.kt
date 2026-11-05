package com.sugar.weather.data

import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.CitySearchResult
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.OpenMeteoApi
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 天气数据仓库(DESIGN.md §9):唯一的数据出入口。
 *
 * - 所有网络访问收敛在本类(§1.4):DTO 只存在于 data/remote/dto/,换数据源 = 新增 DTO 包 + 改 Mapper;
 * - 暴露 suspend 函数与 StateFlow,内存层 `reports` + DataStore 缓存(经 CityStore);
 * - 异常纪律(ask 约束):对外只出现 [WeatherDataException],经 [Result] 或直接抛出;
 *   协程取消(CancellationException)原样重抛,不吞。
 *
 * 刷新失败语义(§9):forecast 失败 → 整体失败;air-quality 失败 → 保留旧 pm25,卡片显示「—」。
 *
 * @param cityStore 城市事务与天气持久化边界。
 * @param forecastApi 预报网络接口。
 * @param airQualityApi 空气质量接口。
 * @param geocodingApi 城市搜索接口。
 * @param externalScope 承载共享流和启动预热的进程级作用域。
 */
class WeatherRepository(
    private val cityStore: CityStorage,
    private val forecastApi: OpenMeteoApi,
    private val airQualityApi: OpenMeteoApi,
    private val geocodingApi: OpenMeteoApi,
    externalScope: CoroutineScope
) {

    private val _reports = MutableStateFlow<Map<String, WeatherReport>>(emptyMap())

    /** 城市修改、缓存预热和刷新提交共用此锁；网络请求始终在锁外执行。 */
    private val cityMutationMutex = Mutex()

    /** cityId → 最近一次成功抓取的天气报告(内存层;启动时从 DataStore 缓存预热)。 */
    val reports: StateFlow<Map<String, WeatherReport>> = _reports.asStateFlow()

    /** 城市列表(displayOrder 与列表下标一致,定位城市固定第 0 位)。 */
    val cities: StateFlow<List<City>> = cityStore.cities
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    /** 缓存预热完成后再判断是否缺数据，避免冷启动时先发一轮多余请求。 */
    private val cacheReady = externalScope.launch {
        cityMutationMutex.withLock {
            val retainedIds = cityStore.cities.first().mapTo(HashSet()) { it.id }
            val cached = cityStore.loadAllCachedWeather().filterKeys { it in retainedIds }
            _reports.update { current -> cached + current }
        }
    }

    /** 等待磁盘预热，取得当前城市缓存；此过程不访问网络。 */
    suspend fun cachedReport(cityId: String): WeatherReport? {
        cacheReady.join()
        return _reports.value[cityId]
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
            // 小时聚合、模型合并移出主线程，刷新时不阻塞滚动和动画。
            val report = withContext(Dispatchers.Default) {
                val fallbackReport = fallbackForecast.toDomain(cityId, fetchedAt, pm25)
                cmaForecast
                    ?.toDomain(cityId, fetchedAt, pm25)
                    ?.let { mergeReports(fallback = fallbackReport, cma = it) }
                    ?: fallbackReport
            }
            cityMutationMutex.withLock {
                // 不使用可能滞后的 StateFlow；检查、内存更新及落盘必须与删除串行。
                val latest = cityStore.cities.first().firstOrNull { it.id == cityId }
                if (latest == null || latest.latitude != city.latitude ||
                    latest.longitude != city.longitude || latest.addedAtMillis != city.addedAtMillis
                ) {
                    return@withLock Result.failure(WeatherDataException.CityNotFound(cityId))
                }
                _reports.update { it + (cityId to report) }
                try {
                    withContext(Dispatchers.IO) { cityStore.saveWeather(cityId, report) }
                } catch (storeError: Exception) {
                    // 缓存写失败不影响本次展示；取消仍需传播给页面生命周期。
                    if (storeError is CancellationException) throw storeError
                }
                Result.success(report)
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(WeatherDataException.wrap(error))
    }

    /**
     * 详情页优先复用新鲜缓存；过期或无缓存时刷新，失败则保留旧报告供离线查看。
     */
    suspend fun cachedOrRefresh(cityId: String): Result<WeatherReport> {
        val cached = cachedReport(cityId)
        if (cached != null && !cached.isStale()) return Result.success(cached)
        val result = refreshWeather(cityId)
        return if (result.isFailure && cached != null) Result.success(cached) else result
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

    /** 追加城市；重复时返回实际保留的城市，供搜索页正确切回已有卡片。 */
    suspend fun addCity(city: City): City {
        val updated = mutateCities { list ->
            if (list.any { it.id == city.id || isSamePlace(it, city) }) list
            else list + city.copy(addedAtMillis = System.currentTimeMillis())
        }
        return updated.first { it.id == city.id || isSamePlace(it, city) }
    }

    /** 单项删除复用批量入口；定位城市始终受到仓库保护。 */
    suspend fun removeCity(cityId: String) {
        removeCities(setOf(cityId))
    }

    /** 一次提交多选删除，城市条目、磁盘缓存及内存天气同步移除。 */
    suspend fun removeCities(cityIds: Set<String>) {
        mutateCities { list ->
            list.filter { it.isLocated || it.id !in cityIds }
        }
    }

    /** 拖拽排序(城市管理页):按 orderedIds 重排;缺失 id 的城市追加在末尾,定位城市仍钉在 0 位。 */
    suspend fun reorderCities(orderedIds: List<String>) {
        mutateCities { list ->
            val byId = list.associateBy { it.id }
            val uniqueIds = orderedIds.distinct()
            val ordered = uniqueIds.toSet()
            uniqueIds.mapNotNull { byId[it] } + list.filter { it.id !in ordered }
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
        val updated = mutateCities { current ->
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
            listOf(target) + current.filterNot { it.id == target.id }
        }
        return updated.first { it.isLocated }
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
     * 城市列表的事务入口：先归一化并持久化，再移除不再属于列表的内存报告。
     * 定位、添加、排序及删除都走这里，防止相互覆盖或被迟到的天气重新写回。
     */
    private suspend fun mutateCities(transform: (List<City>) -> List<City>): List<City> = try {
        cityMutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val updated = cityStore.updateCities { current -> normalize(transform(current)) }
                val retainedIds = updated.mapTo(HashSet()) { it.id }
                _reports.update { reports -> reports.filterKeys { it in retainedIds } }
                updated
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw WeatherDataException.Store(error)
    }

    /** 归一化:定位城市钉在 0 位(最多一个),其余保持相对顺序;displayOrder = 列表下标。 */
    private fun normalize(list: List<City>): List<City> {
        val located = list.firstOrNull { it.isLocated }
        val others = list.filter { !it.isLocated }
        return (if (located != null) listOf(located) else emptyList())
            .plus(others)
            .mapIndexed { index, city -> city.copy(displayOrder = index) }
    }

    /** 直接读取存储快照，避免刚删除的城市仍暂留在共享 StateFlow 中。 */
    private suspend fun resolveCity(cityId: String): City? =
        cityStore.cities.first().firstOrNull { it.id == cityId }

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
