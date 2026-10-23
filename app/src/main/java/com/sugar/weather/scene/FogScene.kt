package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 雾场景：24 个巨大的线性渐变圆缓慢漂移、旋转并相互叠加，模拟低能见度的雾团。
 */
class FogScene : WeatherScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // 雾团本身是半透明的，叠得越多越接近均匀的灰蓝色。
        alpha = 228
    }
    // 日/夜各预生成一套渐变 Shader，绘制时只切换引用，避免每帧创建对象。
    private val dayShaders = arrayOfNulls<Shader>(COUNT)
    private val nightShaders = arrayOfNulls<Shader>(COUNT)
    private val radiusPx = FloatArray(COUNT)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var rollFactor = 0f
    private var pitchFactor = 0f
    private var time = 0f

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        for (i in 0 until COUNT) {
            // 原 App 的雾团坐标是以 1080×1920 为基准的像素值，这里按屏幕宽高归一化。
            val radius = REF_RADIUS[i] / REF_WIDTH * width
            radiusPx[i] = radius
            // Shader 使用以圆心为原点的局部坐标，绘制时通过 canvas 变换定位和旋转。
            dayShaders[i] = LinearGradient(
                -radius, 0f, radius, 0f,
                intArrayOf(DAY_BASE, DAY_BASE, DAY_HIGHLIGHT),
                null,
                Shader.TileMode.CLAMP
            )
            nightShaders[i] = LinearGradient(
                -radius, 0f, radius, 0f,
                intArrayOf(NIGHT_BASE, NIGHT_BASE, NIGHT_HIGHLIGHT),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        time = elapsedSec
        // 手机左右/前后倾斜时，整片雾层做小幅反向视差，避免画面显得完全贴死。
        rollFactor = tilt.rollDeg.coerceIn(-90f, 90f) / 90f
        pitchFactor = ((abs(tilt.pitchDeg) - 90f) / 90f).coerceIn(-1f, 1f)
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        paint.alpha = if (night) 236 else 226
        val tiltX = rollFactor * 28f * density
        val tiltY = pitchFactor * 46f * density
        for (i in 0 until COUNT) {
            val radius = radiusPx[i]
            // 每个雾团用不同频率做轻微漂移，叠加后形成缓慢翻滚的雾层。
            val driftX = sin(time * 0.06f + i * 1.7f) * radius * 0.045f
            val driftY = cos(time * 0.045f + i * 1.3f) * radius * 0.035f
            val x = REF_X[i] / REF_WIDTH * width + tiltX + driftX
            val y = REF_Y[i] / REF_HEIGHT * height + tiltY + driftY
            paint.shader = if (night) nightShaders[i] else dayShaders[i]
            // 渐变方向随每个雾团缓慢旋转，让圆形边缘的明暗过渡不断变化。
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(time * 4f + i * 31f)
            canvas.drawCircle(0f, 0f, radius, paint)
            canvas.restore()
        }
        paint.shader = null
    }

    private companion object {
        /** 雾团数量。 */
        const val COUNT = 24

        /** 参考画布尺寸，用于把原视觉坐标归一化到当前屏幕。 */
        const val REF_WIDTH = 1080f
        const val REF_HEIGHT = 1920f

        /** 日间雾团的暗部 / 亮部颜色。 */
        const val DAY_BASE = 0xFF617D8F.toInt()
        const val DAY_HIGHLIGHT = 0xFF7798A9.toInt()

        /** 夜间雾团的暗部 / 亮部颜色。 */
        const val NIGHT_BASE = 0xFF2A383F.toInt()
        const val NIGHT_HIGHLIGHT = 0xFF32424A.toInt()

        /** 雾团中心的参考 x 坐标（基准宽度 1080）。 */
        val REF_X = floatArrayOf(
            872f, 196f, 80f, 836f, 1528f, 940f, 288f, 1467f, -260f, 1840f, 668f, -360f,
            229f, 1028f, 1459f, 654f, -72f, 1480f, 1028f, 220f, 604f, 1316f, 204f, 924f
        )

        /** 雾团中心的参考 y 坐标（基准高度 1920）。 */
        val REF_Y = floatArrayOf(
            2436f, 2338f, 1876f, 1864f, 1784f, 1564f, 1356f, 1211f, 1184f, 1124f, 882f,
            699f, 609f, 532f, 532f, 263f, 244f, 12f, -201f, -350f, -618f, -604f, -731f, -828f
        )

        /** 雾团参考半径（基准宽度 1080）。 */
        val REF_RADIUS = floatArrayOf(
            395.5f, 412f, 542.5f, 426.5f, 425.5f, 419f, 408f, 412.5f, 509f, 410.5f, 531f,
            515.5f, 460f, 250f, 317f, 296f, 484.5f, 509.5f, 391f, 492.5f, 561.5f, 233.5f,
            344.5f, 350f
        )
    }
}
