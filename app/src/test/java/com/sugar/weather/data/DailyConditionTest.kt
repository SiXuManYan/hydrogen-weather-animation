package com.sugar.weather.data

import com.sugar.weather.data.model.DailyForecast
import com.sugar.weather.data.model.HourForecast
import com.sugar.weather.data.model.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全天摘要的业务回归：夜间降水、雨雪转换、短时危险天气及旧缓存兼容。 */
class DailyConditionTest {

    private val date: LocalDate = LocalDate.of(2026, 11, 8)

    @Test
    fun `rain clearing in the afternoon keeps precipitation in the icon and summary`() {
        val condition = deriveDayCondition(
            daytimeCodes(53, 51, 51, 51, 2, 1, 0, 0, 0, 0, 0, 0), date, 61
        )
        assertEquals(51, condition.weatherCode)
        assertEquals("毛毛雨转晴", condition.summaryText)
        assertEquals("白天毛毛雨转晴", condition.conditionText)
    }

    @Test
    fun `rain turning to snow retains a brief heavier interval only in the detail`() {
        val condition = deriveDayCondition(
            daytimeCodes(55, 55, 71, 73, 73, 71, 71, 73, 75, 75, 73, 71), date, 75
        )
        assertEquals(73, condition.weatherCode)
        assertEquals("毛毛雨转中雪", condition.summaryText)
        assertEquals("白天毛毛雨转中雪（短时大雪）", condition.conditionText)
    }

    @Test
    fun `continuous snow keeps a single condition`() {
        val condition = deriveDayCondition(dailyCodes(*IntArray(24) { 75 }), date, 75)
        assertEquals(75, condition.weatherCode)
        assertEquals("大雪", condition.summaryText)
        assertEquals("大雪", condition.conditionText)
    }

    @Test
    fun `clear day reports sunny`() {
        val condition = deriveDayCondition(dailyCodes(*IntArray(24) { 0 }), date, 3)
        assertEquals(0, condition.weatherCode)
        assertEquals("晴", condition.summaryText)
        assertEquals("晴", condition.conditionText)
    }

    @Test
    fun `two stable sky conditions preserve the transition on the homepage`() {
        val condition = deriveDayCondition(
            dailyCodes(*IntArray(24) { if (it < 12) 0 else 3 }), date, 3
        )
        assertEquals("晴转阴", condition.summaryText)
        assertEquals("凌晨晴；白天晴转阴；夜间阴", condition.conditionText)
    }

    /** 阴天后夜间下雨，两页不能只留下白天的“阴”。 */
    @Test
    fun `nighttime rain remains in the daily summary`() {
        val condition = deriveDayCondition(
            dailyCodes(*IntArray(24) { if (it < 20) 3 else 61 }), date, 61
        )
        assertEquals(61, condition.weatherCode)
        assertEquals("阴转小雨", condition.summaryText)
        assertEquals("凌晨阴；白天阴；夜间小雨", condition.conditionText)
    }

    @Test
    fun `overnight rain changing to daytime snow preserves both phases`() {
        val condition = deriveDayCondition(
            dailyCodes(*IntArray(24) { if (it < 8) 61 else 71 }), date, 75
        )
        assertEquals(71, condition.weatherCode)
        assertEquals("小雨转小雪", condition.summaryText)
        assertEquals("凌晨小雨；白天小雪；夜间小雪", condition.conditionText)
        assertFalse(condition.summaryText.orEmpty().contains("雨夹雪"))
    }

    @Test
    fun `rain between clear intervals is retained without a long cloud sequence`() {
        val condition = deriveDayCondition(
            daytimeCodes(0, 0, 0, 0, 61, 61, 61, 61, 0, 0, 0, 0), date, 61
        )
        assertEquals("晴有小雨", condition.summaryText)
        assertEquals("白天晴有小雨", condition.conditionText)
    }

    @Test
    fun `rain stopping and returning is described as intermittent`() {
        val condition = deriveDayCondition(
            daytimeCodes(61, 61, 61, 61, 0, 0, 0, 0, 61, 61, 61, 61), date, 61
        )
        assertEquals("间歇小雨", condition.summaryText)
        assertEquals("白天间歇小雨", condition.conditionText)
    }

    @Test
    fun `short cloud interruptions do not make the weather text longer`() {
        val condition = deriveDayCondition(
            daytimeCodes(0, 0, 2, 0, 0, 61, 61, 61, 61, 61, 61, 61), date, 61
        )
        assertEquals("晴转小雨", condition.summaryText)
        assertEquals("白天晴转小雨", condition.conditionText)
    }

    /** 晴与少云属于同类，不能拆票后被持续更短的阴天取代。 */
    @Test
    fun `clear and mostly clear hours count together`() {
        val condition = deriveDayCondition(
            daytimeCodes(0, 0, 0, 1, 1, 1, 3, 3, 3, 3, 2, 2), date, 3
        )
        assertEquals(1, condition.weatherCode)
        assertEquals("晴", condition.summaryText)
    }

    @Test
    fun `a single hour of rain is kept as a brief event`() {
        val codes = IntArray(24) { 0 }.also { it[15] = 61 }
        val condition = deriveDayCondition(dailyCodes(*codes), date, 61)
        assertEquals("短时小雨", condition.summaryText)
        assertTrue(condition.conditionText.orEmpty().contains("白天短时小雨"))
    }

