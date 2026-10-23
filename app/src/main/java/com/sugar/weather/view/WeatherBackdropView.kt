package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.scene.WeatherScene

/**
 * 纯渲染 View。
 *
 * - 只负责 Canvas 绘制、Choreographer 帧循环，以及把触摸拖动换算成模拟姿态；
 * - 不再直接持有传感器，传感器和 UI 状态由 MainViewModel / TiltSensorRepository 管理；
 * - 姿态通过 [setTilt] 由 Activity 收集 StateFlow 后传入，场景切换通过 [setScene] 完成。
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

    private var dragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartPitch = 0f
    private var dragStartRoll = 0f

    /** 是否消费触摸事件来模拟倾斜；由 MainActivity 根据 ViewModel 状态设置。 */
    var simulateTilt: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            isClickable = value
        }

    /** 触摸模拟产生的新姿态回调给 ViewModel，由 ViewModel 统一更新 StateFlow。 */
    var onSimulatedTiltChange: ((pitchDeg: Float, rollDeg: Float) -> Unit)? = null

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!simulateTilt) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                dragStartX = event.x
                dragStartY = event.y
                dragStartPitch = tilt.pitchDeg
                dragStartRoll = tilt.rollDeg
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging || viewWidth == 0 || viewHeight == 0) return true
                val dx = event.x - dragStartX
                val dy = event.y - dragStartY
                // 整屏宽度对应 180° 横滚，整屏高度对应 180° 俯仰，并限制在 Tilt 的有效范围内。
                val newRoll = (dragStartRoll + dx / viewWidth * TOUCH_FULL_SCALE_DEG)
                    .coerceIn(Tilt.MIN_ROLL_DEG, Tilt.MAX_ROLL_DEG)
                val newPitch = (dragStartPitch - dy / viewHeight * TOUCH_FULL_SCALE_DEG)
                    .coerceIn(Tilt.MIN_PITCH_DEG, Tilt.MAX_PITCH_DEG)
                // 本地先更新一次，手指拖动时无需等待 StateFlow 回传；ViewModel 会保存同一份值。
                tilt = Tilt(newPitch, newRoll)
                onSimulatedTiltChange?.invoke(newPitch, newRoll)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        /** 单帧最大时间步长，避免切后台回来时动画跳变。 */
        const val MAX_FRAME_MS = 30L

        /** 手指从屏幕一侧拖到另一侧对应的姿态变化角度。 */
        const val TOUCH_FULL_SCALE_DEG = 180f
    }
}
