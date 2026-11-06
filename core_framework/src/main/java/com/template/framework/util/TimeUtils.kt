package com.template.framework.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** 时间与日期工具；时长按非负值格式化，日期转换显式支持时区与 Locale。 */
object TimeUtils {
    const val DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    /** 输出 MM:SS，超过一小时后输出 HH:MM:SS。 */
    fun formatTime(milliseconds: Long): String = formatDuration(milliseconds, alwaysShowHours = false)

    /** 始终包含小时，输出 HH:MM:SS。 */
    fun formatTimeWithHours(milliseconds: Long): String =
        formatDuration(milliseconds, alwaysShowHours = true)

    fun formatEpochMillis(
        milliseconds: Long,
        pattern: String = DEFAULT_DATE_TIME_PATTERN,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(milliseconds))

    fun parseToEpochMillis(
        value: String,
        pattern: String = DEFAULT_DATE_TIME_PATTERN,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): Long? = try {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern, locale))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    fun reformat(
        value: String,
        inputPattern: String = DEFAULT_DATE_TIME_PATTERN,
        outputPattern: String,
        locale: Locale = Locale.getDefault()
    ): String? = try {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern(inputPattern, locale))
            .format(DateTimeFormatter.ofPattern(outputPattern, locale))
    } catch (_: DateTimeParseException) {
        null
    }

    fun isSameDay(
        firstEpochMillis: Long,
        secondEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean = Instant.ofEpochMilli(firstEpochMillis).atZone(zoneId).toLocalDate() ==
        Instant.ofEpochMilli(secondEpochMillis).atZone(zoneId).toLocalDate()

    fun datesFromToday(
        count: Int,
        today: LocalDate = LocalDate.now()
    ): List<LocalDate> {
        require(count >= 0) { "count must not be negative" }
        return List(count) { offset -> today.plusDays(offset.toLong()) }
    }

    fun age(birthDate: LocalDate, today: LocalDate = LocalDate.now()): Int {
        require(!birthDate.isAfter(today)) { "birthDate must not be in the future" }
        return java.time.Period.between(birthDate, today).years
    }

    private fun formatDuration(milliseconds: Long, alwaysShowHours: Boolean): String {
        val duration = Duration.ofMillis(milliseconds.coerceAtLeast(0))
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (alwaysShowHours || hours > 0) {
            "%02d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        } else {
            "%02d:%02d".format(Locale.ROOT, minutes, seconds)
        }
    }
}
