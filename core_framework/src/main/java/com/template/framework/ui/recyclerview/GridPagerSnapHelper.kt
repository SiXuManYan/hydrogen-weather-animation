package com.template.framework.ui.recyclerview

import android.graphics.PointF
import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Snaps a RecyclerView to the first adapter position of a fixed-size grid page.
 *
 * The adapter must arrange each page as [rows] x [columns] contiguous items. This helper works
 * with either a horizontally or vertically scrolling LayoutManager.
 * - 中文：将固定行列网格按页吸附，每页数据在 Adapter 中必须连续排列。
 *
 * @property rows number of rows per page; must be positive
 * @property columns number of columns per page; must be positive
 */
class GridPagerSnapHelper(
    val rows: Int,
    val columns: Int,
) : SnapHelper() {

    private val itemsPerPage: Int

    private var attachedRecyclerView: RecyclerView? = null
    private var verticalHelper: OrientationHelper? = null
    private var horizontalHelper: OrientationHelper? = null

    init {
        require(rows > 0) { "rows must be greater than 0" }
        require(columns > 0) { "columns must be greater than 0" }
        require(rows <= Int.MAX_VALUE / columns) { "rows * columns is too large" }
        itemsPerPage = rows * columns
    }

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        super.attachToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
        if (recyclerView == null) {
            verticalHelper = null
            horizontalHelper = null
        }
    }

    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View,
    ): IntArray {
        val horizontalDistance = if (layoutManager.canScrollHorizontally()) {
            distanceToStart(layoutManager, targetView, horizontalHelper(layoutManager))
        } else {
            0
        }
        val verticalDistance = if (layoutManager.canScrollVertically()) {
            distanceToStart(layoutManager, targetView, verticalHelper(layoutManager))
        } else {
            0
        }
        return intArrayOf(horizontalDistance, verticalDistance)
    }

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        return when {
            layoutManager.canScrollVertically() -> {
                findViewClosestToStart(layoutManager, verticalHelper(layoutManager))
            }

            layoutManager.canScrollHorizontally() -> {
                findViewClosestToStart(layoutManager, horizontalHelper(layoutManager))
            }

            else -> null
        }
    }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int,
    ): Int {
        val itemCount = layoutManager.itemCount
        if (itemCount == 0) return RecyclerView.NO_POSITION

        val orientationHelper = when {
            layoutManager.canScrollVertically() -> verticalHelper(layoutManager)
            layoutManager.canScrollHorizontally() -> horizontalHelper(layoutManager)
            else -> return RecyclerView.NO_POSITION
        }
        val startView = findStartView(layoutManager, orientationHelper)
            ?: return RecyclerView.NO_POSITION
        val startPosition = layoutManager.getPosition(startView)
        if (startPosition == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION

        val currentPage = startPosition / itemsPerPage
        val forwardFling = if (layoutManager.canScrollVertically()) {
            velocityY > 0
        } else {
            velocityX > 0
        }
        val reverseLayout = isReverseLayout(layoutManager, itemCount)
        val pageDelta = when {
            !forwardFling -> 0
            reverseLayout -> -1
            else -> 1
        }

        val lastPage = (itemCount - 1) / itemsPerPage
        val targetPage = (currentPage + pageDelta).coerceIn(0, lastPage)
        return targetPage * itemsPerPage
    }

    override fun createScroller(
        layoutManager: RecyclerView.LayoutManager,
    ): RecyclerView.SmoothScroller? {
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) return null
        val recyclerView = attachedRecyclerView ?: return null

        return object : LinearSmoothScroller(recyclerView.context) {
            override fun onTargetFound(
                targetView: View,
                state: RecyclerView.State,
                action: Action,
            ) {
                val currentRecyclerView = attachedRecyclerView ?: return
                if (currentRecyclerView.layoutManager !== layoutManager) return

                val distances = calculateDistanceToFinalSnap(layoutManager, targetView)
                val dx = distances[0]
                val dy = distances[1]
                val duration = calculateTimeForDeceleration(max(abs(dx), abs(dy)))
                if (duration > 0) {
                    action.update(dx, dy, duration, mDecelerateInterpolator)
                }
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
            }

            override fun calculateTimeForScrolling(dx: Int): Int {
                return min(MAX_SCROLL_DURATION_MS, super.calculateTimeForScrolling(dx))
            }
        }
    }

    private fun isReverseLayout(
        layoutManager: RecyclerView.LayoutManager,
        itemCount: Int,
    ): Boolean {
        val vectorProvider =
            layoutManager as? RecyclerView.SmoothScroller.ScrollVectorProvider ?: return false
        val vectorToEnd: PointF =
            vectorProvider.computeScrollVectorForPosition(itemCount - 1) ?: return false
        return if (layoutManager.canScrollVertically()) {
            vectorToEnd.y < 0f
        } else {
            vectorToEnd.x < 0f
        }
    }

    private fun distanceToStart(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View,
        helper: OrientationHelper,
    ): Int {
        return helper.getDecoratedStart(targetView) - containerStart(layoutManager, helper)
    }

    private fun findViewClosestToStart(
        layoutManager: RecyclerView.LayoutManager,
        helper: OrientationHelper,
    ): View? {
        val start = containerStart(layoutManager, helper)
        var closestView: View? = null
        var closestDistance = Int.MAX_VALUE

        for (index in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(index) ?: continue
            val distance = abs(helper.getDecoratedStart(child) - start)
            if (distance < closestDistance) {
                closestDistance = distance
                closestView = child
            }
        }
        return closestView
    }

    private fun findStartView(
        layoutManager: RecyclerView.LayoutManager,
        helper: OrientationHelper,
    ): View? {
        var startView: View? = null
        var smallestStart = Int.MAX_VALUE

        for (index in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(index) ?: continue
            val childStart = helper.getDecoratedStart(child)
            if (childStart < smallestStart) {
                smallestStart = childStart
                startView = child
            }
        }
        return startView
    }

    private fun containerStart(
        layoutManager: RecyclerView.LayoutManager,
        helper: OrientationHelper,
    ): Int {
        return if (layoutManager.clipToPadding) helper.startAfterPadding else 0
    }

    private fun verticalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        return verticalHelper
            ?.takeIf { it.layoutManager === layoutManager }
            ?: OrientationHelper.createVerticalHelper(layoutManager).also { verticalHelper = it }
    }

    private fun horizontalHelper(layoutManager: RecyclerView.LayoutManager): OrientationHelper {
        return horizontalHelper
            ?.takeIf { it.layoutManager === layoutManager }
            ?: OrientationHelper.createHorizontalHelper(layoutManager).also { horizontalHelper = it }
    }

    private companion object {
        const val MILLISECONDS_PER_INCH = 100f
        const val MAX_SCROLL_DURATION_MS = 100
    }
}
