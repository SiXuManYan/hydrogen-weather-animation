package com.template.framework.util

import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.annotation.DimenRes

/**
 * Converts Android display units and reads current display metrics.
 *
 * - 中文：提供 dp、sp、px 转换及当前资源上下文的屏幕尺寸。
 */
object ScreenUtil {

    /** Converts density-independent [dpValue] to rounded physical pixels. */
    fun dp2px(context: Context, dpValue: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dpValue * density + 0.5f).toInt()
    }

    /** Integer overload of [dp2px]. */
    fun dp2px(context: Context, dpValue: Int): Int = dp2px(context, dpValue.toFloat())

    /** Converts physical [pxValue] to density-independent pixels. */
    fun px2dp(context: Context, pxValue: Float): Float {
        val density = context.resources.displayMetrics.density
        return pxValue / density
    }

    /** Converts physical [pxValue] to scale-independent pixels using platform font scaling. */
    fun px2sp(context: Context, pxValue: Float): Float {
        val metrics = context.resources.displayMetrics
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, pxValue, metrics)
        } else {
            @Suppress("DEPRECATION")
            pxValue / metrics.scaledDensity
        }
    }

    /** Converts scale-independent [spValue] to physical pixels. */
    fun sp2px(context: Context, spValue: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spValue,
            context.resources.displayMetrics
        )
    }

    /** Resolves a dimension resource to rounded physical pixels. */
    fun dimensionToPx(context: Context, @DimenRes resId: Int): Int {
        return context.resources.getDimensionPixelSize(resId)
    }

    /** Returns current display-metric width in pixels. */
    fun screenWidth(context: Context): Int = context.resources.displayMetrics.widthPixels

    /** Returns current display-metric height in pixels. */
    fun screenHeight(context: Context): Int = context.resources.displayMetrics.heightPixels
}
