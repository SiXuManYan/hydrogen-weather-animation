package com.sugar.weather.data

import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.OpenMeteoApi
import com.sugar.weather.data.remote.dto.AirQualityResponse
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仓库级删除回归：真实协程交错与可控制的网络、存储边界，不依赖设备。 */
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
        val weather = initial.associate { it.id to WeatherReport(cityId = it.id, fetchedAtMillis = 1L) }
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
            return ForecastResponse(
                latitude = latitude, longitude = longitude, timezone = "UTC",
                current = null, hourly = null, daily = null
            )
        }

        override suspend fun airQuality(
            latitude: Double, longitude: Double, current: String, timezone: String
        ): AirQualityResponse = AirQualityResponse(current = null)

        override suspend fun searchCity(
            name: String, count: Int, language: String, format: String
        ): GeocodingResponse = GeocodingResponse(results = emptyList())
    }
}
