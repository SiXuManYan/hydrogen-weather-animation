package com.template.framework.ui.base

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.template.framework.R
import com.template.framework.util.FullScreenUtils

/**
 * Dialog foundation with optional immersive mode and outside-click handling.
 *
 * ## Layout contract
 * - `R.id.rootLayout` receives outside clicks.
 * - `R.id.cardView`, when present, consumes clicks inside the dialog content.
 *
 * An outside click hides the keyboard first; a later click dismisses the dialog when
 * [enableOutsideDismiss] is enabled.
 * - 中文：外部点击会优先收起键盘，再按配置决定是否关闭弹窗。
 *
 * ## Usage
 * ```kotlin
 * class DemoDialog(context: Context) : BaseDialog(context) {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.dialog_demo)
 *     }
 * }
 * ```
 *
 * @param context host context used by [Dialog]
 */
abstract class BaseDialog(context: Context) : Dialog(context) {

    /** Whether an outside click may dismiss the dialog after the keyboard is hidden. */
    protected open val enableOutsideDismiss: Boolean = true

    /** Whether [show] hides system bars for a kiosk-style experience. */
    protected open val enableImmersiveFullScreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun show() {
        super.show()
        if (enableImmersiveFullScreen) {
            FullScreenUtils.enableFullScreenForDialog(this)
        }
        setupOutsideClickBehaviour()
    }

    private fun setupOutsideClickBehaviour() {
        setCanceledOnTouchOutside(false)
        findViewById<View>(R.id.rootLayout)?.setOnClickListener { handleOutsideClick() }
        findViewById<View>(R.id.cardView)?.setOnClickListener { /* 阻止冒泡 */ }
    }

    private fun handleOutsideClick() {
        if (isKeyboardVisible()) {
            hideKeyboard()
        } else if (enableOutsideDismiss) {
            dismiss()
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
            ?: return false
        return imm.isActive && window?.currentFocus is EditText
    }

    /** Hides the keyboard and clears the currently focused dialog view. */
    protected fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(context, InputMethodManager::class.java)
            ?: return
        val currentFocus = window?.currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        }
    }
}
