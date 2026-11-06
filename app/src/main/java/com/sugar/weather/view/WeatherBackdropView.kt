package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.scene.WeatherScene

/**
 * 纯渲染 View。
 *
 * - 只负责 Canvas 绘制与 Choreographer 帧循环；触摸由主页统一观察；
 * - 不再直接持有传感器，传感器和 UI 状态由 MainViewModel / TiltSensorRepository 管理；
 * - 姿态通过 [setTilt] 由 Activity 收集 StateFlow 后传入，场景切换通过 [setScene] 完成。
 * @param context 提供绘制密度和 View 环境。
 * @param attrs XML 布局传入的 View 属性。
 * @param defStyleAttr 默认样式属性。
 */
class WeatherBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var tilt = Tilt()
    private var sceneType = SceneType.SUNNY
    private var night = false
    private var scene: WeatherScene = sceneType.createScene()

    private var viewWidth = 0
    private var viewHeight = 0
    private var density = resources.displayMetrics.density
    private var elapsedSec = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var attached = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            var dtMs = 0L
            if (lastFrameNanos != 0L) {
                dtMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
                if (dtMs > MAX_FRAME_MS) dtMs = MAX_FRAME_MS
                if (dtMs < 0L) dtMs = 0L
            }
            lastFrameNanos = frameTimeNanos
            elapsedSec += dtMs / 1000f
            scene.update(dtMs, tilt, night, elapsedSec)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * 切换天气/昼夜。相同组合直接返回，避免模拟开关或调试开关变化时重置动画。
     */
    fun setScene(type: SceneType, night: Boolean) {
        if (sceneType == type && this.night == night) return
        sceneType = type
        this.night = night
        scene = type.createScene()
        // 重置场景时间，让颜色呼吸、粒子分布等从稳定状态开始。
        elapsedSec = 0f
        if (viewWidth > 0 && viewHeight > 0) {
            scene.onSizeChanged(viewWidth, viewHeight, density)
        }
        invalidate()
    }

    /** 由 Activity 从 ViewModel 的 StateFlow 写入；帧循环每帧读取最新姿态。 */
    fun setTilt(value: Tilt) {
        tilt = value
    }

    /** 启动 Canvas 帧循环；传感器生命周期由 ViewModel 管理，不在这里处理。 */
    fun start() {
        if (running) return
        running = true
        // 第一帧不计算 dt，避免恢复时因时间差过大产生跳变。
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /** 停止 Canvas 帧循环；传感器由 ViewModel 独立停止。 */
    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (windowVisibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        attached = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // 切后台 / 进入分屏不可见时停掉帧循环，回到前台再恢复。
        if (visibility == VISIBLE && attached) {
            start()
        } else {
            stop()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        density = resources.displayMetrics.density
        scene.onSizeChanged(w, h, density)
    }

    override fun onDraw(canvas: Canvas) {
        // 背景色由场景类型提供，具体天气图形全部程序化绘制，不加载位图。
        canvas.drawColor(sceneType.background(night))
        scene.draw(canvas, night)
    }

    private companion object {
        /** 单帧最大时间步长，避免切后台回来时动画跳变。 */
        const val MAX_FRAME_MS = 30L
    }
}