    @Test
    fun `one hour of thunder is never filtered as a short interruption`() {
        val codes = IntArray(24) { 0 }.also { it[15] = 95 }
        val condition = deriveDayCondition(dailyCodes(*codes), date, 95)
        assertEquals(95, condition.weatherCode)
        assertEquals("短时雷阵雨", condition.summaryText)
        assertTrue(condition.conditionText.orEmpty().contains("白天短时雷阵雨"))
    }

    @Test
    fun `brief freezing rain is not merged into ordinary rain`() {
        val codes = IntArray(24) { 61 }.also { it[13] = 66 }
        val condition = deriveDayCondition(dailyCodes(*codes), date, 66)
        assertEquals(66, condition.weatherCode)
        assertEquals("短时冻雨", condition.summaryText)
        assertTrue(condition.conditionText.orEmpty().contains("小雨转冻雨转小雨"))
    }

    @Test
    fun `hail in a long rainy day remains in the summary`() {
        val codes = IntArray(24) { 61 }.also { it[13] = 96 }
        val condition = deriveDayCondition(dailyCodes(*codes), date, 96)
        assertEquals(96, condition.weatherCode)
        assertEquals("短时雷雨伴冰雹", condition.summaryText)
        assertTrue(condition.conditionText.orEmpty().contains("雷雨伴冰雹"))
    }

    @Test
    fun `frequent rain and snow changes are not called sleet`() {
        val condition = deriveDayCondition(
            daytimeCodes(61, 61, 71, 71, 61, 61, 71, 71, 61, 61, 71, 71), date, 71
        )
        assertEquals("雨雪交替", condition.summaryText)
        assertEquals("白天雨雪交替", condition.conditionText)
    }

    @Test
    fun `compressed rain snow changes still retain a brief heavy snow interval`() {
        val codes = IntArray(12) { if (it % 2 == 0) 61 else 71 }.also { it[11] = 75 }
        val condition = deriveDayCondition(daytimeCodes(*codes), date, 75)
        assertEquals("雨雪交替", condition.summaryText)
        assertEquals("白天雨雪交替（短时大雪）", condition.conditionText)
    }

    @Test
    fun `weather from the next natural day is excluded`() {
        val hours = dailyCodes(*IntArray(24) { 0 }) + hour(0, 75).copy(time = date.plusDays(1).atStartOfDay())
        val condition = deriveDayCondition(hours, date, 75)
        assertEquals("晴", condition.summaryText)
        assertEquals("晴", condition.conditionText)
    }

    /** 缺失 11 点的样本不证明雨停过；重复时间也不能增加权重。 */
    @Test
    fun `unordered duplicate hours and data gaps do not invent an interruption`() {
        val condition = deriveDayCondition(
            listOf(hour(12, 61), hour(10, 61), hour(10, 61)), date, 61
        )
        assertEquals("小雨", condition.summaryText)
        assertEquals("白天小雨", condition.conditionText)
    }

    @Test
    fun `only nighttime samples are still used and labelled`() {
        val condition = deriveDayCondition(listOf(hour(0, 61), hour(1, 61)), date, 75)
        assertEquals(61, condition.weatherCode)
        assertEquals("小雨", condition.summaryText)
        assertEquals("凌晨小雨", condition.conditionText)
    }

    @Test
    fun `missing hourly data falls back to the API daily code`() {
        val condition = deriveDayCondition(emptyList(), date, 75)
        assertEquals(75, condition.weatherCode)
        assertNull(condition.summaryText)
        assertNull(condition.conditionText)
    }

    @Test
    fun `legacy cached daily text is rebuilt without changing temperatures`() {
        val old = report(dailyCodes(*IntArray(24) { if (it < 20) 3 else 61 }))
        val restored = old.restoreDailyConditions()
        assertEquals("阴转小雨", restored.daily.single().summaryText)
        assertEquals(61, restored.daily.single().weatherCode)
        assertEquals(old.daily.single().tempMaxC, restored.daily.single().tempMaxC, 0.0)
        assertEquals(old.daily.single().tempMinC, restored.daily.single().tempMinC, 0.0)
        assertEquals(old.fetchedAtMillis, restored.fetchedAtMillis)
    }

    @Test
    fun `cache with no hourly data is retained for offline viewing`() {
        val old = report(emptyList())
        assertSame(old, old.restoreDailyConditions())
    }

    private fun daytimeCodes(vararg codes: Int): List<HourForecast> =
        codes.mapIndexed { index, code -> hour(8 + index, code) }

    private fun dailyCodes(vararg codes: Int): List<HourForecast> =
        codes.mapIndexed { index, code -> hour(index, code) }

    private fun hour(hourOfDay: Int, code: Int): HourForecast = HourForecast(
        time = LocalDateTime.of(date, LocalTime.of(hourOfDay, 0)),
        temperatureC = 0.0,
        weatherCode = code
    )

    private fun report(hours: List<HourForecast>): WeatherReport = WeatherReport(
        cityId = "dalian",
        fetchedAtMillis = 123L,
        hourly = hours,
        daily = listOf(DailyForecast(
            date = date,
            weatherCode = 3,
            tempMaxC = 8.0,
            tempMinC = 2.0,
            precipProbabilityMaxPercent = null,
            uvIndexMax = null,
            sunrise = LocalTime.of(6, 0),
            sunset = LocalTime.of(17, 0),
            conditionText = "阴"
        ))
    )
}
