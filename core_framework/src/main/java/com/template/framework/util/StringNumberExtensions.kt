package com.template.framework.util

import java.math.BigDecimal

/**
 * Parses a trimmed integer, or returns [defaultValue].
 * - 中文：解析失败时返回默认值。
 */
fun String?.toIntOrDefault(defaultValue: Int = 0): Int {
    return this?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: defaultValue
}

/**
 * Parses a trimmed long, or returns [defaultValue].
 * - 中文：解析失败时返回默认值。
 */
fun String?.toLongOrDefault(defaultValue: Long = 0L): Long {
    return this?.trim()?.takeIf(String::isNotEmpty)?.toLongOrNull() ?: defaultValue
}

/**
 * Parses a trimmed decimal, or returns [defaultValue].
 * - 中文：解析失败时返回默认值。
 */
fun String?.toBigDecimalOrDefault(defaultValue: BigDecimal = BigDecimal.ZERO): BigDecimal {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return defaultValue
    return normalized.toBigDecimalOrNull() ?: defaultValue
}
