package com.template.framework.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Decimal arithmetic with an explicit, consistent rounding policy.
 *
 * Operations round only their final result. Prefer constructing monetary values from strings;
 * use [fromDouble] only when the input already exists as a `Double`.
 * - 中文：统一处理 `BigDecimal` 精度与舍入，金额建议优先从字符串构造。
 */
object DecimalUtils {

    /** Default number of digits kept after the decimal point. */
    const val DEFAULT_SCALE = 2

    /** Default financial-style rounding mode used by all helpers. */
    val DEFAULT_ROUNDING_MODE: RoundingMode = RoundingMode.HALF_UP

    /** Creates a decimal without inheriting the binary representation error of [Double]. */
    @JvmStatic
    @JvmOverloads
    fun fromDouble(
        value: Double,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = round(BigDecimal.valueOf(value), scale, roundingMode)

    /** Returns [value] rounded to [scale] using [roundingMode]. */
    @JvmStatic
    @JvmOverloads
    fun round(
        value: BigDecimal,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = value.setScale(scale, roundingMode)

    /** Adds [left] and [right], then rounds the result once. */
    @JvmStatic
    @JvmOverloads
    fun add(
        left: BigDecimal,
        right: BigDecimal,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = round(left.add(right), scale, roundingMode)

    /** Sums all values before rounding, avoiding accumulated intermediate rounding error. */
    @JvmStatic
    @JvmOverloads
    fun sum(
        values: Iterable<BigDecimal>,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal {
        val total = values.fold(BigDecimal.ZERO, BigDecimal::add)
        return round(total, scale, roundingMode)
    }

    /** Subtracts [right] from [left], then rounds the result once. */
    @JvmStatic
    @JvmOverloads
    fun subtract(
        left: BigDecimal,
        right: BigDecimal,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = round(left.subtract(right), scale, roundingMode)

    /** Multiplies [left] by [right], then rounds the result once. */
    @JvmStatic
    @JvmOverloads
    fun multiply(
        left: BigDecimal,
        right: BigDecimal,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = round(left.multiply(right), scale, roundingMode)

    /**
     * Divides [dividend] by [divisor] using the requested scale and rounding mode.
     *
     * @throws ArithmeticException when [divisor] is zero
     */
    @JvmStatic
    @JvmOverloads
    fun divide(
        dividend: BigDecimal,
        divisor: BigDecimal,
        scale: Int = DEFAULT_SCALE,
        roundingMode: RoundingMode = DEFAULT_ROUNDING_MODE
    ): BigDecimal = dividend.divide(divisor, scale, roundingMode)
}
