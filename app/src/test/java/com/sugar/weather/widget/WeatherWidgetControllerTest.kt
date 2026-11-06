package com.sugar.weather.widget

import com.sugar.weather.data.CityStorage
import com.sugar.weather.data.WeatherRepository
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.CurrentWeather
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.OpenMeteoApi
import com.sugar.weather.data.remote.dto.AirQualityResponse
import com.sugar.weather.data.remote.dto.CurrentBlock
import com.sugar.weather.data.remote.dto.DailyBlock
import com.sugar.weather.data.remote.dto.ForecastResponse
import com.sugar.weather.data.remote.dto.GeocodingResponse
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 真实仓库与协程验证后台刷新边界，系统桌面由内存宿主替代。 */
class WeatherWidgetControllerTest {
    /** 无微件时直接退出并停止调度，不因后台任务恢复而访问网络。 */
    @Test fun `no widgets cancels work without fetching weather`() = runBlocking {
        fixture { f ->
            f.host.hasWidgets = false
            assertTrue(f.controller.refresh(force = true))
            assertTrue(f.host.cancelled)
            assertEquals(0, f.api.requests.get())
        }
    }

    /** 数据尚未从磁盘预热到共享流时，刷新仍先取得真实城市和缓存。 */
    @Test fun `cold start with fresh disk cache avoids network`() = runBlocking {
        fixture { f ->
            assertTrue(f.controller.refresh(force = false))
            assertEquals(0, f.api.requests.get())
            assertEquals("first", f.host.frame.value!!.first.cityId)
            assertEquals("8°", f.host.frame.value!!.first.temperature)
        }
    }

    /** 手动刷新绕过新鲜缓存，成功内容会同时写回仓库。 */
    @Test fun `manual refresh bypasses cache and commits new report`() = runBlocking {
        fixture { f ->
            assertTrue(f.controller.refresh(force = true))
            assertEquals(1, f.api.requests.get())
            assertEquals("25°", f.host.frame.value!!.first.temperature)
            assertEquals(25.0, f.repository.cachedReport("first")!!.current!!.temperatureC, 0.01)
            assertEquals(WidgetRefreshStage.IDLE, f.host.frame.value!!.second)
        }
    }

    /** 离线更新失败保留旧温度和旧成功时间，同时给 Worker 明确的失败结果。 */
    @Test fun `failed update preserves cache and successful timestamp`() = runBlocking {
        fixture(stale = true) { f ->
            f.api.fail = true
            assertFalse(f.controller.refresh(force = false))
            val (state, stage) = f.host.frame.value!!
            assertEquals("8°", state.temperature)
            assertEquals(1L, state.fetchedAtMillis)
            assertEquals(WidgetRefreshStage.FAILED, stage)
        }
    }

    /** 用户前台刷新成功后，旧后台失败标记必须自行消失。 */
    @Test fun `foreground success clears previous widget failure`() = runBlocking {
        fixture(stale = true) { f ->
            f.controller.start()
            f.api.fail = true
            assertFalse(f.controller.refresh(force = false))
            f.api.fail = false
            assertTrue(f.repository.refreshWeather("first").isSuccess)
            withTimeout(5_000) {
                f.host.frame.first { it?.first?.temperature == "25°" && it.second == WidgetRefreshStage.IDLE }
            }
        }
    }

    /** 迟到网络结果不能把已经删掉的首城写回微件，新首城在同一次工作内补齐。 */
    @Test fun `city deleted while refreshing loads new first city`() = runBlocking {
        fixture(stale = true, secondCity = true) { f ->
            f.api.release = CompletableDeferred()
            val refresh = async { f.controller.refresh(force = true) }
            withTimeout(5_000) { f.api.started.await() }
            f.repository.removeCity("first")
            f.api.release!!.complete(Unit)
            assertTrue(withTimeout(5_000) { refresh.await() })
            assertEquals(2, f.api.requests.get())
            assertEquals("second", f.host.frame.value!!.first.cityId)
            assertEquals("25°", f.host.frame.value!!.first.temperature)
            assertFalse("first" in f.repository.reports.value)
        }
    }

    /** 空城市列表不触发定位或天气查询，桌面仍有可点击的添加入口。 */
    @Test fun `empty city list renders empty state without network`() = runBlocking {
        fixture { f ->
            f.repository.removeCity("first")
            assertTrue(f.controller.refresh(force = true))
            assertEquals(null, f.host.frame.value!!.first.cityId)
            assertEquals(0, f.api.requests.get())
        }
    }

    /** 每个测试独立进程作用域和内存存储，结束后取消观察器。 */
    private suspend fun fixture(
        stale: Boolean = false,
        secondCity: Boolean = false,
        block: suspend (Fixture) -> Unit
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val city = City(id = "first", name = "首城", latitude = 5.0, longitude = 10.0, addedAtMillis = 1L)
        val cities = if (secondCity) listOf(city, city.copy(id = "second", name = "次城", latitude = 6.0))
            else listOf(city)
        val cached = WeatherReport(
            cityId = city.id, fetchedAtMillis = if (stale) 1L else System.currentTimeMillis(), timezone = "UTC",
            current = CurrentWeather(
                temperatureC = 8.0, apparentTemperatureC = 6.0, humidityPercent = 50.0, pressureHpa = 1010.0,
                windKmh = 10.0, windDirectionDeg = 0.0, visibilityKm = null, weatherCode = 0,
                isNight = false, pm25 = null
            )
        )
        val store = MemoryStorage(initial = cities, cached = cached)
        val api = ControlledApi()
        val repository = WeatherRepository(
            cityStore = store, forecastApi = api, airQualityApi = api, geocodingApi = api, externalScope = scope
        )
        val host = MemoryHost()
        val controller = WeatherWidgetController(host = host, cityStore = store, repository = repository, scope = scope)
        try {
            block(Fixture(controller = controller, repository = repository, host = host, api = api))
        } finally {
            scope.cancel()
        }
    }

