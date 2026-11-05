package com.sugar.weather.data

import com.sugar.weather.data.model.HourForecast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 白天天气聚合的纯 JVM 单测:防止「日天气取当天最严重小时」把短时大雪夸大成全天大雪。
 */
class DailyConditionTest {

    private val date: LocalDate = LocalDate.of(2026, 11, 8)

    @Test
    fun `rain clearing in the afternoon reports transition`() {
        // CMA 11/8 大连的典型形态:上午毛毛雨,中午转多云,下午起晴。
        val condition = deriveDayCondition(
            hours = daytimeCodes(53, 51, 51, 51, 2, 1, 0, 0, 0, 0, 0, 0),
            date = date,
            fallbackCode = 61
        )

        assertEquals(0, condition.weatherCode)
        assertEquals("毛毛雨转晴", condition.conditionText)
    }

    @Test
    fun `rain turning to snow does not become heavy snow all day`() {
        // best_match 11/8 的形态:08-09 毛毛雨,10-19 雪;日码 75(大雪)只是 16-17 两小时。
        val condition = deriveDayCondition(
            hours = daytimeCodes(55, 55, 71, 73, 73, 71, 71, 73, 75, 75, 73, 71),
            date = date,
            fallbackCode = 75
        )

        assertEquals(73, condition.weatherCode)
        assertEquals("毛毛雨转中雪", condition.conditionText)
    }

    @Test
    fun `continuous snow keeps a single short condition`() {
        val condition = deriveDayCondition(
            hours = daytimeCodes(*IntArray(12) { 75 }),
            date = date,
            fallbackCode = 75
        )

        assertEquals(75, condition.weatherCode)
        assertEquals("大雪", condition.conditionText)
    }

    @Test
    fun `clear day reports sunny`() {
        val condition = deriveDayCondition(
            hours = daytimeCodes(*IntArray(12) { 0 }),
            date = date,
            fallbackCode = 3
        )

        assertEquals(0, condition.weatherCode)
        assertEquals("晴", condition.conditionText)
    }

    @Test
    fun `missing daytime hours fall back to daily code`() {
        val nighttime = listOf(
            hour(0, 61),
            hour(1, 61)
        )

        val condition = deriveDayCondition(
            hours = nighttime,
            date = date,
            fallbackCode = 61
        )

        assertEquals(61, condition.weatherCode)
        assertNull(condition.conditionText)
    }

    private fun daytimeCodes(vararg codes: Int): List<HourForecast> =
        codes.mapIndexed { index, code ->
            hour(DAY_START_HOUR + index, code)
        }

    private fun hour(hourOfDay: Int, code: Int): HourForecast = HourForecast(
        time = LocalDateTime.of(date, LocalTime.of(hourOfDay, 0)),
        temperatureC = 0.0,
        weatherCode = code
    )

    private companion object {
        /** 白天窗口起点 08:00;12 个样本覆盖 08:00–19:00。 */
        const val DAY_START_HOUR = 8
    }
}
