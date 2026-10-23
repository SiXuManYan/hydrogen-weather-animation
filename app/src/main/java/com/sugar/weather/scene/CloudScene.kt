package com.sugar.weather.scene

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * 多云 / 阴天场景。
 *
 * 原 App 用带透明度的圆形云团叠出云层，这里沿用同样的思路：日/夜两套颜色与透明度，
 * 云团缓慢缩放呼吸，并按深度做轻微视差。多云的夜间会额外绘制闪烁的星点。
 */
class CloudScene(private val overcast: Boolean) : WeatherScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val xDp: FloatArray
    private val yDp: FloatArray
    private val radiusDp: FloatArray
    private val level: IntArray
    private val dayColor: IntArray
    private val dayAlpha: IntArray
    private val nightColor: IntArray
    private val nightAlpha: IntArray

    private val starX = FloatArray(STAR_COUNT)
    private val starY = FloatArray(STAR_COUNT)
    private val starPhase = FloatArray(STAR_COUNT)

    private var width = 0
    private var height = 0
    private var density = 1f
    private var pitchFactor = 0f
    private var rollFactor = 0f
    private var time = 0f

    init {
        // 云团参数取自原 App：x/y 为 dp 坐标，radiusDp 为圆形云团半径。
        // 多云场景第 3 个云团是黄色太阳圆盘，夜间会变成淡黄白色。
        if (overcast) {
            xDp = floatArrayOf(333f, 184f, 333f, 300f, 96f, -11f, 297f, 10f, 366f)
            yDp = floatArrayOf(73f, -16f, -200f, -116f, -86f, -110f, -162f, -230f, -231.33333f)
            radiusDp = floatArrayOf(91f, 158f, 133f, 200f, 200f, 200f, 216f, 216f, 233f)
            level = intArrayOf(4, 4, 6, 4, 4, 5, 6, 5, 5)
            dayColor = intArrayOf(
                rgb(105, 123, 134), rgb(105, 123, 134), rgb(105, 123, 134),
                rgb(255, 255, 255), rgb(255, 255, 255), rgb(122, 140, 152),
                rgb(90, 108, 120), rgb(255, 255, 255), rgb(221, 226, 229)
            )
            dayAlpha = intArrayOf(102, 102, 102, 102, 153, 102, 102, 153, 204)
            nightColor = intArrayOf(
                rgb(27, 36, 42), rgb(37, 55, 76), rgb(74, 92, 103),
                rgb(134, 148, 150), rgb(72, 94, 106), rgb(28, 48, 60),
                rgb(59, 78, 88), rgb(99, 118, 120), rgb(92, 111, 119)
            )
            nightAlpha = intArrayOf(170, 51, 178, 127, 178, 102, 178, 178, 255)
        } else {
            xDp = floatArrayOf(333f, 184f, 250f, 333f, 300f, 96f, -11f, 297f, 10f)
            yDp = floatArrayOf(73f, -16f, 93f, -200f, -116f, -86f, -110f, -162f, -230f)
            radiusDp = floatArrayOf(91f, 158f, 50f, 133f, 200f, 200f, 200f, 216f, 216f)
            level = intArrayOf(4, 4, 4, 6, 4, 4, 5, 6, 5)
            dayColor = intArrayOf(
                rgb(255, 255, 255), rgb(255, 255, 255), rgb(255, 210, 0),
                rgb(255, 255, 255), rgb(255, 255, 255), rgb(255, 255, 255),
                rgb(255, 255, 255), rgb(255, 255, 255), rgb(255, 255, 255)
            )
            dayAlpha = intArrayOf(102, 102, 204, 102, 102, 153, 102, 102, 153)
            nightColor = intArrayOf(
                rgb(25, 48, 78), rgb(40, 74, 118), rgb(251, 255, 192),
                rgb(48, 87, 130), rgb(40, 74, 118), rgb(39, 71, 107),
                rgb(12, 35, 65), rgb(57, 103, 142), rgb(69, 122, 156)
            )
            nightAlpha = intArrayOf(102, 102, 255, 102, 178, 178, 102, 153, 204)
        }

        val random = Random(20240517)
        // 星点只出现在多云的夜间；坐标用 0~1 的比例保存，适配任意屏幕尺寸。
        for (i in 0 until STAR_COUNT) {
            starX[i] = random.nextFloat()
            starY[i] = random.nextFloat() * 0.82f
            starPhase[i] = random.nextFloat() * 6.28318f
        }
    }

    override fun onSizeChanged(width: Int, height: Int, density: Float) {
        this.width = width
        this.height = height
        this.density = density
    }

    override fun update(dtMs: Long, tilt: Tilt, night: Boolean, elapsedSec: Float) {
        time = elapsedSec
        // rollFactor ∈ [-1, 1]：左右倾斜程度。
        // pitchFactor ∈ [-1, 1]：0 表示竖直握持，-1 表示完全放平，+1 表示完全翻转。
        rollFactor = tilt.rollDeg.coerceIn(-90f, 90f) / 90f
        pitchFactor = ((abs(tilt.pitchDeg) - 90f) / 90f).coerceIn(-1f, 1f)
    }

    override fun draw(canvas: Canvas, night: Boolean) {
        if (width == 0 || height == 0) return
        for (i in xDp.indices) {
            // 半径越大的云团视为离镜头越近，视差和呼吸幅度略大，叠加后更有层次。
            val depth = 0.55f + radiusDp[i] / 500f
            val x = xDp[i] * density + rollFactor * 42f * density * depth
            val y = (yDp[i] + BASE_Y_SHIFT_DP) * density +
                pitchFactor * 74f * density * depth
            // 每朵云独立缓慢呼吸：半径在 1.0~1.1 倍之间往返。
            val radius = radiusDp[i] * density *
                (1.05f + 0.05f * sin(time * 0.55f + i * 0.9f))
            // 完全移出屏幕的云团直接跳过，减少绘制开销。
            if (x + radius < 0f || x - radius > width || y + radius < 0f || y - radius > height) {
                continue
            }
            // 日/夜使用两套颜色和透明度；夜间云团更深、更接近背景色。
            paint.color = if (night) nightColor[i] else dayColor[i]
            paint.alpha = if (night) nightAlpha[i] else dayAlpha[i]
            canvas.drawCircle(x, y, radius, paint)
        }
        // 多云夜间额外叠加星点；阴天不画星星，保持压抑的观感。
        if (night && !overcast) {
            starPaint.color = 0xFFFFFFFF.toInt()
            val starRadius = density * 0.9f
            for (i in 0 until STAR_COUNT) {
                // 每个星点用不同相位做明暗闪烁，避免整片星空同步呼吸。
                val twinkle = 0.5f + 0.5f * sin(time * 1.3f + starPhase[i] * 3f)
                starPaint.alpha = (35 + twinkle * 105).toInt()
                canvas.drawCircle(starX[i] * width, starY[i] * height, starRadius, starPaint)
            }
        }
    }

    private companion object {
        /** 多云夜间显示的星点数量。 */
        const val STAR_COUNT = 50

        /** 云层整体下移的基准距离（dp），避免云全部堆在状态栏附近。 */
        const val BASE_Y_SHIFT_DP = 88f

        /** 把 0~255 的 RGB 分量拼成不透明 ARGB。 */
        fun rgb(r: Int, g: Int, b: Int): Int {
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
