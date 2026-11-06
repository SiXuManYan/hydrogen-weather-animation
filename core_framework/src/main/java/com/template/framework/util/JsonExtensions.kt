package com.template.framework.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonUtils {
    @PublishedApi
    internal val gson = Gson()

    fun toJson(value: Any?): String = gson.toJson(value)

    fun <T> fromJson(json: String, type: Class<T>): T = gson.fromJson(json, type)

    inline fun <reified T> fromJson(json: String): T =
        gson.fromJson(json, object : TypeToken<T>() {}.type)
}

fun Any?.toJson(): String = JsonUtils.toJson(this)

inline fun <reified T> String.fromJson(): T = JsonUtils.fromJson(this)
