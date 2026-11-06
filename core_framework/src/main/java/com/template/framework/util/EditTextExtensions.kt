package com.template.framework.util

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** 控制获取焦点是否打开软键盘，适合外部键盘或扫码枪输入。 */
fun EditText.setShowSoftInputOnFocus(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        showSoftInputOnFocus = show
    } else {
        try {
            val method = EditText::class.java.getMethod(
                "setShowSoftInputOnFocus",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(this, show)
        } catch (_: Exception) {
            // Unsupported on this device.
        }
    }
}

/** 等待本次视图遍历后请求焦点。 */
fun EditText.requestFocusSafely() {
    post { if (!isFocused) requestFocus() }
}

/** 延迟请求焦点；非正数延迟改为下一次视图遍历。 */
fun EditText.requestFocusSafely(delayMillis: Long) {
    if (delayMillis <= 0) {
        requestFocusSafely()
        return
    }
    postDelayed({ if (!isFocused) requestFocus() }, delayMillis)
}

/** 焦点丢失后恢复；页面销毁时清理监听与回调，防止继续持有页面。 */
fun EditText.keepFocus(lifecycleOwner: LifecycleOwner, delayAfterInput: Long = 300L) {
    val handler = Handler(Looper.getMainLooper())
    // 统一非负延迟约束，避免负数被当作立即执行。
    val focusDelay = delayAfterInput.coerceAtLeast(0L)
    val focusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            handler.postDelayed({ if (!isFocused) requestFocus() }, focusDelay)
        }
    }
    onFocusChangeListener = focusChangeListener
    lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) {
            onFocusChangeListener = null
            handler.removeCallbacksAndMessages(null)
        }
    })
}

/** 更新文本并将光标移到末尾；null 视为空文本。 */
fun EditText.setTextAndMoveCursorToEnd(text: String?) {
    val safeText = text.orEmpty()
    setText(safeText)
    setSelection(safeText.length)
}

/** 监听文本变更并移除前后空白。 */
fun EditText.doAfterTrimmedTextChanged(action: (String) -> Unit): TextWatcher =
    doAfterTextChanged { action(it?.toString()?.trim().orEmpty()) }

/** 判断所有输入框均包含非空白内容。 */
fun areAllNotBlank(vararg editTexts: EditText): Boolean =
    editTexts.all { it.text?.toString()?.isNotBlank() == true }
