package com.template.framework.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import com.template.framework.R
import com.template.framework.ui.base.BaseDialog

/**
 * Small loading dialog with an optional message.
 *
 * It is non-cancelable by default and never dismisses from an outside click. Updating [message]
 * while visible refreshes the label immediately.
 * - 中文：通用加载弹窗，默认不可取消，可在显示期间动态修改提示文字。
 *
 * @param context host context
 * @param message optional initial label; blank text hides the label
 * @param cancelable whether the system back action may cancel the dialog
 */
class LoadingDialog @JvmOverloads constructor(
    context: Context,
    message: CharSequence? = null,
    cancelable: Boolean = false
) : BaseDialog(context) {

    override val enableOutsideDismiss: Boolean = false

    /** Current loading label; `null` or blank hides the message view. */
    var message: CharSequence? = message
        set(value) {
            field = value
            updateMessage()
        }

    init {
        setCancelable(cancelable)
        setCanceledOnTouchOutside(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.framework_dialog_loading)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.32f)
        }
        updateMessage()
    }

    override fun show() {
        super.show()
        window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateMessage() {
        val textView = findViewById<TextView>(R.id.framework_loading_message) ?: return
        val value = message
        textView.text = value
        textView.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
