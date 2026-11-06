package com.template.framework.util

import android.view.View

/**
 * Runs [onTrigger] after [clickCount] clicks occur within [timeWindow].
 *
 * A gap longer than the window resets the counter. After triggering, the next sequence starts from
 * zero.
 * - 中文：用于隐藏入口等连续点击场景，超时或触发后会重置计数。
 *
 * ## Usage
 * ```kotlin
 * titleView.setOnMultiClickListener(clickCount = 5) {
 *     // 进入管理员设置
 * }
 * ```
 *
 * @param clickCount required click count; callers should pass a positive value
 * @param timeWindow maximum interval window in milliseconds; callers should pass a non-negative
 * value
 * @param onTrigger callback invoked after the required sequence
 */
fun View.setOnMultiClickListener(
    clickCount: Int = 3,
    timeWindow: Long = 2000L,
    onTrigger: () -> Unit
) {
    var clickCounter = 0
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > timeWindow) {
            clickCounter = 0
        }
        lastClickTime = currentTime
        clickCounter++
        if (clickCounter >= clickCount) {
            clickCounter = 0
            onTrigger()
        }
    }
}
