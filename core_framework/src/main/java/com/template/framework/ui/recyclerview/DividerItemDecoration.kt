package com.template.framework.ui.recyclerview

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * A color divider for vertical or horizontal RecyclerViews.
 *
 * [startPaddingPx] and [endPaddingPx] apply to the divider's cross axis: left/right for a
 * vertical list and top/bottom for a horizontal list. All dimensions are pixels.
 * - 中文：支持横向和纵向列表；所有尺寸参数单位均为像素。
 *
 * @param color divider color
 * @param thicknessPx divider thickness in pixels
 * @param orientation [RecyclerView.VERTICAL] or [RecyclerView.HORIZONTAL]
 * @property drawLastItem whether a divider is drawn after the final adapter item
 * @param startPaddingPx padding at the cross-axis start edge
 * @param endPaddingPx padding at the cross-axis end edge
 */
class DividerItemDecoration(
    @ColorInt color: Int,
    private val thicknessPx: Int,
    @param:RecyclerView.Orientation private val orientation: Int = RecyclerView.VERTICAL,
    var drawLastItem: Boolean = true,
    private val startPaddingPx: Int = 0,
    private val endPaddingPx: Int = 0,
) : RecyclerView.ItemDecoration() {

    private val divider = ColorDrawable(color)
    private val childBounds = Rect()

    init {
        require(thicknessPx >= 0) { "thicknessPx must not be negative" }
        require(startPaddingPx >= 0) { "startPaddingPx must not be negative" }
        require(endPaddingPx >= 0) { "endPaddingPx must not be negative" }
        require(orientation == RecyclerView.VERTICAL || orientation == RecyclerView.HORIZONTAL) {
            "orientation must be RecyclerView.VERTICAL or RecyclerView.HORIZONTAL"
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.set(0, 0, 0, 0)
        if (thicknessPx == 0 || !shouldDrawAfter(view, parent)) return

        if (orientation == RecyclerView.VERTICAL) {
            outRect.bottom = thicknessPx
        } else {
            outRect.right = thicknessPx
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (thicknessPx == 0 || parent.layoutManager == null || parent.adapter == null) return

        val saveCount = canvas.save()
        if (parent.clipToPadding) {
            canvas.clipRect(
                parent.paddingLeft,
                parent.paddingTop,
                parent.width - parent.paddingRight,
                parent.height - parent.paddingBottom,
            )
        }

        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (!shouldDrawAfter(child, parent)) continue
            parent.getDecoratedBoundsWithMargins(child, childBounds)

            if (orientation == RecyclerView.VERTICAL) {
                drawVerticalDivider(canvas, parent, child)
            } else {
                drawHorizontalDivider(canvas, parent, child)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun shouldDrawAfter(child: View, parent: RecyclerView): Boolean {
        val adapterPosition = parent.getChildAdapterPosition(child)
        val itemCount = parent.adapter?.itemCount ?: return false
        if (adapterPosition == RecyclerView.NO_POSITION || itemCount == 0) return false
        return drawLastItem || adapterPosition < itemCount - 1
    }

    private fun drawVerticalDivider(canvas: Canvas, parent: RecyclerView, child: View) {
        val left = parent.paddingLeft + startPaddingPx
        val right = parent.width - parent.paddingRight - endPaddingPx
        if (right <= left) return

        val top = childBounds.bottom + child.translationY.roundToInt()
        divider.setBounds(left, top, right, top + thicknessPx)
        divider.draw(canvas)
    }

    private fun drawHorizontalDivider(canvas: Canvas, parent: RecyclerView, child: View) {
        val top = parent.paddingTop + startPaddingPx
        val bottom = parent.height - parent.paddingBottom - endPaddingPx
        if (bottom <= top) return

        val left = childBounds.right + child.translationX.roundToInt()
        divider.setBounds(left, top, left + thicknessPx, bottom)
        divider.draw(canvas)
    }
}
