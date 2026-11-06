package com.template.framework.util

import java.math.BigDecimal
import java.math.RoundingMode

private const val DEFAULT_DECIMAL_SCALE = 2

fun Double.toScaledDecimal(
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = BigDecimal.valueOf(this).setScale(scale, roundingMode)

fun BigDecimal.scaled(
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = setScale(scale, roundingMode)

fun BigDecimal.plusScaled(
    other: BigDecimal,
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = add(other).setScale(scale, roundingMode)

fun BigDecimal.minusScaled(
    other: BigDecimal,
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = subtract(other).setScale(scale, roundingMode)

fun BigDecimal.timesScaled(
    other: BigDecimal,
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = multiply(other).setScale(scale, roundingMode)

fun BigDecimal.dividedBy(
    divisor: BigDecimal,
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal {
    require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Divisor must not be zero" }
    return divide(divisor, scale, roundingMode)
}

fun Iterable<BigDecimal>.sumScaled(
    scale: Int = DEFAULT_DECIMAL_SCALE,
    roundingMode: RoundingMode = RoundingMode.HALF_UP
): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add).setScale(scale, roundingMode)
