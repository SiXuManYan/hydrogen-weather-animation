package com.sugar.weather.data

import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.OpenMeteoApi
import com.sugar.weather.data.remote.dto.AirQualityResponse
import com.sugar.weather.data.remote.dto.CurrentBlock
import com.sugar.weather.data.remote.dto.DailyBlock
import com.sugar.weather.data.remote.dto.ForecastResponse
import com.sugar.weather.data.remote.dto.GeocodingResponse
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仓库级城市事务回归：真实协程交错与可控制的网络、存储边界，不依赖设备。 */
class CityDeletionTest {
    /** 多选删除只移除手动城市，同时清除两层天气，定位城市保留。 */
    @Test fun `batch delete protects located city and clears both caches`() = runBlocking {
        val located = city("located", located = true)
        val manual = city("manual")
        val second = city("second", latitude = 6.0)
        fixture(listOf(located, manual, second)) { repository, storage, _ ->
            repository.cachedReport("manual")
            repository.removeCities(setOf("located", "manual", "second", "unknown"))
            assertEquals(listOf("located"), storage.cities.value.map { it.id })
            assertEquals(setOf("located"), storage.weather.keys)
            assertEquals(setOf("located"), repository.reports.value.keys)
        }
    }

    /** 纯手动列表的第一张城市同样可以删除，剩余 displayOrder 归一化。 */
    @Test fun `first manual city can be deleted and order is normalized`() = runBlocking {
        fixture(listOf(city("first"), city("second", latitude = 6.0))) { repository, storage, _ ->
            repository.removeCity("first")
            assertEquals(listOf("second"), storage.cities.value.map { it.id })
            assertEquals(0, storage.cities.value.single().displayOrder)
            repository.removeCity("second")
            assertTrue(storage.cities.value.isEmpty())
            assertTrue(repository.reports.value.isEmpty())
        }
    }

    /** 网络返回前删除城市，迟到结果必须被拒绝，不能复活内存和磁盘缓存。 */
    @Test fun `in flight refresh cannot restore a deleted city`() = runBlocking {
        fixture(listOf(city("manual"))) { repository, storage, api ->
            repository.cachedReport("manual")
            api.release = CompletableDeferred()
            val request = async { repository.refreshWeather("manual") }
            withTimeout(5_000L) { api.started.await() }
            repository.removeCity("manual")
            api.release!!.complete(Unit)
            val result = withTimeout(5_000L) { request.await() }
            assertTrue(result.exceptionOrNull() is WeatherDataException.CityNotFound)
            assertFalse("manual" in storage.weather)
            assertFalse("manual" in repository.reports.value)
        }
    }

    /** 定位、添加、排序和删除同时提交，不能用旧列表覆盖其他已完成操作。 */
    @Test fun `concurrent city mutations retain all valid changes`() = runBlocking {
        fixture(listOf(city("remove"), city("keep", latitude = 6.0))) { repository, storage, _ ->
            awaitAll(
                async { repository.removeCity("remove") },
                async { repository.addCity(city("added", latitude = 7.0)) },
                async { repository.upsertLocatedCity(name = "当前位置", latitude = 8.0, longitude = 11.0) },
                async { repository.reorderCities(listOf("keep", "keep", "remove")) }
            )
            val cities = storage.cities.value
            assertTrue(cities.first().isLocated)
            assertEquals(setOf("keep", "added"), cities.filterNot { it.isLocated }.map { it.id }.toSet())
            assertEquals(cities.indices.toList(), cities.map { it.displayOrder })
            assertEquals(cities.size, cities.map { it.id }.distinct().size)
        }
    }

    /** 同坐标不同 id 的搜索结果应返回已有定位城市，主页才能切回正确卡片。 */
    @Test fun `adding duplicate coordinates returns retained city identity`() = runBlocking {
        fixture(listOf(city("located", located = true))) { repository, storage, _ ->
            val actual = repository.addCity(city("search-result"))
            assertEquals("located", actual.id)
            assertTrue(actual.isLocated)
            assertEquals(1, storage.cities.value.size)
        }
    }

