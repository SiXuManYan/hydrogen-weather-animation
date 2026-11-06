package com.template.framework.util

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun String.md5(charset: Charset = StandardCharsets.UTF_8): String =
    digest("MD5", charset).toHexString()

fun String.sha256(charset: Charset = StandardCharsets.UTF_8): String =
    digest("SHA-256", charset).toHexString()

fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun String.digest(algorithm: String, charset: Charset): ByteArray =
    MessageDigest.getInstance(algorithm).digest(toByteArray(charset))
