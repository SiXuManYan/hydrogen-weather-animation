package com.template.framework.api

import com.template.framework.constants.FrameworkConstants
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Token 拦截器
 *
 * 按需为每个请求添加：
 * 1. 显式配置的 `clientid` Header（无业务默认值）
 * 2. `Authorization: Bearer {token}` Header（如果存在 token）
 *
 * 使用示例：
 * ```kotlin
 * val tokenInterceptor = TokenInterceptor(
 *     getToken = { preferencesManager.accessToken.first() }
 * )
 * ```
 *
 */
class TokenInterceptor(
    private val getToken: suspend () -> String?,
    private val clientId: String? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()

        // 添加 clientid header（如果请求未指定）
        if (clientId != null && request.header(FrameworkConstants.HEADER_CLIENT_ID) == null) {
            requestBuilder.header(FrameworkConstants.HEADER_CLIENT_ID, clientId)
        }

        // 添加 Authorization header（如果请求未指定）
        if (request.header(FrameworkConstants.HEADER_AUTHORIZATION) == null) {
            // OkHttp 拦截器是同步接口，此桥接仅应运行在异步请求的工作线程。
            val token = runBlocking { getToken() }
            if (!token.isNullOrEmpty()) {
                requestBuilder.header(
                    FrameworkConstants.HEADER_AUTHORIZATION,
                    "${FrameworkConstants.HEADER_AUTHORIZATION_PREFIX}$token"
                )
            }
        }

        return chain.proceed(requestBuilder.build())
    }

}
