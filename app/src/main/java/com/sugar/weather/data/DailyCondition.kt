package com.sugar.weather.data

import com.sugar.weather.data.model.HourForecast
import java.time.LocalDate

/**
 * 城市本地自然日的天气摘要；首页与详情共享过程，只在展示长度上有所区别。
 *
 * 图标优先表示降水或特殊现象，同类强度按主导样本选择；晴、少云等先归类再统计。
 * 短摘要保留重要转折，详情补充凌晨、白天、夜间，避免夜间降水被白天多数票覆盖。
 * 没有逐小时数据时保留 API 日天气码，不根据气温猜测雨夹雪。
 */
internal data class DayCondition(
    val weatherCode: Int,
    val summaryText: String?,
    val conditionText: String?
)

/** 按自然日聚合天气过程，普通云量变化可简化，降水与特殊现象始终保留。 */
internal fun deriveDayCondition(
    hours: List<HourForecast>,
    date: LocalDate,
    fallbackCode: Int
): DayCondition {
    val dailyHours = hours.filter { it.time.toLocalDate() == date }
        .distinctBy { it.time }
        .sortedBy { it.time }
    if (dailyHours.isEmpty()) return DayCondition(fallbackCode, null, null)

    val codes = dailyHours.map { it.weatherCode }
    val special = codes.filter(::isSpecialCode)
    val phenomena = codes.filter { !categoryOf(it).isSky }
    val representative = when {
        special.isNotEmpty() -> special.maxByOrNull(::displayPriority)!!
        phenomena.isNotEmpty() -> representativeCode(phenomena)
        else -> representativeCode(codes)
    }

    val summary = if (special.isNotEmpty()) {
        // 危险现象即使只有一个小时也进入首页，时段与其他过程仍保留在详情。
        val prefix = if (special.size == 1 && dailyHours.size > 1) "短时" else ""
        prefix + dailyDesc(representative)
    } else {
        summarize(dailyHours, includePeak = false)
    }
    val periods = listOf(
        Period("凌晨", 0, 8),
        Period("白天", 8, 20),
        Period("夜间", 20, 24)
    ).mapNotNull { period ->
        val periodHours = dailyHours.filter { it.time.hour in period.start until period.end }
        if (periodHours.isEmpty()) return@mapNotNull null
        val text = summarize(periodHours, includePeak = true)
        // “雨雪交替”等压缩描述仍需保留其间的强降水，不能因省略过程而漏掉。
        val strong = periodHours.map { it.weatherCode }.filter { it in STRONG_PRECIPITATION_CODES }
            .map(::dailyDesc).distinct().filterNot { text.contains(it) }
        period.label to if (strong.isEmpty()) text else text + "（短时" + strong.joinToString("、") + "）"
    }
    val detail = if (periods.size == 3 && periods.map { it.second }.distinct().size == 1) {
        periods.first().second
    } else {
        periods.joinToString("；") { (label, text) -> label + text }
    }
    return DayCondition(representative, summary, detail)
}

/** 同类别连续样本；遇到缺失小时分段，不能把数据缺口解释成连续天气。 */
private class Segment(val category: WeatherCategory, val hours: MutableList<HourForecast>)

private data class Period(val label: String, val start: Int, val end: Int)

/** 冻雨单列，防止被普通降雨的多数票吞掉。 */
private enum class WeatherCategory(val isSky: Boolean = false) {
    CLEAR(true), CLOUDY(true), OVERCAST(true), FOG, RAIN, SNOW, FREEZING_RAIN, THUNDER, HAIL
}

private fun categoryOf(code: Int): WeatherCategory = when (code) {
    0, 1 -> WeatherCategory.CLEAR
    2 -> WeatherCategory.CLOUDY
    3 -> WeatherCategory.OVERCAST
    45, 48 -> WeatherCategory.FOG
    56, 57, 66, 67 -> WeatherCategory.FREEZING_RAIN
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> WeatherCategory.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCategory.SNOW
    95 -> WeatherCategory.THUNDER
    96, 99 -> WeatherCategory.HAIL
    else -> WeatherCategory.CLOUDY
}