    /** 原地从市名细化为区名，只更新名称，城市身份、顺序和两层天气保持不变。 */
    @Test fun `located city name can be refined without moving or dropping caches`() = runBlocking {
        val located = city(id = "located", located = true).copy(name = "北京市")
        val manual = city(id = "manual", latitude = 6.0).copy(displayOrder = 1)
        fixture(listOf(located, manual)) { repository, storage, api ->
            val memoryReport = checkNotNull(repository.cachedReport(cityId = located.id))
            val diskReport = checkNotNull(storage.weather[located.id])

            repository.updateLocatedCityName(city = located, name = " 海淀区 ")

            assertEquals(listOf(located.copy(name = "海淀区"), manual), storage.cities.value)
            assertSame(memoryReport, repository.reports.value[located.id])
            assertSame(diskReport, storage.weather[located.id])
            assertFalse(api.started.isCompleted)
        }
    }

    /** 系统缺测、空白或名称未变化时不降级为回退名，也不清空已保存天气。 */
    @Test fun `missing or unchanged located name preserves saved place`() = runBlocking {
        val located = city(id = "located", located = true).copy(name = "海淀区")
        fixture(listOf(located)) { repository, storage, _ ->
            val report = checkNotNull(repository.cachedReport(cityId = located.id))
            for (name in listOf(null, " \t ", " 海淀区 ")) {
                repository.updateLocatedCityName(city = located, name = name)
            }
            assertEquals(listOf(located), storage.cities.value)
            assertSame(report, repository.reports.value[located.id])
            assertTrue(located.id in storage.weather)
        }
    }

    /** 反地理返回前已移动到其他坐标时，旧名称不能写到新地点上。 */
    @Test fun `late located name cannot overwrite a moved city`() = runBlocking {
        val located = city(id = "located", located = true).copy(name = "北京市")
        fixture(listOf(located)) { repository, storage, _ ->
            val moved = repository.upsertLocatedCity(
                name = "西湖区", latitude = 8.0, longitude = 11.0
            )
            repository.updateLocatedCityName(city = located, name = "海淀区")
            assertEquals(listOf(moved), storage.cities.value)
        }
    }

    /** 同一坐标已经提交新名称后，基于旧名称的迟到结果也应被拒绝。 */
    @Test fun `late located name cannot overwrite a newer name`() = runBlocking {
        val located = city(id = "located", located = true).copy(name = "北京市")
        fixture(listOf(located)) { repository, storage, _ ->
            repository.updateLocatedCityName(city = located, name = "海淀区")
            repository.updateLocatedCityName(city = located, name = "朝阳区")
            assertEquals(listOf(located.copy(name = "海淀区")), storage.cities.value)
        }
    }

    /** 只改名入口不触碰手动城市，过期或缺失的城市快照也不能新建条目。 */
    @Test fun `located name update ignores manual and missing cities`() = runBlocking {
        val manual = city(id = "manual")
        fixture(listOf(manual)) { repository, storage, _ ->
            repository.updateLocatedCityName(city = manual, name = "海淀区")
            repository.updateLocatedCityName(
                city = city(id = "missing", located = true), name = "海淀区"
            )
            assertEquals(listOf(manual), storage.cities.value)
        }
    }

    /** 落盘失败不能提前清除内存天气或误报删除成功。 */
    @Test fun `failed deletion keeps city and caches available for retry`() = runBlocking {
        fixture(listOf(city("manual"))) { repository, storage, _ ->
            repository.cachedReport("manual")
            storage.failUpdates = true
            assertTrue(
                runCatching { repository.removeCity("manual") }.exceptionOrNull() is WeatherDataException.Store
            )
            assertEquals(listOf("manual"), storage.cities.value.map { it.id })
            assertTrue("manual" in storage.weather)
            assertTrue("manual" in repository.reports.value)
        }
    }

    /** 固定在中国模型范围外，便于仅控制单次 forecast 返回时机。 */
    private fun city(id: String, latitude: Double = 5.0, located: Boolean = false): City = City(
        id = id, name = id, latitude = latitude, longitude = 10.0,
        isLocated = located, addedAtMillis = 1L
    )

