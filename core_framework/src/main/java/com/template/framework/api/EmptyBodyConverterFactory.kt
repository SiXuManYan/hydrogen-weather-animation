package com.template.framework.api

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Converts an empty HTTP response body to `null` before delegating non-empty bodies.
 *
 * Register this factory before the JSON converter and use a nullable Retrofit return body.
 * - 中文：处理服务端完全空的响应体，必须放在 JSON Converter 之前。
 *
 * ## Usage
 * ```kotlin
 * Retrofit.Builder()
 *     .addConverterFactory(EmptyBodyConverterFactory.create())
 *     .addConverterFactory(GsonConverterFactory.create())
 * ```
 */
class EmptyBodyConverterFactory private constructor() : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
        val delegate = retrofit.nextResponseBodyConverter<Any>(this, type, annotations)
        return Converter { body ->
            if (body.isEmpty()) {
                body.close()
                null
            } else {
                delegate.convert(body)
            }
        }
    }

    private fun ResponseBody.isEmpty(): Boolean {
        if (contentLength() == 0L) return true
        return !source().request(1L)
    }

    companion object {
        /** Creates a stateless converter factory instance. */
        @JvmStatic
        fun create(): EmptyBodyConverterFactory = EmptyBodyConverterFactory()
    }
}
