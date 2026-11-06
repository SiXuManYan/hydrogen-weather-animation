package com.sugar.weather.scene

import android.graphics.Canvas
import kotlin.math.roundToInt

/**
 * 单个天气场景只负责“背景之上的内容”，纯色背景由 [SceneType] 提供。
 *
 * 渲染线程每帧先调用 [update] 再调用 [draw]。
 * [draw] 在硬件加速的 Canvas 上执行，必须避免分配对象（尤其是雨雪粒子场景），以稳定 60fps。
 */
interface WeatherScene {
    /** 尺寸或密度变化时重新计算与屏幕相关的参数，例如半径、基准位置和渐变。 */
    fun onSizeChanged(width: Int, height: Int, density: Float)

    /**
     * 每个渲染帧调用一次，用于推进自转、颜色呼吸和粒子运动。
     * [dtMs] 已由 View 限制在 30ms 内，[elapsedSec] 是场景自身的累计时间。
     */
    fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float)

    /** 将当前场景绘制到 Canvas；实现中应复用 Paint/Path/数组，避免在绘制时分配对象。 */
    fun draw(canvas: Canvas, night: Boolean)
}

/** 按显示面积缩减粒子数组，保留少量粒子以维持小卡片的天气辨识度。 */
internal fun particleCount(baseCount: Int, scale: Float): Int =
    (baseCount * scale.coerceIn(0.05f, 1f)).roundToInt().coerceIn(12, baseCount)
