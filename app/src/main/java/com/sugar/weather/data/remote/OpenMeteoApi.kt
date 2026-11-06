package com.sugar.weather.data.remote

import com.sugar.weather.data.remote.dto.AirQualityResponse
import com.sugar.weather.data.remote.dto.ForecastResponse
import com.sugar.weather.data.remote.dto.GeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo 三接口(DESIGN.md §1.1,决策 D1)。
 *
 * 同一接口类挂三个 baseUrl(forecast / air-quality / geocoding),由
 * [WeatherApiClient.openMeteo] 通过基础框架按域名各建一个实例,见 di/AppContainer。
 *
 * current/hourly/daily 的参数串为常量,值即 DESIGN.md §1.1 实测命令中的串。
 */
interface OpenMeteoApi {

    companion object {
        /** 实况字段串(实测命令)。 */
        const val CURRENT_PARAMS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code," +
                "wind_speed_10m,wind_direction_10m,pressure_msl"

        /** 逐小时字段串:能见度单位为米(§1.1 实测样本 4980.0)。 */
        const val HOURLY_PARAMS = "temperature_2m,weather_code,visibility"

        /** 逐日字段串;forecast_days=16 即「今天 + 未来 15 天」(『15 天天气预报』数据源,§3)。 */
        const val DAILY_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "uv_index_max,precipitation_probability_max,wind_speed_10m_max"

        /** 空气质量实况字段串。 */
        const val AIR_CURRENT_PARAMS = "pm2_5,us_aqi"

        /** CMA GRAPES 全球数值模型；前 5 天优先使用，不等同国内官方成品预报。 */
        const val CMA_MODEL = "cma_grapes_global"
    }

    /**
     * 实况 + 逐时 + 逐日 + 日出日落。
     * 响应内时间为城市本地时间(timezone=auto),单位固定公制(wind_speed_unit=kmh)。
     */
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("models") models: String? = null
    ): ForecastResponse

    /** PM2.5 / US AQI 实况。失败时由调用方降级(§9:保留旧 pm25,卡片显示「—」)。 */
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = AIR_CURRENT_PARAMS,
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse

    /** 城市搜索;注意短名陷阱(§6.4):「大连」只命中村镇,需引导输入全名。 */
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "zh",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
