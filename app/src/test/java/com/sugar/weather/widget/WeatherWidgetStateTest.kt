package com.sugar.weather.widget

import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.CurrentWeather
import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.WeatherReport
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 微件日期和缓存身份回归，不连接外部网络或依赖设备时区。 */
class WeatherWidgetStateTest {
    /** 未添加城市时保留真实空状态，不能显示示例城市和温度。 */
    @Test fun `no city contains no fabricated weather`() {
        val state = WeatherWidgetState.from(city = null, report = null, now = now)
        assertNull(state.cityId)
        assertEquals("—", state.temperature)
        assertNull(state.fetchedAtMillis)
        assertTrue(state.days.all { it.forecast == null })
    }

    /** 城市排序和报告回流交错时，拒绝显示其他城市的温度。 */
    @Test fun `report belonging to another city is discarded`() {
        val state = WeatherWidgetState.from(city = city, report = report().copy(cityId = "other"), now = now)
        assertEquals("测试城市", state.cityName)
        assertEquals("—", state.temperature)
        assertNull(state.weatherCode)
        assertNull(state.fetchedAtMillis)
    }

    /** 跨午夜按城市当地日期生成今天和未来槽，而不是按缓存观测日。 */
    @Test fun `forecast follows city date across midnight`() {
        val weather = report().copy(
            timezone = "Asia/Shanghai",
            daily = listOf(day("2026-11-06"), day("2026-11-07"), day("2026-11-08"), day("2026-11-09"))
        )
        val state = WeatherWidgetState.from(city = city, report = weather, now = now)
        assertEquals("12°/3°", state.highLow)
        assertEquals(listOf("2026-11-08", "2026-11-09", "2026-11-10"), state.days.map { it.date.toString() })
        assertNull(state.days.last().forecast)
    }

    /** 报告过期到没有今天日报时，高低温为空，未来槽不会挪用旧日期。 */
    @Test fun `expired daily forecast never becomes today`() {
        val state = WeatherWidgetState.from(city = city, report = report().copy(
            daily = listOf(day("2026-11-01"), day("2026-11-02"))
        ), now = now)
        assertEquals("—", state.highLow)
        assertTrue(state.days.all { it.forecast == null })
        assertEquals(123L, state.fetchedAtMillis)
    }

    /** 后天缺测时保留其空位，不把第四天的数据补成后天。 */
    @Test fun `missing date keeps its own forecast slot`() {
        val weather = report().copy(timezone = "UTC", daily = listOf(
            day("2026-11-06"), day("2026-11-07"), day("2026-11-09"), day("2026-11-10")
        ))
        val state = WeatherWidgetState.from(city = city, report = weather, now = now)
        assertEquals("2026-11-07", state.days[0].forecast?.date.toString())
        assertNull(state.days[1].forecast)
        assertEquals("2026-11-09", state.days[2].forecast?.date.toString())
    }

    /** 本机日期已前进时，西半球城市仍使用当地日期筛选。 */
    @Test fun `western time zone retains its local today`() {
        val weather = report().copy(timezone = "America/Los_Angeles", daily = listOf(day("2026-11-06")))
        val state = WeatherWidgetState.from(city = city, report = weather, now = now)
        assertEquals("12°/3°", state.highLow)
        assertEquals(LocalDate.parse("2026-11-07"), state.days.first().date)
    }

    /** 负温和实况昼夜复用领域值，不从日期或设备小时猜测。 */
    @Test fun `temperature night and timestamp preserve report values`() {
        val state = WeatherWidgetState.from(city = city, report = report(), now = now)
        assertEquals("-6°", state.temperature)
        assertEquals("晴", state.condition)
        assertTrue(state.isNight)
        assertEquals(123L, state.fetchedAtMillis)
    }

    /** 无实况但有日报时不伪造当前温度，仍允许查看有效预报。 */
    @Test fun `missing current does not suppress valid daily forecast`() {
        val weather = report().copy(current = null, timezone = "UTC", daily = listOf(day("2026-11-07")))
        val state = WeatherWidgetState.from(city = city, report = weather, now = now)
        assertEquals("—", state.temperature)
        assertNull(state.weatherCode)
        assertEquals("2026-11-07", state.days.first().forecast?.date.toString())
    }

    /** 损坏时区按系统回退，不能导致后台任务崩溃。 */
    @Test fun `invalid time zone does not crash widget`() {
        assertEquals(3, WeatherWidgetState.from(city = city, report = report().copy(timezone = "invalid"), now = now).days.size)
    }

    /** 固定城市无定位权限需求。 */
    private val city = City(id = "city", name = "测试城市", latitude = 5.0, longitude = 10.0)

    /** UTC 与东八区恰好跨日，覆盖日期偏移。 */
    private val now = Instant.parse("2026-11-06T18:00:00Z")

    /** 最小完整报告，时间戳保持不变以验证离线展示。 */
    private fun report(): WeatherReport = WeatherReport(
        cityId = city.id, fetchedAtMillis = 123L, timezone = "UTC",
        current = CurrentWeather(
            temperatureC = -5.6, apparentTemperatureC = -8.0, humidityPercent = 50.0,
            pressureHpa = 1010.0, windKmh = 10.0, windDirectionDeg = 0.0,
            visibilityKm = null, weatherCode = 0, isNight = true, pm25 = null
        )
    )

    /** 构造指定日期的日报，避免测试与 Mapper 细节耦合。 */
    private fun day(date: String): DailyForecast = DailyForecast(
        date = LocalDate.parse(date), weatherCode = 2, tempMaxC = 12.0, tempMinC = 3.0,
        precipProbabilityMaxPercent = null, uvIndexMax = null,
        sunrise = LocalTime.of(6, 0), sunset = LocalTime.of(18, 0)
    )
}
