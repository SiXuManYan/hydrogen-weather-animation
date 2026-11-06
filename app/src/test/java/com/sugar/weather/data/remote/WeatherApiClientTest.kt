package com.sugar.weather.data.remote

import com.sugar.weather.data.WeatherDataException
import com.template.framework.api.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException

/** 在本机 HTTP 服务上验证框架迁移后的接口契约，不连接天气服务或 Android 设备。 */
class WeatherApiClientTest {

    /** 每个测试独立持有服务，避免响应队列和端口相互干扰。 */
    private val servers = mutableListOf<MockWebServer>()

    /** 测试结束时回收连接池和线程，取消测试不会遗留正在等待的请求。 */
    private val clients = mutableListOf<WeatherApiClient>()

    /** 先取消请求，再关闭本地服务和连接池。 */
    @After
    fun closeResources() {
        clients.forEach { it.httpClient.dispatcher.cancelAll() }
        servers.forEach { it.shutdown() }
        clients.forEach {
            it.httpClient.connectionPool.evictAll()
            it.httpClient.dispatcher.executorService.shutdownNow()
        }
    }

    /** 迁移保留原超时，正式构建不安装业务或日志拦截器。 */
    @Test
    fun releaseClientPreservesTimeoutsWithoutBusinessInterceptors() {
        val http = client().httpClient
        assertEquals(10_000, http.connectTimeoutMillis)
        assertEquals(15_000, http.readTimeoutMillis)
        assertEquals(10_000, http.writeTimeoutMillis)
        assertTrue(http.interceptors.isEmpty())
        assertTrue(http.networkInterceptors.isEmpty())
    }

    /** Debug 使用复制库的摘要日志，不引入 Token 或版本 Header 处理。 */
    @Test
    fun debugClientUsesOnlyFrameworkSummaryLogging() {
        val interceptors = client(debug = true).httpClient.interceptors
        assertEquals(1, interceptors.size)
        assertTrue(interceptors.single() is HttpLoggingInterceptor)
    }

    /** 三个地址分别请求并解析原生 Open-Meteo JSON，接口不套用框架的示例响应外壳。 */
    @Test
    fun forecastAirQualityAndSearchKeepTheirOwnEndpointsAndSchemas() = runBlocking {
        val factory = client()
        val forecast = server("""{"timezone":"Asia/Shanghai","current":{"temperature_2m":18.5,"weather_code":3}}""")
        val air = server("""{"current":{"pm2_5":12.4,"us_aqi":51.0}}""")
        val search = server("""{"results":[{"id":1816670,"name":"北京市","latitude":39.9,"longitude":116.4}]}""")

        // 不带结尾斜杠的地址也由 Library 正确规范化。
        val forecastApi = factory.openMeteo(baseUrl = forecast.url("/").toString().trimEnd('/'))
        val airApi = factory.openMeteo(baseUrl = air.url("/").toString())
        val searchApi = factory.openMeteo(baseUrl = search.url("/").toString())
        assertEquals(18.5, forecastApi.forecast(latitude = 39.9, longitude = 116.4).current!!.temperature_2m!!, 0.0)
        assertEquals(12.4, airApi.airQuality(latitude = 39.9, longitude = 116.4).current!!.pm2_5!!, 0.0)
        assertEquals("北京市", searchApi.searchCity(name = "北京市").results!!.single().name)

        val forecastRequest = request(forecast)
        assertEquals("/v1/forecast", forecastRequest.requestUrl!!.encodedPath)
        assertEquals("16", forecastRequest.requestUrl!!.queryParameter("forecast_days"))
        assertEquals("auto", forecastRequest.requestUrl!!.queryParameter("timezone"))
        assertEquals("kmh", forecastRequest.requestUrl!!.queryParameter("wind_speed_unit"))
        assertEquals(OpenMeteoApi.CURRENT_PARAMS, forecastRequest.requestUrl!!.queryParameter("current"))
        assertEquals(OpenMeteoApi.HOURLY_PARAMS, forecastRequest.requestUrl!!.queryParameter("hourly"))
        assertEquals(OpenMeteoApi.DAILY_PARAMS, forecastRequest.requestUrl!!.queryParameter("daily"))
        assertNull(forecastRequest.requestUrl!!.queryParameter("models"))
        val airRequest = request(air)
        assertEquals("/v1/air-quality", airRequest.requestUrl!!.encodedPath)
        assertEquals(OpenMeteoApi.AIR_CURRENT_PARAMS, airRequest.requestUrl!!.queryParameter("current"))
        val searchRequest = request(search)
        assertEquals("/v1/search", searchRequest.requestUrl!!.encodedPath)
        assertEquals("北京市", searchRequest.requestUrl!!.queryParameter("name"))
        assertEquals("zh", searchRequest.requestUrl!!.queryParameter("language"))
        for (recorded in listOf(forecastRequest, airRequest, searchRequest)) {
            assertEquals("GET", recorded.method)
            assertNull(recorded.getHeader("Authorization"))
            assertNull(recorded.getHeader("clientid"))
            assertNull(recorded.getHeader("VersionCode"))
            assertNull(recorded.getHeader("VersionName"))
        }
    }

