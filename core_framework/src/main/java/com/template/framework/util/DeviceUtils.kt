package com.template.framework.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import java.net.NetworkInterface

/**
 * Device network and identifier helpers.
 *
 * Hardware identifiers are restricted on modern Android versions. [getDeviceSerialNumber] falls
 * back to Android ID when the serial number is unavailable.
 * - 中文：提供局域网 IPv4、设备标识回退和 ASCII 英文大写转换。
 */
object DeviceUtils {

    /**
     * Returns the first non-loopback IPv4 address found across network interfaces.
     *
     * @return an IPv4 address, or an empty string when none can be read
     */
    fun getDeviceLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress ?: ""
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns the hardware serial number when permitted, otherwise Android ID.
     *
     * Android 10+ generally restricts hardware serial access to privileged apps. This function
     * catches permission failures and returns an empty string only when both identifiers fail.
     *
     * @param context context used to access secure settings
     */
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    fun getDeviceSerialNumber(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val serial = Build.getSerial()
                    if (serial != "unknown" && serial.isNotEmpty()) {
                        serial
                    } else {
                        getAndroidId(context)
                    }
                } catch (e: SecurityException) {
                    getAndroidId(context)
                }
            } else {
                @Suppress("DEPRECATION")
                val serial = Build.SERIAL
                if (serial.isNotEmpty() && serial != "unknown") serial else getAndroidId(context)
            }
        } catch (e: Exception) {
            getAndroidId(context)
        }
    }

    /**
     * Returns Android ID, or an empty string when secure settings cannot be read.
     * - 中文：获取 Android ID，读取失败时返回空字符串。
     */
    private fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Converts ASCII letters in [text] to uppercase while preserving all other characters. */
    fun toUpperCaseEnglish(text: String): String {
        return text.map { char ->
            if (char.isLetter() && char.code < 128) char.uppercaseChar() else char
        }.joinToString("")
    }
}
