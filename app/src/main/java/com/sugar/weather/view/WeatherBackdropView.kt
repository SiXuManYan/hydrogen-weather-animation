package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.sugar.weather.sensor.TiltSensorController
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.scene.WeatherScene

/**
 * 场景宿主 View。
 *
 * - 使用 Choreographer 按真实帧间隔驱动场景，dt 上限 30ms，避免切后台回来跳变；
 * - 有方向传感器时默认使用真实传感器，没有时自动启用触摸模拟；
 * - onResume/onPause 由 Activity 调用 start/stop，传感器与帧循环同步停止；
 * - 场景切换、昼夜切换和模拟角度重置由 MainActivity 的场景面板调用。
 */
class WeatherBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tilt = Tilt()
    // 传感器回调只更新“传感器姿态”；触摸模拟开启时不会覆盖当前模拟角度。
    private val sensorController = TiltSensorController(context) { pitch, roll ->
        sensorPitch = pitch
        sensorRoll = roll
        if (!simulateTilt) {
            tilt.pitchDeg = pitch
            tilt.rollDeg = roll
        }
    }
    private var sensorPitch = 90f
    private var sensorRoll = 0f
    private var simulatedPitch = 90f
    private var simulatedRoll = 0f

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

    var simulateTilt: Boolean = !sensorController.hasSensor
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                // 从当前真实姿态接管，避免打开模拟时画面突然跳到 90°/0°。
                simulatedPitch = tilt.pitchDeg
                simulatedRoll = tilt.rollDeg
            }
            isClickable = value
            invalidate()
        }

    val hasTiltSensor: Boolean
        get() = sensorController.hasSensor
    val currentScene: SceneType
        get() = sceneType
    val isNight: Boolean
        get() = night
    val isSimulating: Boolean
        get() = simulateTilt
    val tiltPitch: Float
        get() = tilt.pitchDeg
    val tiltRoll: Float
        get() = tilt.rollDeg

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
            // 触摸模拟开启时用模拟角度覆盖真实传感器值。
            if (simulateTilt) {
                tilt.pitchDeg = simulatedPitch
                tilt.rollDeg = simulatedRoll
            }
            elapsedSec += dtMs / 1000f
            scene.update(dtMs, tilt, night, elapsedSec)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isClickable = simulateTilt
    }

    fun setScene(type: SceneType, night: Boolean) {
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

    fun setNight(night: Boolean) {
        if (this.night == night) return
        this.night = night
        invalidate()
    }

    fun setSimulatedTilt(pitch: Float, roll: Float) {
        simulateTilt = true
        simulatedPitch = pitch.coerceIn(0f, 180f)
        simulatedRoll = roll.coerceIn(-90f, 90f)
        tilt.pitchDeg = simulatedPitch
        tilt.rollDeg = simulatedRoll
        invalidate()
    }

    fun resetSimulatedTilt() {
        setSimulatedTilt(90f, 0f)
    }

    fun start() {
        if (running) return
        running = true
        sensorController.start()
        // 第一帧不计算 dt，避免恢复时因时间差过大产生跳变。
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        sensorController.stop()
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
        // 切后台 / 进入分屏不可见时停掉帧循环和传感器，回到前台再恢复。
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
                dragStartPitch = simulatedPitch
                dragStartRoll = simulatedRoll
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging || viewWidth == 0 || viewHeight == 0) return true
                val dx = event.x - dragStartX
                val dy = event.y - dragStartY
                // 整屏宽度对应 180° 横滚，整屏高度对应 180° 俯仰，并限制在有效范围内。
                simulatedRoll = (dragStartRoll + dx / viewWidth * 180f).coerceIn(-90f, 90f)
                simulatedPitch = (dragStartPitch - dy / viewHeight * 180f).coerceIn(0f, 180f)
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
        const val MAX_FRAME_MS = 30L
    }
}
