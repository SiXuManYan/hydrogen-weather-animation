package com.sugar.weather.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.sugar.weather.R
import com.sugar.weather.scene.SceneType
import com.sugar.weather.scene.Tilt
import com.sugar.weather.scene.WeatherScene

/**
 * 城市卡小动画背景，复用全屏场景并单独限制卡片的绘制预算。
 *
 * - 每张卡持有独立场景，雨雪、霾和沙尘的粒子数量按卡片相对屏幕面积缩减；
 * - 显示密度仍乘 0.35，以保留卡片的小尺寸粒子与运动速度；
 * - 所有卡片共享一个最高 24fps 的时钟，只更新实际可见的卡片；
 * - 自行按 [R.dimen.city_card_radius] 裁切圆角，不依赖外层 CardView 的裁切行为。
 *
 * 纯渲染 View:场景类型/昼夜由 Adapter 经 [setScene] 传入,不持有任何业务对象。
 */
class WeatherCardBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 当前场景身份用于列表重绑去重，避免天气未变时重建粒子。 */
    private var sceneType = SceneType.SUNNY
    /** 昼夜只改变场景配色，不改变共享帧时钟。 */
    private var night = false
    /** 首次测量前先使用保守预算，测量后按实际卡片面积重建。 */
    private var particleScale = MIN_PARTICLE_SCALE
    /** 卡片自己的粒子和运动状态，不与其他城市串用。 */
    private var scene: WeatherScene = sceneType.createScene(particleScale = particleScale)

    /** 仅累计实际可见时的时间，卡片滚出屏幕后不会继续推进动画。 */
    private var elapsedSec = 0f
    /** 上次可见帧时刻，恢复时清零以防粒子跳跃。 */
    private var lastFrameNanos = 0L
    /** 是否已注册到共享时钟。 */
    private var running = false
    /** 防止尚未挂载的 View 注册帧循环。 */
    private var attached = false

    /** 圆角裁切路径只在尺寸变化时更新。 */
    private val clipPath = Path()
    /** 复用可见区域，逐帧检查不创建 Rect。 */
    private val visibleRect = Rect()
    /** 缓存主题圆角尺寸。 */
    private var cornerRadius = 0f

    /** 由共享时钟调用；裁切区域外的已挂载卡片也不计算粒子。 */
    internal fun onAnimationFrame(frameTimeNanos: Long) {
        if (!running || !isShown || !getLocalVisibleRect(visibleRect)) {
            lastFrameNanos = 0L
            return
        }
        val dtMs = if (lastFrameNanos == 0L) 0L else
            ((frameTimeNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, MAX_FRAME_MS)
        lastFrameNanos = frameTimeNanos
        elapsedSec += dtMs / 1000f
        // 中性姿态不可变，所有卡片复用，避免逐卡逐帧分配。
        scene.update(dtMs = dtMs, tilt = NEUTRAL_TILT, night = night, elapsedSec = elapsedSec)
        invalidate()
    }

    /** 切换天气/昼夜;相同组合直接返回,避免列表重绑时重置动画。 */
    fun setScene(type: SceneType, night: Boolean) {
        if (sceneType == type && this.night == night) return
        sceneType = type
        this.night = night
        scene = type.createScene(particleScale = particleScale)
        elapsedSec = 0f
        if (width > 0 && height > 0) {
            scene.onSizeChanged(width = width, height = height, density = scaledDensity())
        }
        invalidate()
    }

    /** 暂停帧循环(页面不可见时)。 */
    fun pause() {
        if (!running) return
        running = false
        CardFrameClock.unregister(this)
    }

    /** 恢复帧循环。 */
    fun resume() {
        if (running || !attached || windowVisibility != VISIBLE || !isShown) return
        running = true
        lastFrameNanos = 0L
        CardFrameClock.register(this)
    }

    /** 挂载后才允许共享时钟持有本卡片。 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        resume()
    }

    /** 离开 RecyclerView 时立即注销，防止时钟持有脱离窗口的卡片。 */
    override fun onDetachedFromWindow() {
        pause()
        attached = false
        super.onDetachedFromWindow()
    }

    /** 整个窗口不可见时停止调度。 */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Activity 切后台时所有卡片一起暂停,回前台再恢复。
        if (visibility == VISIBLE) resume() else pause()
    }

    /** 卡片或父容器隐藏时同样注销，避免只检查窗口可见性。 */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (attached && isShown) resume() else pause()
    }

    /** 重算圆角及面积预算；全屏仍使用默认完整预算。 */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cornerRadius == 0f) {
            cornerRadius = resources.getDimension(R.dimen.city_card_radius)
        }
        clipPath.reset()
        clipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, Path.Direction.CW)
        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toDouble() * metrics.heightPixels
        val areaScale = if (screenArea > 0.0) (w.toDouble() * h / screenArea).toFloat()
            else MIN_PARTICLE_SCALE
        val newScale = areaScale.coerceIn(MIN_PARTICLE_SCALE, MAX_PARTICLE_SCALE)
        if (newScale != particleScale) {
            particleScale = newScale
            scene = sceneType.createScene(particleScale = particleScale)
            elapsedSec = 0f
            lastFrameNanos = 0L
        }
        scene.onSizeChanged(width = w, height = h, density = scaledDensity())
    }

    /** 仅绘制一次不透明底色，再叠加预算内的场景内容。 */
    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val save = canvas.save()
        canvas.clipPath(clipPath)
        // 背景色由场景类型提供,与全屏 WeatherBackdropView 同一套配色。
        canvas.drawColor(sceneType.background(night))
        scene.draw(canvas, night)
        canvas.restoreToCount(save)
    }

    /** 场景内几何量按 dp 换算时统一乘的缩放密度。 */
    private fun scaledDensity(): Float =
        resources.displayMetrics.density * DENSITY_SCALE

    private companion object {
        /** 几何尺寸和速度的缩放，与粒子数量预算分开。 */
        const val DENSITY_SCALE = 0.35f

        /** 极小卡片也保留可辨认的粒子；最大预算避免宽屏卡片恢复全屏开销。 */
        const val MIN_PARTICLE_SCALE = 0.12f

        /** 卡片粒子最多使用全屏的四成。 */
        const val MAX_PARTICLE_SCALE = 0.4f

        /** 所有卡片共用同一个不可变中性姿态。 */
        val NEUTRAL_TILT = Tilt()

        /** 单帧最大时间步长,避免切后台回来时动画跳变(24fps 正常步长约 42ms)。 */
        const val MAX_FRAME_MS = 100L
    }
}

