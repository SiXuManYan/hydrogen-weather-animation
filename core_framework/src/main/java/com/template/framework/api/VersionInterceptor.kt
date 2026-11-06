package com.template.framework.api

import com.template.framework.Framework
import com.template.framework.constants.FrameworkConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `VersionCode` and `VersionName` headers from [FrameworkConfig].
 *
 * Existing headers are preserved. If the framework has not been initialized, the request proceeds
 * without version headers instead of failing.
 * - 中文：补充应用版本 Header；请求已设置同名 Header 时不会覆盖。
 */
class VersionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()

        val config = runCatching { Framework.getConfig() }.getOrNull()
        if (config != null) {
            if (request.header(FrameworkConstants.HEADER_VERSION_CODE) == null) {
                requestBuilder.header(
                    FrameworkConstants.HEADER_VERSION_CODE,
                    config.versionCode.toString()
                )
            }
            if (request.header(FrameworkConstants.HEADER_VERSION_NAME) == null) {
                requestBuilder.header(
                    FrameworkConstants.HEADER_VERSION_NAME,
                    config.versionName
                )
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
