package com.template.framework.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Applies the framework's Chinese/English locale selection and transition state.
 *
 * [setLanguageSwitchingFlag] and [isLanguageSwitching] coordinate a short fade after screen
 * recreation.
 * - 中文：处理中英文 Locale 及语言切换后的淡入状态。
 */
object LanguageUtils {

    private const val ZH = "zh"
    private const val EN = "en"

    @Volatile
    private var isLanguageSwitching = false

    /**
     * Applies [language] to the supplied context resources where supported.
     *
     * Unknown codes use [Locale.getDefault]. Activities typically need recreation for every view
     * to reflect the new locale. On Android 7.0+, this implementation updates an
     * [AppCompatActivity] directly; for other context types it creates a localized context but
     * cannot replace the caller's existing context.
     *
     * @param context Activity or other context whose resources should be updated
     * @param language `zh`, `en`, or another value that falls back to the system locale
     */
    fun setLanguage(context: Context, language: String) {
        val locale = when (language) {
            EN -> Locale.ENGLISH
            ZH -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.getDefault()
        }

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val newContext = context.createConfigurationContext(config)
            if (context is AppCompatActivity) {
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
            }
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    /** Marks the next compatible screen creation as part of a language switch. */
    fun setLanguageSwitchingFlag() {
        isLanguageSwitching = true
    }

    /** Clears the process-wide language transition marker. */
    fun clearLanguageSwitchingFlag() {
        isLanguageSwitching = false
    }

    /** Returns whether a language transition animation is pending or running. */
    fun isLanguageSwitching(): Boolean = isLanguageSwitching

    /**
     * Fades [rootView] from transparent to opaque over 200 ms.
     *
     * Completion clears the transition marker before invoking [onEnd].
     */
    fun applyFadeInAnimation(rootView: android.view.View, onEnd: (() -> Unit)? = null) {
        rootView.alpha = 0f
        rootView.post {
            ValueAnimator.ofFloat(0.0f, 1.0f).apply {
                duration = 200
                addUpdateListener { rootView.alpha = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        clearLanguageSwitchingFlag()
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }
}
