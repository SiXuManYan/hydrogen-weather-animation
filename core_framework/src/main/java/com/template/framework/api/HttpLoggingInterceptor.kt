package com.template.framework.api

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

/** 安全的概要日志；不读取正文、不打印 Header、URL 用户信息或查询参数。 */
class HttpLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // 只记录方法和主机，路径也可能携带用户或设备标识。
        val target = "${request.method} ${request.url.host}"
        val started = System.nanoTime()
        Timber.tag("OkHttp").d("--> %s", target)
        return try {
            chain.proceed(request).also {
                Timber.tag("OkHttp").d("<-- %s %d (%dms)", target, it.code, (System.nanoTime() - started) / 1_000_000)
            }
        } catch (e: Exception) {
            Timber.tag("OkHttp").d("<-- %s failed: %s", target, e.javaClass.simpleName)
            throw e
        }
    }
}
