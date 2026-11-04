package com.sugar.weather.data.remote.dto

/**
 * Open-Meteo `/v1/forecast` 响应(DESIGN.md §1.1 实测结构,2026-11-05 会话 curl 复验)。
 *
 * 字段名 snake_case 原样(Gson 反射按字段名直映,DESIGN.md §10.2)。
 * 所有字段可空:Gson 反射实例化不经过构造函数,缺键时字段为 null 而非 Kotlin 默认值,
 * 因此一律声明可空,由 WeatherMapper 决定兜底策略。
 */
data class ForecastResponse(
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val current: CurrentBlock?,
    val hourly: HourlyBlock?,
    val daily: DailyBlock?
)

/** 实况块,10 字段与实测一致。 */
data class CurrentBlock(
    val time: String?,
    val interval: Int?,
    val temperature_2m: Double?,
    val relative_humidity_2m: Double?,
    val apparent_temperature: Double?,
    val is_day: Int?,
    val weather_code: Int?,
    val wind_speed_10m: Double?,
    val wind_direction_10m: Double?,
    val pressure_msl: Double?
)

/**
 * 逐小时块(384 点)。列表元素也可能为 null(API 对个别格点返回 null),
 * Mapper 逐元素判空,不依赖 Kotlin 非空类型。
 */
data class HourlyBlock(
    val time: List<String>?,
    val temperature_2m: List<Double>?,
    val weather_code: List<Int>?,
    val visibility: List<Double>?
)

/** 逐日块(16 天 = 今天 + 未来 15 天)。 */
data class DailyBlock(
    val time: List<String>?,
    val weather_code: List<Int>?,
    val temperature_2m_max: List<Double>?,
    val temperature_2m_min: List<Double>?,
    val sunrise: List<String>?,
    val sunset: List<String>?,
    val uv_index_max: List<Double>?,
    val precipitation_probability_max: List<Int>?,
    val wind_speed_10m_max: List<Double>?
)
