package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 霾场景：大量细小、半透明的黄褐色颗粒缓慢漂移并闪烁。
 */
class HazeScene : WeatherScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val x = FloatArray(COUNT)
    private val y = FloatArray(COUNT)
    private val radiusDp = FloatArray(COUNT)
    private val speedDp = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)
    private val alphaBase = IntArray(COUNT)
    private val colorIndex = IntArray(COUNT)
    private val random = Random(7801)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var time = 0f
    private var rollFactor = 0f
    private var pitchFactor = 0f

    init {
        // 颗粒的半径、漂移速度、闪烁相位和基础透明度都随机化，避免看起来像规则点阵。
        for (i in 0 until COUNT) {
            radiusDp[i] = 0.7f + random.nextFloat() * 2.2f
            speedDp[i] = 5f + random.nextFloat() * 14f
            phase[i] = random.nextFloat() * 6.28318f
            alphaBase[i] = 60 + random.nextInt(150)
            colorIndex[i] = random.nextInt(3)
        }
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        for (i in 0 until COUNT) {
            x[i] = random.nextFloat() * width
            y[i] = random.nextFloat() * height
        }
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        if (width == 0 || height == 0) return
        val dt = dtMs / 1000f
        if (dt <= 0f) return
        time = elapsedSec
        // 用俯仰/横滚的归一化值给整片霾层做轻微视差。
        rollFactor = tilt.rollDeg.coerceIn(-90f, 90f) / 90f
        pitchFactor = ((abs(tilt.pitchDeg) - 90f) / 90f).coerceIn(-1f, 1f)
        for (i in 0 until COUNT) {
            // 颗粒缓慢横向漂移，并叠加一点上下浮动；出屏后从左侧重新进入。
            x[i] += speedDp[i] * density * dt
            y[i] += sin(time * 0.4f + phase[i]) * 4f * density * dt
            if (x[i] > width + 12f * density) {
                x[i] = -12f * density
                y[i] = random.nextFloat() * height
            }
        }
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        val tiltX = rollFactor * 40f * density
        val tiltY = pitchFactor * 34f * density
        for (i in 0 until COUNT) {
            paint.color = COLORS[colorIndex[i]]
            // 每颗粒用不同相位做明暗呼吸；夜间整体压暗，避免像发光点。
            val twinkle = 0.55f + 0.45f * sin(time * 1.1f + phase[i])
            paint.alpha = (alphaBase[i] * twinkle * if (night) 0.7f else 1f).toInt()
            canvas.drawCircle(
                x[i] + tiltX,
                y[i] + tiltY,
                radiusDp[i] * density,
                paint
            )
        }
    }

    private companion object {
        /** 霾颗粒数量。 */
        const val COUNT = 230

        /** 颗粒的黄褐色调色板。 */
        val COLORS = intArrayOf(
            0xFFD8C97A.toInt(),
            0xFFE2D58F.toInt(),
            0xFFB7A45C.toInt()
        )
    }
}
