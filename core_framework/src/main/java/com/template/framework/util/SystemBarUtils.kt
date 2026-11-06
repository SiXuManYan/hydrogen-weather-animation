package com.template.framework.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Applies edge-to-edge drawing while preserving readable, inset-safe content.
 *
 * System bars remain available. The original content padding is captured once and combined with
 * system-bar and display-cutout insets. Use [FullScreenUtils] for kiosk screens.
 * - 中文：背景延伸到系统栏，内容通过 padding 自动避让状态栏、导航栏和刘海区域。
 */
object SystemBarUtils {

    /**
     * Configures [activity] and applies safe-area padding to [contentView].
     *
     * Call once for a given content view; repeated calls capture already-adjusted padding.
     */
    @Suppress("DEPRECATION")
    fun applyEdgeToEdge(activity: Activity, contentView: View) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val isLightTheme = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightTheme
            isAppearanceLightNavigationBars = isLightTheme
        }

        val initialLeft = contentView.paddingLeft
        val initialTop = contentView.paddingTop
        val initialRight = contentView.paddingRight
        val initialBottom = contentView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
    }
}
