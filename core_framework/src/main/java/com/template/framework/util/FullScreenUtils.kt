package com.template.framework.util

import android.app.Activity
import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shows a dialog and immediately applies immersive full-screen behavior.
 *
 * ## Usage
 * ```kotlin
 * dialog.showFullScreen()
 * ```
 */
fun Dialog.showFullScreen() {
    show()
    FullScreenUtils.enableFullScreenForDialog(this)
}

/**
 * Configures immersive windows for kiosk-style screens.
 *
 * System bars are hidden but may be revealed transiently with a swipe. Standard phone screens
 * should normally use [SystemBarUtils] instead.
 * - 中文：用于 kiosk 沉浸式全屏；普通页面建议保留系统栏。
 */
object FullScreenUtils {

    /** Hides system bars for [activity] while allowing transient swipe reveal. */
    fun enableFullScreen(activity: Activity) {
        configureImmersiveWindow(activity.window)
    }

    /** Applies immersive mode to [dialog] and keeps the screen awake while it is shown. */
    fun enableFullScreenForDialog(dialog: Dialog) {
        val window: Window = dialog.window ?: return
        configureImmersiveWindow(window)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun configureImmersiveWindow(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