    /** 服务端限流仍沿 Retrofit 异常交给既有业务错误映射。 */
    @Test
    fun httpFailuresKeepStatusCodeForRepositoryMapping() {
        val server = server(responseCode = 429)
        val api = client().openMeteo(baseUrl = server.url("/").toString())
        val error = assertThrows(HttpException::class.java) {
            runBlocking { api.searchCity(name = "北京市") }
        }
        val mapped = WeatherDataException.wrap(error) as WeatherDataException.Http
        assertEquals(429, mapped.code)
    }

    /** 不兼容的 JSON 字段仍映射为解析失败，不伪装为成功空列表。 */
    @Test
    fun invalidResponseKeepsParseFailureSemantics() {
        val server = server("""{"results":"invalid"}""")
        val api = client().openMeteo(baseUrl = server.url("/").toString())
        val error = assertThrows(com.google.gson.JsonSyntaxException::class.java) {
            runBlocking { api.searchCity(name = "北京市") }
        }
        assertTrue(WeatherDataException.wrap(error) is WeatherDataException.Parse)
    }

    /** Open-Meteo 无结果时省略 results，迁移后仍允许该合法响应。 */
    @Test
    fun omittedSearchResultsRemainNullable() = runBlocking {
        val server = server("{}")
        val api = client().openMeteo(baseUrl = server.url("/").toString())
        assertNull(api.searchCity(name = "unknown").results)
    }

    /** 页面取消可以中断等待中的 suspend 请求，不被基础框架包装为业务错误。 */
    @Test
    fun cancellationPropagatesWhileWaitingForResponse() = runBlocking {
        val server = MockWebServer().also {
            servers += it
            it.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            it.start()
        }
        val api = client().openMeteo(baseUrl = server.url("/").toString())
        val pending = async(start = CoroutineStart.UNDISPATCHED) { api.searchCity(name = "北京市") }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        pending.cancel()
        try {
            pending.await()
            throw AssertionError("请求取消后不应正常返回")
        } catch (expected: CancellationException) {
            assertTrue(pending.isCancelled)
            assertFalse(pending.isActive)
        }
    }

    /** 构造隔离客户端，Debug 配置只作用于本测试实例。 */
    private fun client(debug: Boolean = false): WeatherApiClient =
        WeatherApiClient(debug = debug).also { clients += it }

    /** 本地服务只提供本测试指定的 JSON 与 HTTP 状态码。 */
    private fun server(body: String = "{}", responseCode: Int = 200): MockWebServer =
        MockWebServer().also {
            servers += it
            it.enqueue(MockResponse().setResponseCode(responseCode).setHeader("Content-Type", "application/json").setBody(body))
            it.start()
        }

    /** 限时取得请求，端点错误时快速报告，避免测试永久等待。 */
    private fun request(server: MockWebServer): RecordedRequest =
        requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "未收到预期 HTTP 请求" }
}
