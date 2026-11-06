package com.template.framework.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.template.framework.R
import kotlin.math.max

/**
 * A small flow layout for arbitrary child views.
 *
 * Children keep adapter/XML order. In RTL, the first child starts at the right edge. Children
 * beyond [maxRows] are measured but laid out at zero size so stale bounds cannot remain visible.
 * - 中文：子 View 按顺序自动换行，支持 RTL 和最大显示行数。
 *
 * @param context view context
 * @param attrs optional XML attributes
 * @param defStyleAttr default style attribute
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    /** Horizontal gap between adjacent children, in pixels; negative values normalize to zero. */
    var horizontalSpacing: Int = 0
        set(value) {
            val normalized = value.coerceAtLeast(0)
            if (field != normalized) {
                field = normalized
                requestLayout()
            }
        }

    /** Vertical gap between rows, in pixels; negative values normalize to zero. */
    var verticalSpacing: Int = 0
        set(value) {
            val normalized = value.coerceAtLeast(0)
            if (field != normalized) {
                field = normalized
                requestLayout()
            }
        }

    /** Maximum visible row count; zero hides all rows and negative values normalize to zero. */
    var maxRows: Int = Int.MAX_VALUE
        set(value) {
            val normalized = value.coerceAtLeast(0)
            if (field != normalized) {
                field = normalized
                requestLayout()
            }
        }

    private val rows = mutableListOf<Row>()
    private val overflowChildren = mutableListOf<View>()

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.FlowLayout,
            defStyleAttr,
            0,
        )
        try {
            horizontalSpacing = typedArray.getDimensionPixelSize(
                R.styleable.FlowLayout_fw_horizontalSpacing,
                0,
            )
            verticalSpacing = typedArray.getDimensionPixelSize(
                R.styleable.FlowLayout_fw_verticalSpacing,
                0,
            )
            maxRows = typedArray.getInt(
                R.styleable.FlowLayout_fw_maxRows,
                Int.MAX_VALUE,
            )
        } finally {
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        rows.clear()
        overflowChildren.clear()

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val availableWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            (widthSize - paddingLeft - paddingRight).coerceAtLeast(0)
        }

        var overflowStarted = maxRows == 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) {
                continue
            }

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)

            if (overflowStarted) {
                overflowChildren += child
                continue
            }

            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth +
                params.marginStart + params.marginEnd
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin

            if (rows.isEmpty()) {
                rows += Row()
            }

            var row = rows.last()
            val widthWithChild = if (row.children.isEmpty()) {
                childWidth
            } else {
                row.width + horizontalSpacing + childWidth
            }

            if (row.children.isNotEmpty() && widthWithChild > availableWidth) {
                if (rows.size >= maxRows) {
                    overflowStarted = true
                    overflowChildren += child
                    continue
                }
                row = Row()
                rows += row
            }

            row.add(child, childWidth, childHeight, horizontalSpacing)
        }

        val contentWidth = rows.maxOfOrNull(Row::width) ?: 0
        val contentHeight = rows.sumOf(Row::height) +
            verticalSpacing * (rows.size - 1).coerceAtLeast(0)

        val desiredWidth = max(
            suggestedMinimumWidth,
            contentWidth + paddingLeft + paddingRight,
        )
        val desiredHeight = max(
            suggestedMinimumHeight,
            contentHeight + paddingTop + paddingBottom,
        )

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var rowTop = paddingTop

        rows.forEach { row ->
            var cursor = if (isRtl) width - paddingRight else paddingLeft

            row.children.forEach { child ->
                val params = child.layoutParams as MarginLayoutParams
                val startMargin = params.marginStart
                val endMargin = params.marginEnd
                val childTop = rowTop + params.topMargin

                if (isRtl) {
                    val childRight = cursor - startMargin
                    val childLeft = childRight - child.measuredWidth
                    child.layout(
                        childLeft,
                        childTop,
                        childRight,
                        childTop + child.measuredHeight,
                    )
                    cursor = childLeft - endMargin - horizontalSpacing
                } else {
                    val childLeft = cursor + startMargin
                    val childRight = childLeft + child.measuredWidth
                    child.layout(
                        childLeft,
                        childTop,
                        childRight,
                        childTop + child.measuredHeight,
                    )
                    cursor = childRight + endMargin + horizontalSpacing
                }
            }

            rowTop += row.height + verticalSpacing
        }

        overflowChildren.forEach { child ->
            child.layout(0, 0, 0, 0)
        }
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        requestLayout()
    }

    override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
    )

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(params: LayoutParams): LayoutParams =
        if (params is MarginLayoutParams) MarginLayoutParams(params) else MarginLayoutParams(params)

    override fun checkLayoutParams(params: LayoutParams): Boolean = params is MarginLayoutParams

    private class Row {
        val children = mutableListOf<View>()
        var width: Int = 0
            private set
        var height: Int = 0
            private set

        fun add(child: View, childWidth: Int, childHeight: Int, spacing: Int) {
            if (children.isNotEmpty()) {
                width += spacing
            }
            children += child
            width += childWidth
            height = max(height, childHeight)
        }
    }
}