    /** 每个测试独立仓库及进程作用域，结束时取消共享流。 */
    private suspend fun fixture(
        cities: List<City>,
        block: suspend (WeatherRepository, MemoryStorage, ControlledApi) -> Unit
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val storage = MemoryStorage(cities)
        val api = ControlledApi()
        val repository = WeatherRepository(
            cityStore = storage, forecastApi = api, airQualityApi = api,
            geocodingApi = api, externalScope = scope
        )
        try {
            block(repository, storage, api)
        } finally {
            scope.cancel()
        }
    }

    /**
     * 在事务内挂起以放大并发读改写窗口，并模拟落盘失败。
     * @param initial 初始城市及其已有天气缓存。
     */
    private class MemoryStorage(initial: List<City>) : CityStorage {
        override val cities = MutableStateFlow(initial)
        /** 模拟磁盘缓存，与仓库内存报告独立。 */
        val weather = initial.associate { city ->
            city.id to deletionFixtureForecast(latitude = city.latitude, longitude = city.longitude)
                .toDomain(cityId = city.id, fetchedAtMillis = 1L, pm25 = null)
        }
            .toMutableMap()
        /** 在修改前模拟存储失败。 */
        var failUpdates = false
        /** 模拟 DataStore 的事务边界。 */
        private val mutex = Mutex()

        override suspend fun updateCities(transform: (List<City>) -> List<City>): List<City> =
            mutex.withLock {
                val old = cities.value
                yield()
                if (failUpdates) throw IOException("storage unavailable")
                val updated = transform(old)
                val retained = updated.map { it.id }.toSet()
                weather.keys.retainAll(retained)
                cities.value = updated
                updated
            }

        override suspend fun saveWeather(cityId: String, report: WeatherReport) {
            mutex.withLock { weather[cityId] = report }
        }

        override suspend fun loadAllCachedWeather(): Map<String, WeatherReport> =
            mutex.withLock { weather.toMap() }
    }

    /** 可在 forecast 已开始后挂起，精确安排删除与网络返回的先后关系。 */
    private class ControlledApi : OpenMeteoApi {
        /** 请求开始的同步信号。 */
        val started = CompletableDeferred<Unit>()
        /** 为空时立即返回，否则等待测试释放。 */
        var release: CompletableDeferred<Unit>? = null

        override suspend fun forecast(
            latitude: Double, longitude: Double, current: String, hourly: String,
            daily: String, timezone: String, forecastDays: Int, windSpeedUnit: String, models: String?
        ): ForecastResponse {
            started.complete(Unit)
            release?.await()
            return deletionFixtureForecast(latitude = latitude, longitude = longitude)
        }

        override suspend fun airQuality(
            latitude: Double, longitude: Double, current: String, timezone: String
        ): AirQualityResponse = AirQualityResponse(current = null)

        override suspend fun searchCity(
            name: String, count: Int, language: String, format: String
        ): GeocodingResponse = GeocodingResponse(results = emptyList())
    }
}

/** 删除测试的成功响应包含实况与日报，让断言继续聚焦城市事务而非缺测拒绝。 */
private fun deletionFixtureForecast(latitude: Double, longitude: Double): ForecastResponse = ForecastResponse(
    latitude = latitude,
    longitude = longitude,
    timezone = "UTC",
    current = CurrentBlock(
        time = "2026-11-06T12:00", interval = 900, temperature_2m = 10.0,
        relative_humidity_2m = 50.0, apparent_temperature = 8.0, is_day = 1,
        weather_code = 0, wind_speed_10m = 5.0, wind_direction_10m = 0.0, pressure_msl = 1010.0
    ),
    hourly = null,
    daily = DailyBlock(
        time = listOf("2026-11-06"), weather_code = listOf(0), temperature_2m_max = listOf(15.0),
        temperature_2m_min = listOf(5.0), sunrise = null, sunset = null, uv_index_max = null,
        precipitation_probability_max = null, wind_speed_10m_max = null
    )
)
