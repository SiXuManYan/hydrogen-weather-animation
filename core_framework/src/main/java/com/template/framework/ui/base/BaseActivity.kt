package com.template.framework.ui.base

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.template.framework.Framework
import com.template.framework.util.LanguageUtils
import com.template.framework.util.SystemBarUtils
import timber.log.Timber

/**
 * ViewBinding-based Activity foundation with edge-to-edge and language support.
 *
 * The binding is created before [initView], then [initListener] and [initData] run in order.
 * Touching outside a focused `EditText` hides the keyboard. System bars remain visible unless a
 * subclass explicitly enables immersive mode through another utility.
 *
 * - 中文：统一管理 ViewBinding、系统栏、语言设置、键盘和初始化钩子。
 *
 * ## Usage
 * ```kotlin
 * class DemoActivity : BaseActivity<ActivityDemoBinding>() {
 *     override fun initViewBinding() = ActivityDemoBinding.inflate(layoutInflater)
 *     override fun initView() { /* 初始化视图 */ }
 * }
 * ```
 *
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null

    /** Binding valid from `onCreate` completion until `onDestroy`. */
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException(
            "Binding should not be accessed before onCreate or after onDestroy"
        )

    /**
     * Whether this Activity consumes system back presses without finishing.
     *
     * Override with `true` only for screens that intentionally disable navigation back.
     */
    protected open val enableBackKeyInterceptor: Boolean = false

    private val onBackPressedCallback = object : OnBackPressedCallback(enableBackKeyInterceptor) {
        override fun handleOnBackPressed() {
            Timber.tag(TAG).d("禁用返回键")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 之前应用语言设置（同步方式）
        applyLanguageSettingsSync()

        super.onCreate(savedInstanceState)
        _binding = initViewBinding()
        setContentView(binding.root)

        SystemBarUtils.applyEdgeToEdge(this, binding.root)

        if (enableBackKeyInterceptor) {
            onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        }

        initView()
        initListener()
        initData()

        if (LanguageUtils.isLanguageSwitching()) {
            applyFadeInAnimation()
        }
    }

    /** Applies the persisted locale before `super.onCreate`; failures keep the system locale. */
    private fun applyLanguageSettingsSync() {
        runCatching {
            // 首帧不等待 DataStore，读取之前双写保存的语言备份。
            val lang = Framework.getPreferences().getLanguageBackup()
            if (lang.isNotEmpty()) {
                LanguageUtils.setLanguage(this@BaseActivity, lang)
            }
        }
    }

    /** Plays the short transition used after a language-triggered Activity recreation. */
    private fun applyFadeInAnimation() {
        val rootView = window.decorView.rootView
        rootView.alpha = 0f
        rootView.post {
            val fadeIn = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                duration = 200
                addUpdateListener { rootView.alpha = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        LanguageUtils.clearLanguageSwitchingFlag()
                    }
                })
            }
            fadeIn.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    /** Hides the keyboard when a down event lands outside the focused `EditText`. */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val currentFocus = currentFocus
            if (currentFocus is EditText) {
                val touchX = ev.rawX.toInt()
                val touchY = ev.rawY.toInt()
                val location = IntArray(2)
                currentFocus.getLocationOnScreen(location)
                val editTextX = location[0]
                val editTextY = location[1]
                val editTextWidth = currentFocus.width
                val editTextHeight = currentFocus.height
                val isOutsideEditText = touchX < editTextX ||
                        touchX > editTextX + editTextWidth ||
                        touchY < editTextY ||
                        touchY > editTextY + editTextHeight
                if (isOutsideEditText) {
                    hideKeyboard()
                    currentFocus.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Hides the soft keyboard using the Activity window token. */
    protected fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    /** Creates the binding used as this Activity's content view. */
    protected abstract fun initViewBinding(): VB

    /** Configures initial view state after the content view and window insets are ready. */
    protected open fun initView() {}

    /** Registers UI listeners after [initView]. */
    protected open fun initListener() {}

    /** Starts initial data loading after [initListener]. */
    protected open fun initData() {}

    private companion object {
        const val TAG = "BaseActivity"
    }
}
