package com.template.framework.ui.base

import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.template.framework.R
import com.template.framework.util.FullScreenUtils

/**
 * Full-window DialogFragment foundation with ViewBinding and IME-aware content positioning.
 *
 * ## Layout contract
 * - `R.id.rootLayout` receives outside clicks.
 * - `R.id.cardView` consumes content clicks and moves upward when the IME covers an `EditText`.
 *
 * The binding remains valid only for the Fragment view lifecycle. System bars stay visible unless
 * [enableImmersiveFullScreen] is overridden.
 * - 中文：提供 ViewBinding、外部点击处理及软键盘遮挡时的卡片上移能力。
 */
abstract class BaseDialogFragment<VB : ViewBinding> : DialogFragment() {

    private var _binding: VB? = null

    /** Binding valid only between `onCreateView` and `onDestroyView`. */
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException(
            "Binding is only valid between onCreateView and onDestroyView"
        )

    /** Whether an outside click may dismiss a cancelable dialog after hiding the keyboard. */
    protected open val enableOutsideDismiss: Boolean = true

    /** Whether the dialog hides system bars when started. */
    protected open val enableImmersiveFullScreen: Boolean = false

    private var currentCardViewTranslationY = 0f

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = initViewBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupOutsideClickBehaviour(view)
        setupKeyboardAdjustment(view)
        initView()
        initData()
        initListener()
        observeViewModel()
    }

    /** Loads initial data after [initView] and before listener registration. */
    abstract fun initData()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (enableImmersiveFullScreen) {
            dialog?.let(FullScreenUtils::enableFullScreenForDialog)
        }
    }

    private fun setupOutsideClickBehaviour(root: View) {
        root.findViewById<View>(R.id.rootLayout)?.setOnClickListener { handleOutsideClick() }
        root.findViewById<View>(R.id.cardView)?.setOnClickListener { /* 阻止冒泡 */ }
    }

    /** Moves the card enough to keep a nested `EditText` above the IME. */
    private fun setupKeyboardAdjustment(root: View) {
        val cardView = root.findViewById<View>(R.id.cardView) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navigationBarsInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val keyboardHeight = imeInsets.bottom - navigationBarsInsets.bottom

            if (keyboardHeight > 0) {
                val editText = findEditTextInView(cardView)
                val targetTranslationY = if (editText != null) {
                    val location = IntArray(2)
                    editText.getLocationOnScreen(location)
                    val editTextBottom = location[1] + editText.height
                    val screenHeight = resources.displayMetrics.heightPixels
                    val availableHeight = screenHeight - keyboardHeight
                    if (editTextBottom > availableHeight) {
                        val offset = (editTextBottom - availableHeight + 100).toFloat()
                        -offset.coerceAtMost(keyboardHeight.toFloat())
                    } else {
                        -(keyboardHeight / 3).toFloat()
                    }
                } else {
                    -(keyboardHeight / 3).toFloat()
                }
                if (currentCardViewTranslationY != targetTranslationY) {
                    ObjectAnimator.ofFloat(cardView, "translationY", currentCardViewTranslationY, targetTranslationY)
                        .apply { duration = 200; start() }
                    currentCardViewTranslationY = targetTranslationY
                }
            } else {
                if (currentCardViewTranslationY != 0f) {
                    ObjectAnimator.ofFloat(cardView, "translationY", currentCardViewTranslationY, 0f)
                        .apply { duration = 200; start() }
                    currentCardViewTranslationY = 0f
                }
            }
            insets
        }
    }

    private fun findEditTextInView(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findEditTextInView(view.getChildAt(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun handleOutsideClick() {
        if (isKeyboardVisible()) {
            hideKeyboard()
        } else if (enableOutsideDismiss && isCancelable) {
            dismiss()
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?: return false
        return imm.isActive && dialog?.window?.currentFocus is EditText
    }

    /** Hides the keyboard and clears the currently focused dialog view. */
    protected fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?: return
        val currentFocus = dialog?.window?.currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            currentFocus.clearFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        currentCardViewTranslationY = 0f
    }

    /** Inflates or creates the binding for this dialog view. */
    protected abstract fun initViewBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    /** Configures initial view state. */
    protected open fun initView() {}

    /** Registers UI listeners after [initData]. */
    protected open fun initListener() {}

    /** Starts lifecycle-aware state observation after listeners are registered. */
    protected open fun observeViewModel() {}
}
