package com.sugar.weather.data

import java.util.Locale

/**
 * 单位与数值格式化(DESIGN.md §5.4,对齐截图 005344)。
 *
 * 全部为纯 Kotlin 函数,不依赖 Android,可直接 JVM 单测。
 * 单位固定公制:温度 °C、风速 km/h、气压 hPa、能见度 km(§5.4 末行)。
 */

// —— 供 DetailCardUi(label, value, unit)拆分使用的单位常量 ——
const val UNIT_TEMP = "°"
const val UNIT_HIGH_LOW = "°C"
const val UNIT_HUMIDITY = "%"
const val UNIT_WIND_SPEED = "km/h"
const val UNIT_PRESSURE = "hPa"
const val UNIT_VISIBILITY = "km"

/** 四舍五入到整数(§5.4「四舍五入整数」口径,正负同规则)。 */
fun formatRoundInt(value: Double): String = Math.round(value).toString()

/** 1 位小数(§5.4 的「1 位小数」;固定小数点,不随系统 locale 漂移)。 */
fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

/** 当前温度 / 逐时温度:「14°」。 */
fun formatTemperatureC(value: Double): String = formatRoundInt(value) + UNIT_TEMP

/** 高/低温:「16/3°C」。 */
fun formatHighLowC(tempMaxC: Double, tempMinC: Double): String =
    formatRoundInt(tempMaxC) + "/" + formatRoundInt(tempMinC) + UNIT_HIGH_LOW

/** 湿度:「84.0 %」(数值与单位间一个空格,§5.4)。 */
fun formatHumidity(percent: Double): String = formatOneDecimal(percent) + " " + UNIT_HUMIDITY

/** 风速:「39.0 km/h」。 */
fun formatWindSpeedKmh(kmh: Double): String = formatOneDecimal(kmh) + " " + UNIT_WIND_SPEED

/** 气压:「1007 hPa」。 */
fun formatPressureHpa(hpa: Double): String = formatRoundInt(hpa) + " " + UNIT_PRESSURE

/** 能见度:「8.0 km」;入参为 km(Mapper 已把米的原始值 /1000),缺失显示「—」。 */
fun formatVisibilityKm(km: Double?): String =
    if (km == null) UNKNOWN_TEXT else formatOneDecimal(km) + " " + UNIT_VISIBILITY

/** PM2.5:「19.0」——1 位小数、无单位后缀(§5.1,与截图一致);缺失显示「—」。 */
fun formatPm25(pm25: Double?): String = if (pm25 == null) UNKNOWN_TEXT else formatOneDecimal(pm25)
