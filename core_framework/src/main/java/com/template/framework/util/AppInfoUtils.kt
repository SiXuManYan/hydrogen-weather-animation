package com.template.framework.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Reads package metadata and opens safe system intents for the current application.
 *
 * - 中文：读取应用版本信息，并提供无需拨号权限的系统拨号器入口。
 */
object AppInfoUtils {

    /**
     * Creates an `ACTION_DIAL` intent without placing a call directly.
     *
     * @param phoneNumber number shown in the system dialer
     */
    @JvmStatic
    fun createDialIntent(phoneNumber: String): Intent = Intent(
        Intent.ACTION_DIAL,
        Uri.fromParts("tel", phoneNumber.trim(), null)
    )

    /**
     * Opens the system dialer when one is available.
     *
     * @return `false` for a blank number, missing handler, or launch failure
     */
    @JvmStatic
    fun openDialer(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false

        val intent = createDialIntent(phoneNumber)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false

        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Returns the app version name, or `null` when package metadata cannot be read. */
    @JvmStatic
    fun getVersionName(context: Context): String? = getPackageInfo(context)?.versionName

    /** Returns the app version code as a `Long`, or `null` when metadata is unavailable. */
    @JvmStatic
    fun getVersionCode(context: Context): Long? {
        val packageInfo = getPackageInfo(context) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    /** Returns package metadata for the current app, or `null` if lookup fails. */
    @JvmStatic
    fun getPackageInfo(context: Context): PackageInfo? {
        val appContext = context.applicationContext
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}