/** 主线程上的城市卡共享帧时钟，只在有可用卡片时保留一个延迟帧回调。 */
private object CardFrameClock : Choreographer.FrameCallback {
    /** View 在脱离窗口或隐藏时注销，集合不会持有已销毁页面的卡片。 */
    private val cards = LinkedHashSet<WeatherCardBackdropView>()

    /** 延迟后对齐下一次 vsync，不再为每张卡在每个 vsync 排队。 */
    private const val FRAME_DELAY_MS = 42L

    /** 首张卡进入时启动一个共享回调。 */
    fun register(card: WeatherCardBackdropView) {
        if (cards.add(card) && cards.size == 1) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** 最后一张卡离开时释放共享回调。 */
    fun unregister(card: WeatherCardBackdropView) {
        cards.remove(card)
        if (cards.isEmpty()) Choreographer.getInstance().removeFrameCallback(this)
    }

    /** 同一次帧回调推进全部可见卡片，集合只在主线程生命周期回调中修改。 */
    override fun doFrame(frameTimeNanos: Long) {
        cards.forEach { it.onAnimationFrame(frameTimeNanos) }
        if (cards.isNotEmpty()) {
            Choreographer.getInstance().postFrameCallbackDelayed(this, FRAME_DELAY_MS)
        }
    }
}
