package com.template.framework.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.template.framework.R
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * Canvas-based rating control with whole-star and half-star input.
 *
 * The control supports RTL, accessibility range metadata, state restoration, and read-only mode.
 * Programmatic changes report `fromUser = false` to [OnRatingChangeListener].
 * - 中文：支持整星/半星、RTL、无障碍、状态恢复和只读模式的评分控件。
 *
 * ## Usage
 * ```kotlin
 * ratingView.apply {
 *     stepSize = 0.5f
 *     setOnRatingChangeListener { _, value, fromUser ->
 *         if (fromUser) saveRating(value)
 *     }
 * }
 * ```
 *
 * @param context view context
 * @param attrs optional XML attributes
 * @param defStyleAttr default style attribute
 */
class RatingStarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Receives rating changes from touch input and programmatic assignment. */
    fun interface OnRatingChangeListener {
        /**
         * @param view source rating view
         * @param rating normalized rating in `0..starCount`
         * @param fromUser `true` for touch input and `false` for programmatic updates
         */
        fun onRatingChanged(view: RatingStarView, rating: Float, fromUser: Boolean)
    }

    private var currentRating: Float = 0f

    /** Number of rendered stars; values below one normalize to one. */
    var starCount: Int = DEFAULT_STAR_COUNT
        set(value) {
            val normalized = value.coerceAtLeast(1)
            if (field != normalized) {
                field = normalized
                updateRating(rating, fromUser = false, notify = true)
                requestLayout()
                invalidate()
            }
        }

    /** Current normalized rating, clamped to `0..starCount`. */
    var rating: Float
        get() = currentRating
        set(value) {
            updateRating(value, fromUser = false, notify = true)
        }

    /** Supported values are `0.5` and `1.0`; other values normalize to the nearest step. */
    var stepSize: Float = 1f
        set(value) {
            val normalized = normalizeStep(value)
            if (field != normalized) {
                field = normalized
                updateRating(rating, fromUser = false, notify = true)
                invalidate()
            }
        }

    /** Fill color for the selected portion of each star. */
    @ColorInt
    var activeColor: Int = Color.rgb(255, 183, 0)
        set(value) {
            if (field != value) {
                field = value
                activePaint.color = value
                invalidate()
            }
        }

    /** Fill color for the unselected portion of each star. */
    @ColorInt
    var inactiveColor: Int = Color.rgb(232, 232, 232)
        set(value) {
            if (field != value) {
                field = value
                inactivePaint.color = value
                invalidate()
            }
        }

    /** Outline color around each star. */
    @ColorInt
    var strokeColor: Int = Color.rgb(160, 160, 160)
        set(value) {
            if (field != value) {
                field = value
                strokePaint.color = value
                invalidate()
            }
        }

    /** Star outline width in pixels; non-finite or negative values normalize to zero. */
    var strokeWidth: Float = dp(1f)
        set(value) {
            val normalized = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
            if (field != normalized) {
                field = normalized
                strokePaint.strokeWidth = normalized
                invalidate()
            }
        }

    /** Horizontal spacing between stars in pixels. */
    var starSpacing: Float = dp(4f)
        set(value) {
            val normalized = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
            if (field != normalized) {
                field = normalized
                requestLayout()
                invalidate()
            }
        }

    /** Corner-path radius in pixels; zero keeps sharp star points. */
    var starCornerRadius: Float = 0f
        set(value) {
            val normalized = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
            if (field != normalized) {
                field = normalized
                updatePathEffects()
                invalidate()
            }
        }

    /** Whether touch and accessibility click interaction can change [rating]. */
    var ratingEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                isClickable = value
                isFocusable = value
                invalidate()
            }
        }

    private var ratingChangeListener: OnRatingChangeListener? = null
    private val starPath = Path()
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = inactiveColor
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = activeColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = strokeColor
        strokeWidth = this@RatingStarView.strokeWidth
    }

    private var drawnStarSize = 0f
    private var drawnStartX = 0f

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.RatingStarView,
            defStyleAttr,
            0,
        )
        try {
            starCount = typedArray.getInt(
                R.styleable.RatingStarView_fw_starCount,
                DEFAULT_STAR_COUNT,
            ).coerceAtLeast(1)
            stepSize = normalizeStep(
                typedArray.getFloat(R.styleable.RatingStarView_fw_stepSize, 1f),
            )
            activeColor = typedArray.getColor(
                R.styleable.RatingStarView_fw_activeColor,
                activeColor,
            )
            inactiveColor = typedArray.getColor(
                R.styleable.RatingStarView_fw_inactiveColor,
                inactiveColor,
            )
            strokeColor = typedArray.getColor(
                R.styleable.RatingStarView_fw_strokeColor,
                strokeColor,
            )
            strokeWidth = typedArray.getDimension(
                R.styleable.RatingStarView_fw_strokeWidth,
                strokeWidth,
            )
            starSpacing = typedArray.getDimension(
                R.styleable.RatingStarView_fw_starSpacing,
                starSpacing,
            )
            starCornerRadius = typedArray.getDimension(
                R.styleable.RatingStarView_fw_starCornerRadius,
                starCornerRadius,
            )
            ratingEnabled = typedArray.getBoolean(
                R.styleable.RatingStarView_fw_ratingEnabled,
                true,
            )
            rating = typedArray.getFloat(
                R.styleable.RatingStarView_fw_rating,
                0f,
            )
        } finally {
            typedArray.recycle()
        }

        isClickable = ratingEnabled
        isFocusable = ratingEnabled
        updatePathEffects()
    }

    /** Replaces the rating listener; pass `null` to stop receiving changes. */
    fun setOnRatingChangeListener(listener: OnRatingChangeListener?) {
        ratingChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultStarSize = dp(DEFAULT_STAR_SIZE_DP)
        val desiredWidth = paddingLeft + paddingRight +
            (defaultStarSize * starCount + starSpacing * (starCount - 1)).toInt()
        val desiredHeight = paddingTop + paddingBottom + defaultStarSize.toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        val contentHeight = (height - paddingTop - paddingBottom).toFloat()
        val totalSpacing = starSpacing * (starCount - 1)
        val sizeByWidth = (contentWidth - totalSpacing) / starCount
        val starSize = min(contentHeight, sizeByWidth).coerceAtLeast(0f)
        if (starSize <= 0f) return

        drawnStarSize = starSize
        val groupWidth = starSize * starCount + totalSpacing
        drawnStartX = paddingLeft + (contentWidth - groupWidth) / 2f
        val starTop = paddingTop + (contentHeight - starSize) / 2f
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        for (logicalIndex in 0 until starCount) {
            val starLeft = if (isRtl) {
                drawnStartX + groupWidth - starSize - logicalIndex * (starSize + starSpacing)
            } else {
                drawnStartX + logicalIndex * (starSize + starSpacing)
            }
            buildStarPath(starLeft, starTop, starSize)
            canvas.drawPath(starPath, inactivePaint)

            val activeFraction = (rating - logicalIndex).coerceIn(0f, 1f)
            if (activeFraction > 0f) {
                val saveCount = canvas.save()
                if (isRtl) {
                    canvas.clipRect(
                        starLeft + starSize * (1f - activeFraction),
                        starTop,
                        starLeft + starSize,
                        starTop + starSize,
                    )
                } else {
                    canvas.clipRect(
                        starLeft,
                        starTop,
                        starLeft + starSize * activeFraction,
                        starTop + starSize,
                    )
                }
                canvas.drawPath(starPath, activePaint)
                canvas.restoreToCount(saveCount)
            }

            if (strokeWidth > 0f && Color.alpha(strokeColor) > 0) {
                canvas.drawPath(starPath, strokePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ratingEnabled || !isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isPressed = true
                updateRatingFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateRatingFromTouch(event.x)
                return true
            }

            MotionEvent.ACTION_UP -> {
                updateRatingFromTouch(event.x)
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.RatingBar"
        info.isClickable = ratingEnabled && isEnabled
        AccessibilityNodeInfoCompat.wrap(info).rangeInfo =
            AccessibilityNodeInfoCompat.RangeInfoCompat(
            AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT,
            0f,
            starCount.toFloat(),
            rating,
        )
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()).also { state ->
            state.starCount = starCount
            state.rating = rating
            state.stepSize = stepSize
            state.ratingEnabled = ratingEnabled
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        starCount = state.starCount
        stepSize = state.stepSize
        ratingEnabled = state.ratingEnabled
        updateRating(state.rating, fromUser = false, notify = false)
        requestLayout()
        invalidate()
    }

    private fun updateRatingFromTouch(touchX: Float) {
        if (drawnStarSize <= 0f) return

        val groupWidth = drawnStarSize * starCount + starSpacing * (starCount - 1)
        val distanceFromStart = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            drawnStartX + groupWidth - touchX
        } else {
            touchX - drawnStartX
        }.coerceIn(0f, groupWidth)

        val slotSize = drawnStarSize + starSpacing
        val starIndex = (distanceFromStart / slotSize).toInt().coerceIn(0, starCount - 1)
        val withinSlot = distanceFromStart - starIndex * slotSize
        val fraction = (withinSlot / drawnStarSize).coerceIn(0f, 1f)
        val rawRating = starIndex + fraction
        val selectedRating = if (rawRating <= 0f) {
            stepSize
        } else {
            ceil(rawRating / stepSize) * stepSize
        }
        updateRating(selectedRating, fromUser = true, notify = true)
    }

    private fun updateRating(value: Float, fromUser: Boolean, notify: Boolean) {
        val finiteValue = if (value.isFinite()) value else 0f
        val normalized = quantize(finiteValue.coerceIn(0f, starCount.toFloat()))
        if (currentRating == normalized) return
        currentRating = normalized
        invalidate()
        if (notify) {
            ratingChangeListener?.onRatingChanged(this, normalized, fromUser)
        }
    }

    private fun quantize(value: Float): Float =
        (round(value / stepSize) * stepSize).coerceIn(0f, starCount.toFloat())

    private fun buildStarPath(left: Float, top: Float, size: Float) {
        starPath.reset()
        val centerX = left + size / 2f
        val centerY = top + size / 2f
        val outerRadius = ((size - strokeWidth) / 2f).coerceAtLeast(0f)
        val innerRadius = outerRadius * INNER_RADIUS_RATIO

        for (pointIndex in 0 until STAR_POINT_COUNT) {
            val radius = if (pointIndex % 2 == 0) outerRadius else innerRadius
            val angle = -PI / 2.0 + pointIndex * PI / 5.0
            val x = centerX + cos(angle).toFloat() * radius
            val y = centerY + sin(angle).toFloat() * radius
            if (pointIndex == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
    }

    private fun updatePathEffects() {
        val effect = if (starCornerRadius > 0f) CornerPathEffect(starCornerRadius) else null
        activePaint.pathEffect = effect
        inactivePaint.pathEffect = effect
        strokePaint.pathEffect = effect
    }

    private fun normalizeStep(value: Float): Float = if (value < 0.75f) 0.5f else 1f

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private class SavedState : BaseSavedState {
        var starCount: Int = DEFAULT_STAR_COUNT
        var rating: Float = 0f
        var stepSize: Float = 1f
        var ratingEnabled: Boolean = true

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            starCount = source.readInt()
            rating = source.readFloat()
            stepSize = source.readFloat()
            ratingEnabled = source.readInt() == 1
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(starCount)
            out.writeFloat(rating)
            out.writeFloat(stepSize)
            out.writeInt(if (ratingEnabled) 1 else 0)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        private const val DEFAULT_STAR_COUNT = 5
        private const val DEFAULT_STAR_SIZE_DP = 24f
        private const val INNER_RADIUS_RATIO = 0.45f
        private const val STAR_POINT_COUNT = 10
    }
}
