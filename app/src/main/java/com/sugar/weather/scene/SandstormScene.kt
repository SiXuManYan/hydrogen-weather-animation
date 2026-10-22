package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 沙尘暴场景：暖色竖向渐变 + 漂移的沙尘团 + 高速横向沙粒。
 */
class SandstormScene : WeatherScene {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val x = FloatArray(COUNT)
    private val y = FloatArray(COUNT)
    private val radiusDp = FloatArray(COUNT)
    private val speedDp = FloatArray(COUNT)
    private val alphaBase = IntArray(COUNT)
    private val colorIndex = IntArray(COUNT)
    private val phase = FloatArray(COUNT)
    private val random = Random(9107)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var time = 0f
    private var rollFactor = 0f
    private var pitchFactor = 0f

    init {
        // 沙粒的尺寸、速度、透明度、颜色和摆动相位随机化，模拟随风卷起的沙尘。
        for (i in 0 until COUNT) {
            radiusDp[i] = 0.6f + random.nextFloat() * 2.4f
            speedDp[i] = 110f + random.nextFloat() * 330f
            alphaBase[i] = 35 + random.nextInt(125)
            colorIndex[i] = random.nextInt(3)
            phase[i] = random.nextFloat() * 6.28318f
        }
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        // 背景不是纯色，而是自上而下由亮沙色过渡到深褐色，增加沙尘暴的压迫感。
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFFBBA66A.toInt(), 0xFFA59056.toInt(), 0xFF8B7545.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
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
        rollFactor = tilt.rollDeg.coerceIn(-90f, 90f) / 90f
        pitchFactor = ((abs(tilt.pitchDeg) - 90f) / 90f).coerceIn(-1f, 1f)
        for (i in 0 until COUNT) {
            // 沙粒主要做高速横向运动，纵向只叠加小幅正弦漂移。
            x[i] -= speedDp[i] * density * dt
            y[i] += sin(time * 0.8f + phase[i]) * 22f * density * dt
            // 出界后从另一侧重新进入，保证任意时刻画面中都有足量沙粒。
            if (x[i] < -16f * density) {
                x[i] = width + 16f * density
                y[i] = random.nextFloat() * height
            }
            if (y[i] < -16f * density) y[i] = height + 8f * density
            if (y[i] > height + 16f * density) y[i] = -8f * density
        }
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        gradientPaint.alpha = if (night) 210 else 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

        val tiltX = rollFactor * 70f * density
        val tiltY = pitchFactor * 46f * density
        // 先铺几层巨大的半透明沙尘团，作为远景沙幕。
        for (i in 0 until DUST_COUNT) {
            dustPaint.color = if (night) 0xFF4A3D28.toInt() else 0xFFC8B47A.toInt()
            dustPaint.alpha = if (night) 34 else 44
            val radius = (180f + i * 74f) * density
            val drift = sin(time * 0.12f + i * 1.9f) * 40f * density
            canvas.drawCircle(
                width * (0.15f + i * 0.24f) + drift + tiltX,
                height * (0.22f + i * 0.18f) + tiltY,
                radius,
                dustPaint
            )
        }

        // 再画细小的沙粒，位于沙幕之前，形成明确的前后层次。
        for (i in 0 until COUNT) {
            grainPaint.color = COLORS[colorIndex[i]]
            grainPaint.alpha = (alphaBase[i] * (if (night) 0.75f else 1f)).toInt()
            canvas.drawCircle(
                x[i] + tiltX,
                y[i] + tiltY,
                radiusDp[i] * density,
                grainPaint
            )
        }
    }

    private companion object {
        const val COUNT = 170
        const val DUST_COUNT = 4
        val COLORS = intArrayOf(
            0xFFE0CE96.toInt(),
            0xFFC9B47A.toInt(),
            0xFF9A8453.toInt()
        )
    }
}
