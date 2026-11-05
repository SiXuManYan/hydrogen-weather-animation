package com.sugar.weather.data

import com.sugar.weather.data.model.WeatherReport
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 带完整日期的太阳事件，避免跨午夜时把明日日出当成已过去的时刻。
 * @property time 城市本地日期与时刻。
 * @property isSunrise true 表示日出，false 表示日落。
 */
internal data class SunEvent(val time: LocalDateTime, val isSunrise: Boolean)

/** 以城市时区计算真实的今天，旧缓存的观测日期不能充当今天。 */
internal fun WeatherReport.localToday(now: Instant = Instant.now()): LocalDate {
    val zone = runCatching { ZoneId.of(timezone.orEmpty()) }.getOrDefault(ZoneId.systemDefault())
    return now.atZone(zone).toLocalDate()
}

/** 提取逐时窗口内的日出和日落；缺测时的占位值不生成虚假的太阳事件。 */
internal fun WeatherReport.sunEventsInWindow(
    start: LocalDateTime,
    endInclusive: LocalDateTime
): List<SunEvent> = daily.flatMap { day ->
    listOfNotNull(
        day.sunrise.takeUnless { it == LocalTime.MIN }
            ?.let { SunEvent(time = day.date.atTime(it), isSunrise = true) },
        day.sunset.takeUnless { it == LocalTime.MAX }
            ?.let { SunEvent(time = day.date.atTime(it), isSunrise = false) }
    )
}.filter { !it.time.isBefore(start) && !it.time.isAfter(endInclusive) }
    .sortedBy { it.time }

/** 缓存超过 30 分钟或系统时钟回拨时需要刷新。 */
internal fun WeatherReport.isStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val age = nowMillis - fetchedAtMillis
    return age < 0 || age > CACHE_MAX_AGE_MILLIS
}

/** 页面共享同一新鲜度阈值，详情页与首页保持一致。 */
private const val CACHE_MAX_AGE_MILLIS = 30L * 60_000L