/** 生成有限长度的过程描述；多次晴雨往返用“间歇”表达，雨雪交替不写成雨夹雪。 */
private fun summarize(hours: List<HourForecast>, includePeak: Boolean): String {
    val segments = mutableListOf<Segment>()
    for (hour in hours) {
        val category = categoryOf(hour.weatherCode)
        val last = segments.lastOrNull()
        if (last?.category == category && last.hours.last().time.plusHours(1) == hour.time) {
            last.hours += hour
        } else {
            segments += Segment(category, mutableListOf(hour))
        }
    }
    val events = segments.filter { !it.category.isSky }
    if (events.isEmpty()) {
        val significant = segments.filter { it.hours.size >= MIN_SKY_HOURS }
        val first = significant.firstOrNull()
        val last = significant.lastOrNull()
        // 只有两个稳定天空阶段时保留转折，频繁云量变化仍取主导状况。
        return if (significant.size == 2 && first != null && last != null &&
            first.category != last.category
        ) {
            segmentDesc(first) + "转" + segmentDesc(last)
        } else {
            dailyDesc(representativeCode(hours.map { it.weatherCode }))
        }
    }

    // 云量中间过程不挤占雨、雪、冻雨等重要转折，合并相邻的同类事件。
    val phases = mutableListOf<Segment>()
    for (event in events) {
        val last = phases.lastOrNull()
        if (last?.category == event.category) last.hours += event.hours
        else phases += Segment(event.category, event.hours.toMutableList())
    }
    if (phases.size > 1) {
        return if (phases.size <= MAX_PHASES) {
            phases.joinToString("转") { segmentDesc(it, includePeak = includePeak) }
        } else if (phases.all { it.category == WeatherCategory.RAIN || it.category == WeatherCategory.SNOW }) {
            "雨雪交替"
        } else {
            // 频繁变化时列出实际出现的类别，避免过长文案或删掉中间危险现象。
            phases.distinctBy { it.category }
                .joinToString("、") { segmentDesc(it, includePeak = includePeak) }
        }
    }

    val event = phases.single()
    val eventText = segmentDesc(event, includePeak = includePeak)
    val dryHours = hours.filter { categoryOf(it.weatherCode).isSky }
    // 只有实际观测到晴/阴等间隔才称“间歇”，数据缺口不能证明降水暂停。
    val hasDryBreak = events.zipWithNext().any { (before, after) ->
        dryHours.any { it.time.isAfter(before.hours.last().time) && it.time.isBefore(after.hours.first().time) }
    }
    return when {
        hasDryBreak -> "间歇" + eventText
        event.hours.size == 1 && hours.size > 1 -> "短时" + eventText
        dryHours.size < MIN_SKY_HOURS -> eventText
        segments.first().category.isSky && !segments.last().category.isSky ->
            dailyDesc(representativeCode(dryHours.map { it.weatherCode })) + "转" + eventText
        !segments.first().category.isSky && segments.last().category.isSky ->
            eventText + "转" + dailyDesc(representativeCode(dryHours.map { it.weatherCode }))
        else -> dailyDesc(representativeCode(dryHours.map { it.weatherCode })) + "有" + eventText
    }
}

/** 同类先合并再计数，避免晴与少云拆票后输给较短的阴天。 */
private fun representativeCode(codes: List<Int>): Int {
    val category = codes.groupBy(::categoryOf).entries
        .maxWithOrNull(compareBy<Map.Entry<WeatherCategory, List<Int>>> { it.value.size }
            .thenBy { entry -> entry.value.maxOf(::displayPriority) })!!.key
    return codes.filter { categoryOf(it) == category }.groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }
            .thenBy { displayPriority(it.key) })!!.key
}

/** 主导强度描述旁保留短时强降水，避免把一小时大雪概括成全天大雪或完全漏掉。 */
private fun segmentDesc(segment: Segment, includePeak: Boolean = true): String {
    val codes = segment.hours.map { it.weatherCode }
    val code = representativeCode(codes)
    val peak = codes.filter { it in STRONG_PRECIPITATION_CODES }.maxByOrNull(::displayPriority)
    return if (includePeak && peak != null && peak != code) {
        dailyDesc(code) + "（短时" + dailyDesc(peak) + "）"
    } else {
        dailyDesc(code)
    }
}

/** 日摘要收敛细碎修饰，不把 WMO 小时强度重新定义成国内日累计降水等级。 */
private fun dailyDesc(code: Int): String = when (code) {
    0, 1 -> "晴"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻毛毛雨"
    66, 67 -> "冻雨"
    80, 81 -> "阵雨"
    82 -> "强阵雨"
    85 -> "阵雪"
    86 -> "强阵雪"
    96, 99 -> "雷雨伴冰雹"
    else -> wmoDesc(code)
}

private fun isSpecialCode(code: Int): Boolean = code in SPECIAL_CODES

/** 仅用于摘要展示及平票取舍，不代表不同灾害之间通用的危险程度排序。 */
private fun displayPriority(code: Int): Int = when (code) {
    96, 99 -> 500 + code
    95 -> 400
    56, 57, 66, 67 -> 300 + code
    71, 73, 75, 77, 85, 86 -> 200 + code
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> 100 + code
    else -> code
}

/** 只过滤短暂云量变化；降水、雾与特殊天气不套用此门槛。 */
private const val MIN_SKY_HOURS = 2
private const val MAX_PHASES = 3
private val STRONG_PRECIPITATION_CODES = setOf(65, 75, 82, 86)
private val SPECIAL_CODES = setOf(56, 57, 66, 67, 95, 96, 99)
