package com.sugar.weather.data

import com.sugar.weather.data.model.CitySearchResult
import com.sugar.weather.data.model.CurrentWeather
import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.HourForecast
import com.sugar.weather.data.model.WeatherReport
import com.sugar.weather.data.remote.dto.ForecastResponse
import com.sugar.weather.data.remote.dto.GeocodingResponse
import com.sugar.weather.data.remote.dto.HourlyBlock
import com.sugar.weather.data.remote.dto.DailyBlock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Open-Meteo DTO → 领域模型(DESIGN.md §10.2)。
 *
 * 纯 Kotlin 函数;对缺失字段一律显式兜底,不抛解析异常——结构级损坏由
 * Repository 的统一包装(WeatherDataException.Parse)兜住,这里是字段级兜底。
 */

/**
 * forecast 响应 → [WeatherReport]。
 *
 * @param cityId 归属城市
 * @param fetchedAtMillis 抓取完成时间(展示「刚刚更新 / X 分钟前更新」)
 * @param pm25 air-quality 接口的 PM2.5;该接口失败时调用方传旧值或 null(§9)
 */
fun ForecastResponse.toDomain(cityId: String, fetchedAtMillis: Long, pm25: Double?): WeatherReport {
    val observedAt = current?.time?.let { parseLocalDateTime(it) }
    val hourlyForecasts = mapHourly(hourly)
    val currentWeather = current?.let { block ->
        // 气温与 weather_code 是实况块的硬前提,缺失则整块视为不可用
        val temperature = block.temperature_2m ?: return@let null
        val code = block.weather_code ?: return@let null
        CurrentWeather(
            temperatureC = temperature,
            apparentTemperatureC = block.apparent_temperature ?: temperature,
            humidityPercent = block.relative_humidity_2m ?: 0.0,
            pressureHpa = block.pressure_msl ?: 0.0,
            windKmh = block.wind_speed_10m ?: 0.0,
            windDirectionDeg = block.wind_direction_10m ?: 0.0,
            visibilityKm = currentVisibilityKm(hourly, observedAt),
            weatherCode = code,
            isNight = (block.is_day ?: 1) == 0,
            pm25 = pm25
        )
    }
    return WeatherReport(
        cityId = cityId,
        fetchedAtMillis = fetchedAtMillis,
        timezone = timezone,
        observedAt = observedAt,
        current = currentWeather,
        daily = mapDaily(daily, hourlyForecasts),
        hourly = hourlyForecasts
    )
}

/**
 * 城市搜索结果排序(§6.4 短名陷阱对策):
 * population 非空且大的排前(实测 大连市 population=7450785,同名村镇为 null),
 * 同 population 按 name 长度升序;无效条目(缺 name/坐标)丢弃。
 */
fun GeocodingResponse.sortedResults(): List<CitySearchResult> {
    val places = results.orEmpty().mapNotNull { place ->
        val name = place.name ?: return@mapNotNull null
        val lat = place.latitude ?: return@mapNotNull null
        val lon = place.longitude ?: return@mapNotNull null
        CitySearchResult(
            name = name,
            admin1 = place.admin1,
            country = place.country,
            lat = lat,
            lon = lon,
            population = place.population
        )
    }
    return places.sortedWith(
        compareByDescending<CitySearchResult> { it.population != null }
            .thenByDescending { it.population ?: 0L }
            .thenBy { it.name.length }
            .thenBy { it.name }
    )
}

/**
 * 逐小时切片(§6.2 第 5 条):起点 = 实况整点的**下一小时**(实测 current.time=01:30 → 首列 01:00 之后),
 * 取 [hours] 列。能见度、日出日落节点由 UI 层基于该切片 + daily[0].sunrise/sunset 生成。
 */
fun WeatherReport.hourlyWindow(hours: Int = 24): List<HourForecast> {
    val start = observedAt?.truncatedTo(ChronoUnit.HOURS)?.plusHours(1)
        ?: hourly.firstOrNull()?.time
        ?: return emptyList()
    return hourly.filter { !it.time.isBefore(start) }.take(hours)
}

/** 旧缓存用已有小时数据补齐全天摘要，离线升级也能保持两页一致，无需额外请求。 */
internal fun WeatherReport.restoreDailyConditions(): WeatherReport {
    if (hourly.isEmpty() || daily.none { it.summaryText == null }) return this
    return copy(daily = daily.map { day ->
        if (day.summaryText != null) return@map day
        val condition = deriveDayCondition(hourly, day.date, day.weatherCode)
        day.copy(
            weatherCode = condition.weatherCode,
            summaryText = condition.summaryText,
            conditionText = condition.conditionText
        )
    })
}

// ———————————————————————— 内部实现 ————————————————————————

/** 当前小时的能见度(米→km,§5.4):按 current.time 截断到整点,在 hourly.time 中定位下标。 */
private fun currentVisibilityKm(hourly: HourlyBlock?, observedAt: LocalDateTime?): Double? {
    if (hourly == null || observedAt == null) return null
    val times = hourly.time ?: return null
    val targetHour = observedAt.truncatedTo(ChronoUnit.HOURS)
    val index = times.indexOfFirst { parseLocalDateTime(it) == targetHour }
    if (index < 0) return null
    val meters = hourly.visibility?.getOrNull(index) ?: return null
    return meters / 1000.0
}

private fun mapHourly(block: HourlyBlock?): List<HourForecast> {
    if (block == null) return emptyList()
    val times = block.time ?: return emptyList()
    val temps = block.temperature_2m
    val codes = block.weather_code
    val result = ArrayList<HourForecast>(times.size)
    for (i in times.indices) {
        val time = times[i].let(::parseLocalDateTime) ?: continue
        val temp = temps?.getOrNull(i) ?: continue
        val code = codes?.getOrNull(i) ?: continue
        result += HourForecast(time = time, temperatureC = temp, weatherCode = code)
    }
    return result
}

private fun mapDaily(block: DailyBlock?, hourly: List<HourForecast>): List<DailyForecast> {
    if (block == null) return emptyList()
    val dates = block.time ?: return emptyList()
    val codes = block.weather_code
    val maxes = block.temperature_2m_max
    val mins = block.temperature_2m_min
    val sunrises = block.sunrise
    val sunsets = block.sunset
    val result = ArrayList<DailyForecast>(dates.size)
    for (i in dates.indices) {
        val date = dates[i].let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
        val rawCode = codes?.getOrNull(i) ?: continue
        val max = maxes?.getOrNull(i) ?: continue
        val min = mins?.getOrNull(i) ?: continue
        // 按城市本地全天汇总，保留夜间降水和雨雪转换；同类强度不机械取最恶劣值。
        val condition = deriveDayCondition(hourly, date, rawCode)
        result += DailyForecast(
            date = date,
            weatherCode = condition.weatherCode,
            tempMaxC = max,
            tempMinC = min,
            precipProbabilityMaxPercent = block.precipitation_probability_max?.getOrNull(i),
            uvIndexMax = block.uv_index_max?.getOrNull(i),
            sunrise = sunrises?.getOrNull(i)?.let(::parseLocalDateTime)?.toLocalTime()
                ?: LocalTime.MIN,
            sunset = sunsets?.getOrNull(i)?.let(::parseLocalDateTime)?.toLocalTime()
                ?: LocalTime.MAX,
            conditionText = condition.conditionText,
            summaryText = condition.summaryText
        )
    }
    return result
}

/** ISO 字符串(2026-11-05T01:30 / T00:00)→ LocalDateTime,失败返回 null。 */
private fun parseLocalDateTime(raw: String?): LocalDateTime? =
    raw?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
