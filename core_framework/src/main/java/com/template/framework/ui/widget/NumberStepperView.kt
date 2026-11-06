package com.template.framework.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.IntRange
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.template.framework.R

/**
 * Business-agnostic integer stepper with increment and decrement controls.
 *
 * The value is always clamped to [minValue]..[maxValue]. Programmatic updates do
 * not notify [onValueChangeListener] unless [setValue] is called with `notify = true`.
 * - 中文：整数步进控件，自动限制上下界；普通赋值默认不触发回调。
 *
 * ## Usage
 * ```kotlin
 * stepper.apply {
 *     minValue = 1
 *     maxValue = 99
 *     onValueChangeListener = OnValueChangeListener { _, _, newValue, _ ->
 *         updateQuantity(newValue)
 *     }
 * }
 * ```
 *
 * @param context view context
 * @param attrs optional XML attributes
 * @param defStyleAttr default style attribute
 */
class NumberStepperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Identifies the operation that produced a notified value change. */
    enum class Change {
        /** User pressed the increment control. */
        INCREMENT,

        /** User pressed the decrement control. */
        DECREMENT,

        /** Caller changed the value through [setValue]. */
        PROGRAMMATIC
    }

    /** Receives notified value changes. */
    fun interface OnValueChangeListener {
        /**
         * Called after the visible value changes.
         *
         * @param view source stepper
         * @param oldValue previous bounded value
         * @param newValue new bounded value
         * @param change operation that produced the update
         */
        fun onValueChanged(
            view: NumberStepperView,
            oldValue: Int,
            newValue: Int,
            change: Change
        )
    }

    private val decrementButton = createButton(
        R.drawable.framework_ic_remove_24,
        R.string.framework_decrease
    )
    private val valueText = AppCompatTextView(context).apply {
        gravity = Gravity.CENTER
        minWidth = dp(40)
        setPadding(dp(8), 0, dp(8), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(resolveColor(android.R.attr.textColorPrimary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val incrementButton = createButton(
        R.drawable.framework_ic_add_24,
        R.string.framework_increase
    )

    /** Listener for user changes and programmatic changes that explicitly request notification. */
    var onValueChangeListener: OnValueChangeListener? = null

    /** Inclusive minimum; updating it clamps [value] without notifying the listener. */
    var minValue: Int = 0
        set(value) {
            require(value <= maxValue) { "minValue must be less than or equal to maxValue" }
            field = value
            setValueInternal(currentValue, Change.PROGRAMMATIC, notify = false)
        }

    /** Inclusive maximum; updating it clamps [value] without notifying the listener. */
    var maxValue: Int = Int.MAX_VALUE
        set(value) {
            require(value >= minValue) { "maxValue must be greater than or equal to minValue" }
            field = value
            setValueInternal(currentValue, Change.PROGRAMMATIC, notify = false)
        }

    /** Positive amount added or removed for each button press. */
    @setparam:IntRange(from = 1)
    var step: Int = 1
        set(value) {
            require(value > 0) { "step must be greater than zero" }
            field = value
        }

    /** Whether the numeric label becomes invisible while [value] equals [minValue]. */
    var hideValueAtMin: Boolean = false
        set(value) {
            field = value
            updateViews()
        }

    /** Whether the disabled decrement control remains visible at [minValue]. */
    var showDecrementAtMin: Boolean = false
        set(value) {
            field = value
            updateViews()
        }

    /** Current bounded value; direct assignment does not notify [onValueChangeListener]. */
    var value: Int
        get() = currentValue
        set(value) = setValue(value, notify = false)

    private var currentValue: Int = 0

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val array = context.obtainStyledAttributes(attrs, R.styleable.FwNumberStepperView)
        try {
            minValue = array.getInt(R.styleable.FwNumberStepperView_fw_stepper_min_value, 0)
            maxValue = array.getInt(
                R.styleable.FwNumberStepperView_fw_stepper_max_value,
                Int.MAX_VALUE
            )
            require(minValue <= maxValue) {
                "fw_stepper_min_value must be less than or equal to fw_stepper_max_value"
            }
            step = array.getInt(R.styleable.FwNumberStepperView_fw_stepper_step, 1)
            hideValueAtMin = array.getBoolean(
                R.styleable.FwNumberStepperView_fw_stepper_hide_value_at_min,
                false
            )
            showDecrementAtMin = array.getBoolean(
                R.styleable.FwNumberStepperView_fw_stepper_show_decrement_at_min,
                false
            )
            currentValue = array.getInt(
                R.styleable.FwNumberStepperView_fw_stepper_value,
                minValue
            ).coerceIn(minValue, maxValue)
        } finally {
            array.recycle()
        }

        addView(decrementButton, LayoutParams(dp(40), dp(40)))
        addView(valueText, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
        addView(incrementButton, LayoutParams(dp(40), dp(40)))

        decrementButton.setOnClickListener {
            val next = (currentValue.toLong() - step).coerceAtLeast(minValue.toLong()).toInt()
            setValueInternal(next, Change.DECREMENT, notify = true)
        }
        incrementButton.setOnClickListener {
            val next = (currentValue.toLong() + step).coerceAtMost(maxValue.toLong()).toInt()
            setValueInternal(next, Change.INCREMENT, notify = true)
        }
        updateViews()
    }

    /**
     * Sets and clamps [value].
     *
     * @param notify whether a changed value should invoke [onValueChangeListener]
     */
    fun setValue(value: Int, notify: Boolean = false) {
        setValueInternal(value, Change.PROGRAMMATIC, notify)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        updateViews()
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()).also { it.value = currentValue }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        setValue(state.value, notify = false)
    }

    private fun setValueInternal(value: Int, change: Change, notify: Boolean) {
        val boundedValue = value.coerceIn(minValue, maxValue)
        val oldValue = currentValue
        currentValue = boundedValue
        updateViews()
        if (notify && oldValue != boundedValue) {
            onValueChangeListener?.onValueChanged(this, oldValue, boundedValue, change)
        }
    }

    private fun updateViews() {
        val atMin = currentValue <= minValue
        val atMax = currentValue >= maxValue
        valueText.text = currentValue.toString()
        valueText.visibility = if (hideValueAtMin && atMin) INVISIBLE else VISIBLE
        decrementButton.visibility = if (!showDecrementAtMin && atMin) INVISIBLE else VISIBLE
        decrementButton.isEnabled = isEnabled && !atMin
        incrementButton.isEnabled = isEnabled && !atMax
        decrementButton.alpha = if (decrementButton.isEnabled) 1f else 0.38f
        incrementButton.alpha = if (incrementButton.isEnabled) 1f else 0.38f
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            stateDescription = currentValue.toString()
        }
    }

    private fun createButton(drawableRes: Int, contentDescriptionRes: Int): ImageButton {
        return AppCompatImageButton(context).apply {
            setImageResource(drawableRes)
            contentDescription = context.getString(contentDescriptionRes)
            scaleType = ImageView.ScaleType.CENTER
            background = resolveSelectableBackground()
            ImageViewCompat.setImageTintList(this, resolveColor(android.R.attr.textColorPrimary))
            minimumWidth = dp(40)
            minimumHeight = dp(40)
        }
    }

    private fun resolveSelectableBackground() = TypedValue().let { value ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        AppCompatResources.getDrawable(context, value.resourceId)
    }

    private fun resolveColor(attribute: Int): ColorStateList {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute $attribute is required"
        }
        return if (value.resourceId != 0) {
            AppCompatResources.getColorStateList(context, value.resourceId)
                ?: ColorStateList.valueOf(value.data)
        } else {
            ColorStateList.valueOf(value.data)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private class SavedState : BaseSavedState {
        var value: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            value = source.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(value)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel) = SavedState(source)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
