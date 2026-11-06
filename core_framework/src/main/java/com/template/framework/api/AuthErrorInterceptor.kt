package com.template.framework.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.template.framework.api.model.ApiResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/**
 * 认证错误拦截器
 *
 * 同时处理两种 Token 失效场景：
 * 1. HTTP 401 Unauthorized
 * 2. HTTP 200 但响应体中 `code == 401`
 *
 * 触发时：
 * 1. 调用 [clearToken] 清除本地 token
 * 2. 调用 [onTokenExpired] 通知 App 层跳转登录页
 *
 * 两个回调均在 OkHttp 请求线程执行，UI 操作必须切换到主线程。
 *
 */
class AuthErrorInterceptor(
    private val onTokenExpired: () -> Unit,
    private val clearToken: suspend () -> Unit
) : Interceptor {

    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        var isTokenExpired = false

        // 1) HTTP 状态码 401
        if (response.code == 401) {
            Timber.tag(TAG).e("Token 已过期, 收到 HTTP 401 Unauthorized")
            isTokenExpired = true
        } else if (response.code == 200 && response.body?.contentType()?.subtype == "json") {
            // 2) HTTP 200 但业务 code == 401
            // 只检查有限的 JSON 响应，避免图片、流式或超大正文阻塞请求。
            val responseBody = response.peekBody(64 * 1024L)
            val contentType = responseBody.contentType()
            if (contentType?.type == "application" && contentType.subtype == "json") {
                try {
                    val jsonObject = gson.fromJson(responseBody.string(), JsonObject::class.java)
                    if (jsonObject.has("code")) {
                        val code = jsonObject.get("code").asInt
                        if (code == ApiResponse.TOKEN_EXPIRED_CODE) {
                            Timber.tag(TAG).w("Token 业务码 401")
                            isTokenExpired = true
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).d("响应不是约定的认证 JSON")
                }
            }
        }

        if (isTokenExpired) {
            runBlocking { clearToken() }
            onTokenExpired()
        }

        return response
    }

    companion object {
        private const val TAG = "AuthError"
    }
}
