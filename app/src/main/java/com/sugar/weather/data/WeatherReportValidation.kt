package com.sugar.weather.data

import com.sugar.weather.data.model.CurrentWeather
import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.HourForecast
import com.sugar.weather.data.model.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 将网络与磁盘报告归一化到领域约束；无法确认城市归属或没有可用天气时丢弃。
 *
 * Gson 可能绕过 Kotlin 构造函数，把非空列表、列表元素和日期读成 null，
 * 因此在缓存边界主动按可空值读取，不能依赖模型上的非空声明。
 */
internal fun WeatherReport.normalizedFor(cityId: String): WeatherReport? {
    if (this.cityId != cityId || cityId.isBlank() || fetchedAtMillis <= 0L) return null
    val cachedDaily: List<DailyForecast?>? = daily
    val cachedHourly: List<HourForecast?>? = hourly
    val validDaily = cachedDaily.orEmpty().mapNotNull { it?.normalized() }
        .distinctBy { it.date }
        .sortedBy { it.date }
    val validHourly = cachedHourly.orEmpty().mapNotNull { it?.normalized() }
        .distinctBy { it.time }
        .sortedBy { it.time }
    val validCurrent = current?.normalized()
    if (validCurrent == null && validDaily.isEmpty() && validHourly.isEmpty()) return null
    return copy(
        timezone = timezone?.takeIf { runCatching { ZoneId.of(it) }.isSuccess },
        observedAt = observedAt.takeIf { validCurrent != null },
        current = validCurrent,
        daily = validDaily,
        hourly = validHourly
    )
}

/** 新抓取的报告必须同时提供有时间的实况与可用日报，不能用缺测响应覆盖离线缓存。 */
internal fun WeatherReport.isUsableForecast(): Boolean =
    current != null && observedAt != null && daily.isNotEmpty()

/** 气温是实况的必需字段；其他缺测或非有限指标保留为未知值。 */
private fun CurrentWeather.normalized(): CurrentWeather? {
    if (!temperatureC.isFinite() || weatherCode !in 0..99) return null
    return copy(
        apparentTemperatureC = apparentTemperatureC?.takeIf { it.isFinite() },
        humidityPercent = humidityPercent?.takeIf { it.isFinite() && it in 0.0..100.0 },
        pressureHpa = pressureHpa?.takeIf { it.isFinite() && it > 0.0 },
        windKmh = windKmh?.takeIf { it.isFinite() && it >= 0.0 },
        windDirectionDeg = windDirectionDeg?.takeIf { it.isFinite() && it in 0.0..360.0 },
        visibilityKm = visibilityKm?.takeIf { it.isFinite() && it >= 0.0 },
        pm25 = pm25?.takeIf { it.isFinite() && it >= 0.0 }
    )
}

/** 丢弃日期或温度损坏的日报，太阳事件缺失时使用已有的缺测占位约定。 */
private fun DailyForecast.normalized(): DailyForecast? {
    val cachedDate: LocalDate? = date
    val cachedSunrise: LocalTime? = sunrise
    val cachedSunset: LocalTime? = sunset
    if (cachedDate == null || !tempMaxC.isFinite() || !tempMinC.isFinite() ||
        tempMaxC < tempMinC || weatherCode !in 0..99
    ) return null
    return copy(
        date = cachedDate,
        sunrise = cachedSunrise ?: LocalTime.MIN,
        sunset = cachedSunset ?: LocalTime.MAX,
        precipProbabilityMaxPercent = precipProbabilityMaxPercent?.takeIf { it in 0..100 },
        uvIndexMax = uvIndexMax?.takeIf { it.isFinite() && it >= 0.0 }
    )
}

/** 无法定位到具体小时或温度损坏的缓存点不参与切片与全天摘要。 */
private fun HourForecast.normalized(): HourForecast? {
    val cachedTime: LocalDateTime? = time
    return takeIf { cachedTime != null && temperatureC.isFinite() && weatherCode in 0..99 }
}
