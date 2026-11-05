package com.sugar.weather.data

import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.WeatherReport
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖跨午夜太阳事件、城市时区和共享缓存过期边界。 */
class ForecastTimelineTest {

    /** 使用固定日期，避免测试依赖执行机器的时区或日期。 */
    private val date = LocalDate.of(2026, 11, 6)

    /** 下午打开页面时，24 小时窗口仍应包含明天的日出。 */
    @Test
    fun `evening window includes sunset today and sunrise tomorrow`() {
        val report = report(days = listOf(day(date), day(date.plusDays(1))))
        val events = report.sunEventsInWindow(
            start = date.atTime(15, 0),
            endInclusive = date.plusDays(1).atTime(14, 0)
        )
        assertEquals(
            listOf(
                SunEvent(time = date.atTime(17, 30), isSunrise = false),
                SunEvent(time = date.plusDays(1).atTime(6, 30), isSunrise = true)
            ),
            events
        )
    }

    /** 窗口端点的太阳事件也应保留。 */
    @Test
    fun `events exactly at both window boundaries are included`() {
        val report = report(days = listOf(day(date)))
        val events = report.sunEventsInWindow(
            start = date.atTime(6, 30),
            endInclusive = date.atTime(17, 30)
        )
        assertEquals(2, events.size)
    }

    /** 缺测占位不能显示成午夜日出和深夜日落。 */
    @Test
    fun `missing solar times do not produce fake events`() {
        val report = report(
            days = listOf(day(date).copy(sunrise = LocalTime.MIN, sunset = LocalTime.MAX))
        )
        assertTrue(
            report.sunEventsInWindow(
                start = date.atStartOfDay(),
                endInclusive = date.atTime(LocalTime.MAX)
            ).isEmpty()
        )
    }

    /** 日期标签应随城市本地时间跨日，与旧报告的观测日期无关。 */
    @Test
    fun `today uses the current city date rather than cached observation`() {
        val report = report().copy(
            timezone = "Asia/Shanghai",
            observedAt = date.minusDays(1).atTime(12, 0)
        )
        val now = Instant.parse("2026-11-06T17:00:00Z")
        assertEquals(date.plusDays(1), report.localToday(now = now))
        assertEquals(date, report.copy(timezone = "America/Los_Angeles").localToday(now = now))
    }

    /** 30 分钟内不重复刷新，超过阈值立即过期。 */
    @Test
    fun `cache freshness has a thirty minute boundary`() {
        val report = report().copy(fetchedAtMillis = 1_000L)
        assertFalse(report.isStale(nowMillis = 1_000L))
        assertFalse(report.isStale(nowMillis = 1_000L + 30 * 60_000L))
        assertTrue(report.isStale(nowMillis = 1_001L + 30 * 60_000L))
    }

    /** 系统时钟回拨后不能把未来时间戳缓存永久当成新鲜数据。 */
    @Test
    fun `clock rollback invalidates a future dated cache`() {
        assertTrue(report().copy(fetchedAtMillis = 5_000L).isStale(nowMillis = 4_000L))
    }

    /** 最小报告夹具只设置本测试需要的时间线字段。 */
    private fun report(days: List<DailyForecast> = emptyList()): WeatherReport = WeatherReport(
        cityId = "test-city",
        fetchedAtMillis = 0L,
        daily = days
    )

    /** 构造具有固定日出日落的单日预报。 */
    private fun day(date: LocalDate): DailyForecast = DailyForecast(
        date = date,
        weatherCode = 0,
        tempMaxC = 16.0,
        tempMinC = 3.0,
        precipProbabilityMaxPercent = null,
        uvIndexMax = null,
        sunrise = LocalTime.of(6, 30),
        sunset = LocalTime.of(17, 30)
    )
}
