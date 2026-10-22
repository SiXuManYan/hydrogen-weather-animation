package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.tan
import kotlin.random.Random

/**
 * 雨、暴雨、雷阵雨、冰雹共用。
 *
 * 每滴雨是一条斜线段，按强度调整数量、速度、线宽与透明度；[hail] 为 true 时会在
 * 线段末端追加白色冰雹。雷阵雨会以随机间隔触发一次画面闪光。
 */
class RainScene(
    private val intensity: Intensity,
    private val hail: Boolean = false
) : WeatherScene {

    enum class Intensity { LIGHT, MEDIUM, HEAVY, STORM, THUNDER }

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val hailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val flashPaint = Paint().apply { color = 0xFFFFFFFF.toInt() }

    private val count: Int
    private val x: FloatArray
    private val y: FloatArray
    private val lengthDp: FloatArray
    private val speedDp: FloatArray
    private val widthDp: FloatArray
    private val alpha: IntArray
    private val gold: BooleanArray
    private val hailRadiusDp: FloatArray

    private val random = Random(intensity.ordinal * 7919 + 17)
    private var width = 0
    private var height = 0
    private var density = 1f
    private var slope = 0f
    private var flash = 0f
    private var flashTimer = 2.4f

    init {
        // 配置数组下标：0 数量；1~2 速度范围；3~4 长度范围；5~6 线宽范围；
        // 7~8 透明度范围。数值均为 dp/秒 或 dp。
        val config = when (intensity) {
            Intensity.LIGHT -> floatArrayOf(170f, 480f, 900f, 16f, 34f, 0.6f, 1.5f, 70f, 150f)
            Intensity.MEDIUM -> floatArrayOf(230f, 650f, 1200f, 22f, 48f, 0.8f, 1.9f, 90f, 175f)
            Intensity.HEAVY -> floatArrayOf(290f, 850f, 1500f, 28f, 62f, 1f, 2.3f, 110f, 200f)
            Intensity.STORM -> floatArrayOf(330f, 1050f, 1800f, 34f, 78f, 1.2f, 2.7f, 130f, 220f)
            Intensity.THUNDER -> floatArrayOf(270f, 800f, 1450f, 26f, 58f, 0.9f, 2.2f, 100f, 195f)
        }
        count = config[0].toInt()
        val speedMin = config[1]
        val speedMax = config[2]
        val lengthMin = config[3]
        val lengthMax = config[4]
        val widthMin = config[5]
        val widthMax = config[6]
        val alphaMin = config[7].toInt()
        val alphaMax = config[8].toInt()
        x = FloatArray(count)
        y = FloatArray(count)
        lengthDp = FloatArray(count)
        speedDp = FloatArray(count)
        widthDp = FloatArray(count)
        alpha = IntArray(count)
        gold = BooleanArray(count)
        hailRadiusDp = FloatArray(count)
        for (i in 0 until count) {
            // depth 用来模拟雨滴离镜头的远近：近处更长、更快、更粗、更亮。
            val depth = 0.35f + random.nextFloat() * 0.65f
            speedDp[i] = random.nextFloat() * (speedMax - speedMin) + speedMin
            lengthDp[i] = (random.nextFloat() * (lengthMax - lengthMin) + lengthMin) * depth
            widthDp[i] = (random.nextFloat() * (widthMax - widthMin) + widthMin) * depth
            alpha[i] = (alphaMin + random.nextFloat() * (alphaMax - alphaMin)).toInt()
            // 少量雨滴使用原 App 的金黄色，避免画面全是白线。
            gold[i] = random.nextFloat() < 0.11f
            hailRadiusDp[i] = 1.1f + random.nextFloat() * 1.9f
        }
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
        // 初始时把雨滴均匀撒在整个屏幕及上方的缓冲区，避免首帧出现“空屏再落雨”。
        for (i in 0 until count) {
            x[i] = random.nextFloat() * width
            y[i] = random.nextFloat() * (height + lengthDp[i] * density) - lengthDp[i] * density
        }
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        if (width == 0 || height == 0) return
        val dt = dtMs / 1000f
        if (dt <= 0f) return
        val roll = tilt.rollDeg.coerceIn(-90f, 90f)
        // 雨线默认向左下倾斜约 8°；手机横滚时倾斜角随之变化，产生 3D 视角转动的感觉。
        val angle = (-8.0 + roll * 0.18).coerceIn(-32.0, 32.0)
        slope = tan(Math.toRadians(angle)).toFloat()

        for (i in 0 until count) {
            val speed = speedDp[i] * density
            val length = lengthDp[i] * density
            // 雨滴沿自身斜率下落，而不是只做竖直运动。
            y[i] += speed * dt
            x[i] += speed * slope * dt
            // 落出屏幕或横向越界后，从顶部重新生成，保持雨量稳定。
            if (y[i] - length > height || x[i] < -width * 0.2f || x[i] > width * 1.2f) {
                y[i] = -length - random.nextFloat() * height * 0.2f
                x[i] = random.nextFloat() * width
            }
        }

        if (intensity == Intensity.THUNDER) {
            // 每隔 2.8~8.3 秒触发一次闪光，flash 在约 0.24 秒内线性衰减到 0。
            flashTimer -= dt
            if (flashTimer <= 0f) {
                flash = 1f
                flashTimer = 2.8f + random.nextFloat() * 5.5f
            }
            if (flash > 0f) {
                flash = (flash - dt * 4.2f).coerceAtLeast(0f)
            }
        }
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        for (i in 0 until count) {
            // 白天以白色为主，少数金黄色；夜间换成偏冷的浅蓝与暖金色。
            val color = when {
                gold[i] && night -> 0xFFFFD45C.toInt()
                gold[i] -> 0xFFFFC400.toInt()
                night -> 0xFFD6E9FF.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val length = lengthDp[i] * density
            rainPaint.color = color
            rainPaint.alpha = alpha[i]
            rainPaint.strokeWidth = widthDp[i] * density
            val endX = x[i] + slope * length
            val endY = y[i] + length
            canvas.drawLine(x[i], y[i], endX, endY, rainPaint)
            if (hail) {
                // 冰雹画在雨线末端的 82% 处，像一颗颗白色冰粒被雨线拖着落下。
                hailPaint.alpha = 215
                canvas.drawCircle(
                    x[i] + slope * length * 0.82f,
                    y[i] + length * 0.82f,
                    hailRadiusDp[i] * density,
                    hailPaint
                )
            }
        }
        if (intensity == Intensity.THUNDER && flash > 0f) {
            // 一次性白色蒙层，透明度随时间衰减，形成闪电照亮整个画面的效果。
            flashPaint.alpha = (flash * 120f).toInt().coerceIn(0, 120)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }
}
