package com.template.framework.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

val Context.appVersionName: String
    get() = packageInfo().versionName.orEmpty()

val Context.appVersionCode: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo().longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo().versionCode.toLong()
    }

fun Context.openDialer(phoneNumber: String): Boolean {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)).apply {
        if (this@openDialer !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(packageManager) == null) return false
    startActivity(intent)
    return true
}

private fun Context.packageInfo(): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
