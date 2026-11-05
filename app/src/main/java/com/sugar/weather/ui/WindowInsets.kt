package com.sugar.weather.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

/** 让背景延伸至系统栏，并按页面底色设置导航栏图标。 */
fun AppCompatActivity.configureEdgeToEdge(lightNavigationBar: Boolean = false) {
    WindowCompat.enableEdgeToEdge(window)
    window.isNavigationBarContrastEnforced = false
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = lightNavigationBar
    }
}

/**
 * 在 XML 原始内边距上叠加安全区，重复派发时不累加。
 *
 * @param top 是否避让状态栏。
 * @param bottom 是否避让导航栏；[ime] 开启时同时避让键盘。
 * @param horizontal 是否避让左右系统栏和刘海。
 * @param ime 是否将键盘高度计入底部安全区，适用于搜索页。
 * @param extraTop 状态栏下方预留的固定头部高度。
 */
fun View.applySafeInsets(
    top: Boolean = false,
    bottom: Boolean = false,
    horizontal: Boolean = true,
    ime: Boolean = false,
    extraTop: Int = 0
) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            (if (ime) WindowInsetsCompat.Type.ime() else 0)
        val safe = insets.getInsets(types)
        view.updatePadding(
            left = baseLeft + if (horizontal) safe.left else 0,
            top = baseTop + extraTop + if (top) safe.top else 0,
            right = baseRight + if (horizontal) safe.right else 0,
            bottom = baseBottom + if (bottom) safe.bottom else 0
        )
        // 不消费 insets，保证 Android 10 上的兄弟视图也能收到安全区。
        insets
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
