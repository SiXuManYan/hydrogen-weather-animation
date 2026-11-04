package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.sugar.weather.R
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.scene.WeatherScene

/**
 * 城市卡小动画背景(DESIGN.md §6.5/§10.2,WeatherBackdropView 的迷你版,原 View 零改动):
 *
 * - 内部持有独立的 `SceneType.createScene()` 实例,程序化绘制与全屏背景同一套场景;
 * - 粒子密度按卡片面积缩放:把 0.35 倍的 density 传给 `scene.onSizeChanged`,
 *   场景内所有按 dp 计算的粒子尺寸/速度随之缩小(§6.5「密度系数 0.35」);
 * - Choreographer 帧循环按 24fps 节流(卡片数量多,不需要 60fps);
 * - 自行按 [R.dimen.city_card_radius] 裁切圆角,不依赖外层 CardView 的裁切行为。
 *
 * 纯渲染 View:场景类型/昼夜由 Adapter 经 [setScene] 传入,不持有任何业务对象。
 */
class WeatherCardBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sceneType = SceneType.SUNNY
    private var night = false
    private var scene: WeatherScene = sceneType.createScene()

    private var elapsedSec = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var attached = false

    private val clipPath = Path()
    private var cornerRadius = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            // 每帧都重新排队,但只有距上一帧 ≥ 24fps 间隔时才推进场景并重绘。
            Choreographer.getInstance().postFrameCallback(this)
            if (lastFrameNanos != 0L && frameTimeNanos - lastFrameNanos < FRAME_INTERVAL_NANOS) {
                return
            }
            var dtMs = 0L
            if (lastFrameNanos != 0L) {
                dtMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
                if (dtMs > MAX_FRAME_MS) dtMs = MAX_FRAME_MS
                if (dtMs < 0L) dtMs = 0L
            }
            lastFrameNanos = frameTimeNanos
            elapsedSec += dtMs / 1000f
            // 卡片不参与倾斜视差,统一用中性姿态。
            scene.update(dtMs, Tilt(), night, elapsedSec)
            invalidate()
        }
    }

    /** 切换天气/昼夜;相同组合直接返回,避免列表重绑时重置动画。 */
    fun setScene(type: SceneType, night: Boolean) {
        if (sceneType == type && this.night == night) return
        sceneType = type
        this.night = night
        scene = type.createScene()
        elapsedSec = 0f
        if (width > 0 && height > 0) {
            scene.onSizeChanged(width, height, scaledDensity())
        }
        invalidate()
    }

    /** 暂停帧循环(页面不可见时)。 */
    fun pause() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /** 恢复帧循环。 */
    fun resume() {
        if (running || !attached || windowVisibility != VISIBLE) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        attached = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Activity 切后台时所有卡片一起暂停,回前台再恢复。
        if (visibility == VISIBLE) resume() else pause()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cornerRadius == 0f) {
            cornerRadius = resources.getDimension(R.dimen.city_card_radius)
        }
        clipPath.reset()
        clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        scene.onSizeChanged(w, h, scaledDensity())
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val save = canvas.save()
        canvas.clipPath(clipPath)
        // 背景色由场景类型提供,与全屏 WeatherBackdropView 同一套配色。
        canvas.drawColor(sceneType.background(night))
        scene.draw(canvas, night)
        canvas.restoreToCount(save)
    }

    /** 场景内几何量按 dp 换算时统一乘的缩放密度(§6.5 粒子密度系数 0.35)。 */
    private fun scaledDensity(): Float =
        resources.displayMetrics.density * DENSITY_SCALE

    private companion object {
        /** 粒子密度缩放系数(§6.5/§10.2)。 */
        const val DENSITY_SCALE = 0.35f

        /** 帧率上限 24fps(§6.5)。 */
        const val FRAME_RATE_FPS = 24L

        /** 24fps 的帧间隔(纳秒)。 */
        const val FRAME_INTERVAL_NANOS = 1_000_000_000L / FRAME_RATE_FPS

        /** 单帧最大时间步长,避免切后台回来时动画跳变(24fps 正常步长约 42ms)。 */
        const val MAX_FRAME_MS = 100L
    }
}
