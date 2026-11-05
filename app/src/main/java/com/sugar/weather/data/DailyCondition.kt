package com.sugar.weather.data

import com.sugar.weather.data.model.HourForecast
import java.time.LocalDate
import java.time.LocalTime

/**
 * 由逐小时预报推导「白天天气」。
 *
 * Open-Meteo 的 `daily.weather_code` 表示“当天最严重的小时天气”:一场两小时的大雪
 * 会把全天标成「大雪」,与国内天气 App 的白天主导天气习惯不符。这里改为按城市本地
 * 白天 08:00–19:59 的逐小时天气聚合:
 *
 * - [DayCondition.weatherCode]:出现次数最多的天气码(同次数取更严重者),用于图标
 *   与首页 6 天短文案;
 * - [DayCondition.conditionText]:按连续同类别分段后,若存在 ≥2 小时的显著转折,
 *   生成「小雨转晴」式描述;无转折时为该代表码的中文名。
 *
 * 夜间降水不参与白天描述;没有白天数据时回退到 API 的日天气码。
 */
internal data class DayCondition(
    val weatherCode: Int,
    val conditionText: String?
)

internal fun deriveDayCondition(
    hours: List<HourForecast>,
    date: LocalDate,
    fallbackCode: Int
): DayCondition {
    val daytime = hours.filter { hour ->
        val time = hour.time
        time.toLocalDate() == date &&
            !time.toLocalTime().isBefore(DAY_START) &&
            time.toLocalTime().isBefore(DAY_END)
    }
    if (daytime.isEmpty()) return DayCondition(fallbackCode, null)

    val representative = representativeCode(daytime.map { it.weatherCode })

    // 连续同类别合并为段,用于识别「上午下雨、下午转晴」这类白天转折。
    val segments = mutableListOf<Segment>()
    for (hour in daytime) {
        val category = categoryOf(hour.weatherCode)
        val last = segments.lastOrNull()
        if (last != null && last.category == category) {
            last.codes += hour.weatherCode
        } else {
            segments += Segment(category, mutableListOf(hour.weatherCode))
        }
    }

    val significant = segments.filter { it.codes.size >= MIN_TRANSITION_HOURS }
    // 最多保留三段:首段、最长中间段、末段;避免一天内多次晴雨变化把文案拉得过长。
    val transition = if (significant.size <= 3) {
        significant
    } else {
        listOfNotNull(
            significant.first(),
            significant.subList(1, significant.lastIndex).maxByOrNull { it.codes.size },
            significant.last()
        ).distinctBy { it.category }
    }
    val text = when {
        transition.size >= 2 &&
            transition.first().category != transition.last().category ->
            transition.joinToString(separator = "转") { segmentDesc(it) }
        transition.isNotEmpty() -> segmentDesc(transition.first())
        else -> wmoDesc(representative)
    }
    return DayCondition(representative, text)
}

/** 单段连续天气(同类别内可能有 51/53/55 等不同强度码)。 */
private class Segment(val category: WeatherCategory, val codes: MutableList<Int>)

/** 天气现象大类:同类之间不生成「毛毛雨转小雨」这类无意义转折。 */
private enum class WeatherCategory { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, SNOW, THUNDER, HAIL }

private fun categoryOf(code: Int): WeatherCategory = when (code) {
    0, 1 -> WeatherCategory.CLEAR
    2 -> WeatherCategory.CLOUDY
    3 -> WeatherCategory.OVERCAST
    45, 48 -> WeatherCategory.FOG
    in 51..67, in 80..82 -> WeatherCategory.RAIN
    in 71..77, 85, 86 -> WeatherCategory.SNOW
    95 -> WeatherCategory.THUNDER
    96, 99 -> WeatherCategory.HAIL
    else -> WeatherCategory.CLOUDY
}

/** 段内代表文案:出现次数最多的码,同次数取更严重者。 */
private fun segmentDesc(segment: Segment): String {
    val code = representativeCode(segment.codes)
    return when (categoryOf(code)) {
        // 51–57 的 WMO 原文带「(轻)/(浓)」,转折文案里统一收敛为「毛毛雨」。
        WeatherCategory.RAIN -> if (code in 51..57) {
            if (code == 56 || code == 57) "冻毛毛雨" else "毛毛雨"
        } else {
            wmoDesc(code)
        }
        // 96/99 的原文是「雷阵雨伴冰雹(弱/强)」,转折文案用「冰雹」即可。
        WeatherCategory.HAIL -> "冰雹"
        else -> wmoDesc(code)
    }
}

private fun representativeCode(codes: List<Int>): Int =
    codes.groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { severity(it.key) })
        ?.key
        ?: codes.first()

/**
 * 同次数时的保守取舍:降水 > 云 > 晴,雪 > 雨。仅用于打破平票,
 * 不参与跨类别的主导判断。
 */
private fun severity(code: Int): Int = when (code) {
    0 -> 0
    1 -> 1
    2 -> 2
    3 -> 3
    45 -> 4
    48 -> 5
    51 -> 10
    53 -> 11
    55 -> 12
    56 -> 13
    57 -> 14
    61 -> 20
    63 -> 21
    65 -> 22
    66 -> 23
    67 -> 24
    80 -> 25
    81 -> 26
    82 -> 27
    71 -> 30
    73 -> 31
    75 -> 32
    77 -> 33
    85 -> 34
    86 -> 35
    95 -> 40
    96 -> 41
    99 -> 42
    else -> -1
}

/** 白天窗口 [08:00, 20:00),与国内天气 App 的白天预报口径一致。 */
private val DAY_START: LocalTime = LocalTime.of(8, 0)
private val DAY_END: LocalTime = LocalTime.of(20, 0)

/** 少于 2 小时的天气不单独构成转折(避免短时阵雨把文案拉长)。 */
private const val MIN_TRANSITION_HOURS = 2
