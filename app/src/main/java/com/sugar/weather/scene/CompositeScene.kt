package com.sugar.weather.scene

import android.graphics.Canvas

/**
 * 把多个场景叠加绘制，目前用于雨夹雪（小雨 + 小雪）。
 */
class CompositeScene(private vararg val scenes: WeatherScene) : WeatherScene {

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        for (scene in scenes) {
            scene.onSizeChanged(width, height, density)
        }
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        // 所有子场景共享同一个时基和姿态，保证雨、雪等叠加层的运动节奏一致。
        for (scene in scenes) {
            scene.update(dtMs, tilt, night, elapsedSec)
        }
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        // 按传入顺序绘制，后一个场景覆盖在前一个之上（当前只有雨夹雪在用）。
        for (scene in scenes) {
            scene.draw(canvas, night)
        }
    }
}