    /**
     * 一次测试共享的真实仓库与可控制边界。
     * @property controller 被测后台协调器。
     * @property repository 与后台共用的天气仓库。
     * @property host 最近一次桌面快照。
     * @property api 可挂起或失败的网络实现。
     */
    private data class Fixture(
        val controller: WeatherWidgetController,
        val repository: WeatherRepository,
        val host: MemoryHost,
        val api: ControlledApi
    )

    /** 只记录最终快照，不依赖 Android View 或 WorkManager 的实现细节。 */
    private class MemoryHost : WeatherWidgetHost {
        /** 测试可模拟实例消失。 */
        override var hasWidgets = true
        /** 最近一次渲染和刷新状态，供异步等待。 */
        val frame = MutableStateFlow<Pair<WeatherWidgetState, WidgetRefreshStage>?>(null)
        /** 是否已取消无效后台任务。 */
        var cancelled = false
        /** 发布可断言的完整快照。 */
        override fun show(state: WeatherWidgetState, stage: WidgetRefreshStage) { frame.value = state to stage }
        /** 调度唯一性由设备测试覆盖，本测试不创建系统任务。 */
        override fun ensureScheduled() = Unit
        /** 模拟 KEEP 正在工作时不再入队，验证 Controller 自行追上新首城。 */
        override fun requestRefresh() = Unit
        /** 标记无实例退出。 */
        override fun cancel() { cancelled = true }
    }

    /**
     * 用 Mutex 模拟 DataStore 的读改写与缓存提交。
     * @param initial 初始城市。
     * @param cached 首城磁盘天气。
     */
    private class MemoryStorage(initial: List<City>, cached: WeatherReport) : CityStorage {
        /** 已提交城市快照。 */
        override val cities = MutableStateFlow(initial)
        /** 独立磁盘天气副本。 */
        private val weather = mutableMapOf(cached.cityId to cached)
        /** 城市和天气共用事务锁。 */
        private val mutex = Mutex()
        /** 删除城市时同步清除磁盘天气。 */
        override suspend fun updateCities(transform: (List<City>) -> List<City>): List<City> = mutex.withLock {
            transform(cities.value).also { updated ->
                cities.value = updated
                weather.keys.retainAll(updated.map { it.id }.toSet())
            }
        }
        /** 与真实存储一样独立于仓库内存层。 */
        override suspend fun saveWeather(cityId: String, report: WeatherReport) { mutex.withLock { weather[cityId] = report } }
        /** 冷启动只能通过此入口取得缓存。 */
        override suspend fun loadAllCachedWeather(): Map<String, WeatherReport> = mutex.withLock { weather.toMap() }
    }

    /** 仅替换网络边界，领域转换和持久化仍执行真实仓库代码。 */
    private class ControlledApi : OpenMeteoApi {
        /** 预报调用计数，不把空气质量查询算作重复天气请求。 */
        val requests = AtomicInteger()
        /** 请求已经进入网络边界。 */
        val started = CompletableDeferred<Unit>()
        /** 测试释放请求前保持挂起。 */
        var release: CompletableDeferred<Unit>? = null
        /** 模拟离线，异常由真实仓库包装。 */
        var fail = false
        /** 返回完整实况或等待城市删除后返回，覆盖迟到提交。 */
        override suspend fun forecast(
            latitude: Double, longitude: Double, current: String, hourly: String,
            daily: String, timezone: String, forecastDays: Int, windSpeedUnit: String, models: String?
        ): ForecastResponse {
            requests.incrementAndGet()
            started.complete(Unit)
            release?.await()
            if (fail) throw IOException("offline")
            return ForecastResponse(
                latitude = latitude, longitude = longitude, timezone = "UTC", hourly = null,
                daily = DailyBlock(
                    time = listOf("2026-11-06"), weather_code = listOf(0), temperature_2m_max = listOf(28.0),
                    temperature_2m_min = listOf(18.0), sunrise = null, sunset = null, uv_index_max = null,
                    precipitation_probability_max = null, wind_speed_10m_max = null
                ),
                current = CurrentBlock(
                    time = "2026-11-06T12:00", interval = 900, temperature_2m = 25.0,
                    relative_humidity_2m = 60.0, apparent_temperature = 26.0, is_day = 1, weather_code = 0,
                    wind_speed_10m = 10.0, wind_direction_10m = 0.0, pressure_msl = 1010.0
                )
            )
        }
        /** 空气质量不影响这些测试的成功标准。 */
        override suspend fun airQuality(
            latitude: Double, longitude: Double, current: String, timezone: String
        ): AirQualityResponse = AirQualityResponse(current = null)
        /** 微件不应发起城市搜索。 */
        override suspend fun searchCity(
            name: String, count: Int, language: String, format: String
        ): GeocodingResponse = error("微件不应搜索城市")
    }
}
