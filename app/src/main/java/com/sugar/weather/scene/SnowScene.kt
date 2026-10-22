package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 雪场景。小颗粒画圆点，大颗粒画旋转六边形；少量颗粒使用原 App 的薄荷绿色作为点缀。
 * 雨夹雪由 [CompositeScene] 叠加一层小雨实现。
 */
class SnowScene(private val heavy: Boolean) : WeatherScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val hexPath = Path()

    private val count = if (heavy) 200 else 110
    private val baseX = FloatArray(count)
    private val y = FloatArray(count)
    private val radiusDp = FloatArray(count)
    private val speedDp = FloatArray(count)
    private val swayAmpDp = FloatArray(count)
    private val swaySpeed = FloatArray(count)
    private val swayPhase = FloatArray(count)
    private val spinDeg = FloatArray(count)
    private val spinSpeed = FloatArray(count)
    private val alpha = IntArray(count)
    private val colorIndex = IntArray(count)
    private val depth = FloatArray(count)
    private val random = Random(if (heavy) 3301 else 1201)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var rollFactor = 0f
    private var pitchFactor = 0f
    private var time = 0f

    init {
        buildHexPath()
        for (i in 0 until count) {
            // 雪花半径、下落速度、横向摆幅和自转速度都带随机性，避免整片雪花同步运动。
            radiusDp[i] = if (heavy) {
                1.4f + random.nextFloat() * 5.2f
            } else {
                1f + random.nextFloat() * 4f
            }
            speedDp[i] = (if (heavy) 38f else 28f) + random.nextFloat() * 62f
            swayAmpDp[i] = 8f + random.nextFloat() * 26f
            swaySpeed[i] = 0.5f + random.nextFloat() * 1.1f
            swayPhase[i] = random.nextFloat() * 6.28318f
            spinDeg[i] = random.nextFloat() * 360f
            spinSpeed[i] = (random.nextFloat() * 2f - 1f) * 48f
            alpha[i] = 120 + random.nextInt(105)
            // 大部分白色，少量浅蓝，约 9% 使用原 App 的薄荷绿色点缀。
            val roll = random.nextFloat()
            colorIndex[i] = when {
                roll < 0.09f -> 2
                roll < 0.28f -> 1
                else -> 0
            }
            // 深度用于给不同雪花不同的倾斜视差和前后层次。
            depth[i] = 0.45f + random.nextFloat() * 0.55f
        }
    }

    private fun buildHexPath() {
        hexPath.moveTo(1f, 0f)
        for (i in 1 until 6) {
            val angle = i * PI / 3.0
            hexPath.lineTo(cos(angle).toFloat(), sin(angle).toFloat())
        }
        hexPath.close()
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        // 初始时铺满整个屏幕，保证第一帧就能看到雪，而不是从天空慢慢落下来。
        for (i in 0 until count) {
            baseX[i] = random.nextFloat() * width
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

        val wind = (if (heavy) 15f else 9f) * density
        for (i in 0 until count) {
            // 雪花一边下落一边被缓慢的横向风带走；左右摆动在 draw 中按正弦叠加。
            y[i] += speedDp[i] * density * dt
            baseX[i] += wind * dt
            if (y[i] - radiusDp[i] * density > height) {
                // 落到底部后回到顶部，并重新随机横向位置。
                y[i] = -radiusDp[i] * density
                baseX[i] = random.nextFloat() * width
            }
            // 横向越界时从另一侧绕回，避免雪花在屏幕外“消失”。
            if (baseX[i] < -width * 0.1f) baseX[i] = width * 1.1f
            if (baseX[i] > width * 1.1f) baseX[i] = -width * 0.1f
            spinDeg[i] += spinSpeed[i] * dt
        }
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        for (i in 0 until count) {
            val radius = radiusDp[i] * density
            // 摆动叠加在 baseX 上；倾斜时按深度追加视差位移，近处雪花移动更明显。
            val sway = sin(time * swaySpeed[i] + swayPhase[i]) * swayAmpDp[i] * density
            val drawX = baseX[i] + sway + rollFactor * 48f * density * depth[i]
            val drawY = y[i] + pitchFactor * 34f * density * depth[i]
            val color = when {
                colorIndex[i] == 2 -> 0xFF7DFFD4.toInt()
                colorIndex[i] == 1 -> 0xFFDDF3FF.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            paint.color = color
            paint.alpha = if (night) (alpha[i] * 0.85f).toInt() else alpha[i]
            // 小颗粒画成圆点（绘制便宜），大颗粒画成旋转六边形，和原 App 的雪花形状一致。
            if (radius < 2.6f * density) {
                canvas.drawCircle(drawX, drawY, radius, paint)
            } else {
                canvas.save()
                canvas.translate(drawX, drawY)
                canvas.rotate(spinDeg[i])
                canvas.scale(radius, radius)
                canvas.drawPath(hexPath, paint)
                canvas.restore()
            }
        }
    }
}
