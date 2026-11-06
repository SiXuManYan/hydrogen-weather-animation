package com.sugar.weather.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.sugar.weather.R
import kotlin.math.abs

/**
 * 按起手方向协调纵向刷新、逐小时横滑与外层城市分页，避免轻微斜拖提前切走页面。
 *
 * @param context 当前页面的主题上下文。
 * @param attrs XML 中声明的刷新布局属性。
 */
class WeatherSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    /** 可独立横滑的逐小时列表；只有在该区域起手且列表仍能滚动时才保留横向手势。 */
    var horizontalScrollChild: View? = null

    /** 超过系统阈值后才锁定方向，保持点击和小幅手指抖动的原有行为。 */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** 复用列表在刷新布局坐标系中的命中区域。 */
    private val childBounds = Rect()

    /** 本次手势的横向起点，用于锁定方向。 */
    private var downX = 0f

    /** 本次手势的纵向起点，用于锁定方向。 */
    private var downY = 0f

    /** 上一帧横坐标，用于判断小时列表当前滚动方向。 */
    private var lastX = 0f

    /** 手势是否从逐小时列表开始，滑出列表后仍沿用起手时的归属。 */
    private var startedInHorizontalChild = false

    /** 一次拖动只选择一个方向，避免接近 45 度时反复切换父子拦截。 */
    private var axis = GestureAxis.UNDECIDED

    init {
        setColorSchemeColors(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        )
    }

    /** 刷新圈不跟随内容 padding，显式避让状态栏与头部；重新布局时保留进行中状态。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val start = paddingTop - progressCircleDiameter
        if (progressViewStartOffset != start) {
            val refreshing = isRefreshing
            setProgressViewOffset(false, start, paddingTop + resources.getDimensionPixelSize(R.dimen.space_medium))
            isRefreshing = refreshing
        }
    }

    /** 在祖先下一次拦截前确定归属；结束和取消都释放分页锁。 */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                axis = GestureAxis.UNDECIDED
                startedInHorizontalChild = horizontalScrollChild?.let { child ->
                    child.getDrawingRect(childBounds)
                    offsetDescendantRectToMyCoords(child, childBounds)
                    childBounds.contains(event.x.toInt(), event.y.toInt())
                } == true
            }

            MotionEvent.ACTION_MOVE -> if (axis == GestureAxis.UNDECIDED) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (maxOf(dx, dy) > touchSlop) {
                    axis = if (dx > dy) GestureAxis.HORIZONTAL else GestureAxis.VERTICAL
                }
            }
        }

        val keepGesture = when (axis) {
            GestureAxis.UNDECIDED, GestureAxis.VERTICAL -> true
            GestureAxis.HORIZONTAL -> startedInHorizontalChild &&
                horizontalScrollChild?.canScrollHorizontally(
                    if (event.x - lastX > 0f) -1 else 1
                ) == true
        }
        routeGesture(keepGesture)
        val handled = super.dispatchTouchEvent(event)
        // 子控件也会改变拦截标志，派发后再同步一次，保证纵向拖动不会交给 ViewPager2。
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            horizontalScrollChild?.parent?.requestDisallowInterceptTouchEvent(false)
            parent?.requestDisallowInterceptTouchEvent(false)
        } else {
            routeGesture(keepGesture)
        }
        lastX = event.x
        return handled
    }

    /** 横向手势交给小时列表或城市分页，不允许刷新控件按纵向分量误拦截。 */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        axis != GestureAxis.HORIZONTAL && super.onInterceptTouchEvent(event)

    /** 逐时横滑时同时保护中间的 NestedScrollView；纵向拖动保留其嵌套滚动链。 */
    private fun routeGesture(keepGesture: Boolean) {
        if (startedInHorizontalChild) {
            horizontalScrollChild?.parent?.requestDisallowInterceptTouchEvent(
                keepGesture && axis != GestureAxis.VERTICAL
            )
        }
        parent?.requestDisallowInterceptTouchEvent(keepGesture)
    }

    /** 未超过阈值时先保留手势，之后锁定为水平或垂直方向。 */
    private enum class GestureAxis { UNDECIDED, HORIZONTAL, VERTICAL }
}
