package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.sugar.weather.data.model.DailyForecast
import kotlin.math.roundToInt

/**
 * 6 天高低双温度曲线(DESIGN.md §6.3,对齐截图 005332)。
 *
 * 与日报条 RecyclerView 逐像素对齐:列宽 = 宽度 / 6,曲线点在列中心
 * `(i + 0.5) * width / 6`,日报条由 GridLayoutManager(span=6) 等分,两者中心一致。
 *
 * 纯渲染 View:只消费 [DailyForecast] 数值,不接触 Repository/ViewModel(§8.1 第 1 条)。
 * 绘制顺序:两线之间的垂直渐变填充带(上 #40FFFFFF → 下 #08FFFFFF,§6.3)
 * → 高/低细白折线(二阶贝塞尔经相邻点中点平滑,延伸至屏幕两端)→ 圆点 → 点上/下方数值标签。
 */
class WeatherTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = CURVE_WIDTH_DP * density
        color = CURVE_COLOR
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = DOT_COLOR
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LABEL_COLOR
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP, resources.displayMetrics
        )
    }

    private val highPath = Path()
    private val lowPath = Path()
    private val fillPath = Path()

    private var days: List<DailyForecast> = emptyList()
    private var xs = FloatArray(0)
    private var highYs = FloatArray(0)
    private var lowYs = FloatArray(0)
    private var highLabels: List<String> = emptyList()
    private var lowLabels: List<String> = emptyList()

    /** 供日报条等分对齐使用的列中心(与 onDraw 完全一致)。 */
    fun columnCenters(): FloatArray = xs.copyOf()

    /** 输入 daily[0..5];空列表清空绘制。数据/尺寸变化后统一重算路径与渐变。 */
    fun setDays(days: List<DailyForecast>) {
        this.days = days.take(DAY_COUNT)
        recompute()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recompute()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val measured = if (heightMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            suggestedMinimumHeight
        }
        setMeasuredDimension(width, measured.coerceAtLeast(suggestedMinimumHeight))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (xs.isEmpty()) return
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(highPath, curvePaint)
        canvas.drawPath(lowPath, curvePaint)
        val dotRadius = DOT_RADIUS_DP * density
        for (i in xs.indices) {
            canvas.drawCircle(xs[i], highYs[i], dotRadius, dotPaint)
            canvas.drawCircle(xs[i], lowYs[i], dotRadius, dotPaint)
        }
        for (i in xs.indices) {
            drawLabel(canvas, highLabels[i], xs[i], highYs[i] - LABEL_GAP_ABOVE_DP * density)
            drawLabel(canvas, lowLabels[i], xs[i], lowYs[i] + LABEL_GAP_BELOW_DP * density)
        }
    }

    // ———————————————————————— 内部实现 ————————————————————————

    private fun recompute() {
        val list = days
        highPath.reset()
        lowPath.reset()
        fillPath.reset()
        fillPaint.shader = null
        if (list.isEmpty() || width <= 0 || height <= 0) {
            xs = FloatArray(0)
            highYs = FloatArray(0)
            lowYs = FloatArray(0)
            highLabels = emptyList()
            lowLabels = emptyList()
            return
        }
        val n = list.size
        val maxOfMax = list.maxOf { it.tempMaxC }
        val minOfMin = list.minOf { it.tempMinC }
        val range = (maxOfMax - minOfMin).takeIf { it > TEMP_EPSILON } ?: 1.0
        val topPad = LABEL_PAD_TOP_DP * density
        val bottomPad = LABEL_PAD_BOTTOM_DP * density
        val areaHeight = (height - topPad - bottomPad).coerceAtLeast(0f)

        xs = FloatArray(n) { i -> (i + 0.5f) * width / n }
        highYs = FloatArray(n) { i -> topPad + ((maxOfMax - list[i].tempMaxC) / range * areaHeight).toFloat() }
        lowYs = FloatArray(n) { i -> topPad + ((maxOfMax - list[i].tempMinC) / range * areaHeight).toFloat() }
        highLabels = list.map { "${it.tempMaxC.roundToInt()}°" }
        lowLabels = list.map { "${it.tempMinC.roundToInt()}°" }

        buildStroke(highPath, highYs)
        buildStroke(lowPath, lowYs)
        buildFill()
        // 渐变随实际曲线区高度生成,重算时替换,不在 onDraw 内分配(§WeatherScene 同纪律)
        fillPaint.shader = LinearGradient(
            0f, topPad, 0f, (height - bottomPad).coerceAtLeast(topPad + 1f),
            FILL_TOP_COLOR, FILL_BOTTOM_COLOR, Shader.TileMode.CLAMP
        )
    }

    /** 单条曲线:从左边缘水平延伸进首点,经中点二阶贝塞尔平滑,再水平延伸出右边缘。 */
    private fun buildStroke(path: Path, ys: FloatArray) {
        val n = ys.size
        path.moveTo(0f, ys[0])
        path.lineTo(xs[0], ys[0])
        for (i in 1 until n) {
            path.quadTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2f, (ys[i - 1] + ys[i]) / 2f)
        }
        path.lineTo(xs[n - 1], ys[n - 1])
        path.lineTo(width.toFloat(), ys[n - 1])
    }

    /** 填充带:高温曲线前行 + 低温曲线逆行 + 两侧边缘闭合(§6.3)。 */
    private fun buildFill() {
        val n = xs.size
        fillPath.moveTo(0f, highYs[0])
        fillPath.lineTo(xs[0], highYs[0])
        for (i in 1 until n) {
            fillPath.quadTo(
                xs[i - 1], highYs[i - 1],
                (xs[i - 1] + xs[i]) / 2f, (highYs[i - 1] + highYs[i]) / 2f
            )
        }
        fillPath.lineTo(width.toFloat(), highYs[n - 1])
        fillPath.lineTo(width.toFloat(), lowYs[n - 1])
        for (i in n - 1 downTo 1) {
            fillPath.quadTo(
                xs[i], lowYs[i],
                (xs[i] + xs[i - 1]) / 2f, (lowYs[i] + lowYs[i - 1]) / 2f
            )
        }
        fillPath.lineTo(xs[0], lowYs[0])
        fillPath.lineTo(0f, lowYs[0])
        fillPath.close()
    }

    /** 标签居中于点上方/下方,越出边缘时向内收(截图首尾 16°/14°/3°/9° 仍然可见)。 */
    private fun drawLabel(canvas: Canvas, text: String, x: Float, baselineY: Float) {
        val half = labelPaint.measureText(text) / 2f
        canvas.drawText(text, x.coerceIn(half, (width - half).coerceAtLeast(half)), baselineY, labelPaint)
    }

    private companion object {
        const val DAY_COUNT = 6

        const val CURVE_COLOR = 0xCCFFFFFF.toInt()
        const val DOT_COLOR = 0xFFFFFFFF.toInt()
        const val LABEL_COLOR = 0xE6FFFFFF.toInt()
        const val FILL_TOP_COLOR = 0x40FFFFFF
        const val FILL_BOTTOM_COLOR = 0x08FFFFFF

        const val CURVE_WIDTH_DP = 1.5f
        const val DOT_RADIUS_DP = 2.2f
        const val LABEL_TEXT_SP = 15f

        const val LABEL_PAD_TOP_DP = 26f
        const val LABEL_PAD_BOTTOM_DP = 30f
        const val LABEL_GAP_ABOVE_DP = 11f
        const val LABEL_GAP_BELOW_DP = 27f

        const val TEMP_EPSILON = 1e-6
    }
}
