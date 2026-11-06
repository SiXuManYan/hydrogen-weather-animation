package com.sugar.weather.di

import android.content.Context
import com.sugar.weather.BuildConfig
import com.sugar.weather.data.CityStore
import com.sugar.weather.data.LocationDataSource
import com.sugar.weather.data.WeatherRepository
import com.sugar.weather.data.remote.OpenMeteoApi
import com.sugar.weather.data.remote.WeatherApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写依赖注入容器(DESIGN.md §2.3,决策 D6:不用 Hilt,避免 KAPT/KSP)。
 *
 * 全部为进程级单例,由 [com.sugar.weather.WeatherApplication] 持有;
 * ViewModel 通过 `(application as WeatherApplication).container` 取用。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 应用级协程作用域:仓库的 StateFlow 共享与缓存预热使用,生命周期与进程一致。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 同一容器内的服务通过基础框架共享 HTTP 客户端。 */
    private val apiClient = WeatherApiClient(debug = BuildConfig.DEBUG)

    /** DataStore 封装:城市列表与天气缓存。 */
    val cityStore: CityStore = CityStore(appContext)

    /** forecast 主接口(api.open-meteo.com)。 */
    val openMeteoApi: OpenMeteoApi = apiClient.openMeteo(baseUrl = FORECAST_BASE_URL)

    /** air-quality 域名实例(同一接口类,不同 baseUrl,§2.3)。 */
    val airQualityApi: OpenMeteoApi = apiClient.openMeteo(baseUrl = AIR_QUALITY_BASE_URL)

    /** geocoding 域名实例。 */
    val geocodingApi: OpenMeteoApi = apiClient.openMeteo(baseUrl = GEOCODING_BASE_URL)

    /** 天气仓库:UI/ViewModel 唯一的数据出入口。 */
    val weatherRepository: WeatherRepository = WeatherRepository(
        cityStore = cityStore,
        forecastApi = openMeteoApi,
        airQualityApi = airQualityApi,
        geocodingApi = geocodingApi,
        externalScope = appScope
    )

    /** 定位数据源(权限申请在 UI 层)。 */
    val locationDataSource: LocationDataSource = LocationDataSource(appContext)

    companion object {
        /** 三个 Open-Meteo 域名(§1.1 实测;Retrofit 要求 baseUrl 以 `/` 结尾)。 */
        const val FORECAST_BASE_URL = "https://api.open-meteo.com/"
        const val AIR_QUALITY_BASE_URL = "https://air-quality-api.open-meteo.com/"
        const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}
