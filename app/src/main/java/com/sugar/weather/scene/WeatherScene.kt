package com.sugar.weather.scene

import android.graphics.Canvas

/**
 * 单个天气场景只负责“背景之上的内容”，纯色背景由 [SceneType] 提供。
 *
 * 渲染线程每帧先调用 [update] 再调用 [draw]。
 * [draw] 在硬件加速的 Canvas 上执行，必须避免分配对象（尤其是雨雪粒子场景），以稳定 60fps。
 */
interface WeatherScene {
    fun onSizeChanged(width: Int, height: Int, density: Float)

    fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float)

    fun draw(canvas: Canvas, night: Boolean)
}
