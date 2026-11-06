package com.sugar.weather.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sugar.weather.R
import java.util.Locale

/** 关于页与文档共用外链确认，仅允许网页地址，确认前不启动浏览器。 */
fun AppCompatActivity.confirmAndOpenExternalLink(link: String) {
    val uri = link.toUri()
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.external_link_title)
        .setMessage(getString(R.string.external_link_message, uri.host, uri.toString()))
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.external_link_open) { _, _ ->
            openBrowserTab(uri.buildUpon().scheme(scheme).build())
        }
        .show()
}

/** 优先使用 Chrome 或其他支持 Custom Tabs 的浏览器，失败后回退系统网页入口。 */
private fun AppCompatActivity.openBrowserTab(uri: Uri) {
    val browserPackage = resolveCustomTabsPackage()
    if (browserPackage != null) {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(this, R.color.city_manage_toolbar_bg))
                    .build()
            )
            .build()
        tab.intent.setPackage(browserPackage)
        try {
            tab.launchUrl(this, uri)
            return
        } catch (_: ActivityNotFoundException) {
            // 提供者在查询后被卸载或禁用时，继续尝试系统浏览器。
        } catch (_: SecurityException) {
            // 受设备策略限制的浏览器不阻止用户选择其他网页应用。
        }
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        showNoBrowserMessage()
    } catch (_: SecurityException) {
        showNoBrowserMessage()
    }
}

/** 查询服务提供者，避免把网页交给只处理深链、却不支持浏览器标签页的应用。 */
private fun AppCompatActivity.resolveCustomTabsPackage(): String? = try {
    val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentServices(serviceIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentServices(serviceIntent, 0)
    }
    val candidates = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev") +
        services.mapNotNull { it.serviceInfo?.packageName }
    CustomTabsClient.getPackageName(this, candidates.distinct(), true)
} catch (_: SecurityException) {
    null
}

/** 两种浏览器入口均不可用时给予可见反馈。 */
private fun AppCompatActivity.showNoBrowserMessage() {
    Snackbar.make(
        findViewById<View>(android.R.id.content), R.string.document_no_browser, Snackbar.LENGTH_LONG
    ).show()
}
