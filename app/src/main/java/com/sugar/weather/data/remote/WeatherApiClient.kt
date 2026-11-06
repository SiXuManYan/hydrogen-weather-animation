package com.sugar.weather.data.remote

import com.template.framework.api.NetworkModule
import okhttp3.OkHttpClient

/**
 * 使用基础框架的 HTTP 工厂装配天气服务，三个域名共用一个连接池。
 *
 * Open-Meteo 不需要认证或版本 Header；保留连接 10 秒、读取 15 秒的超时，
 * Debug 仅输出框架摘要日志，Release 不安装日志拦截器。
 *
 * @param debug 是否启用框架 HTTP 摘要日志。
 */
class WeatherApiClient(debug: Boolean) {

    /** 容器级共享客户端，供各域名复用；不启用框架的全局单地址 Retrofit。 */
    internal val httpClient: OkHttpClient = NetworkModule.createOkHttpClient(
        connectTimeoutSeconds = CONNECT_TIMEOUT_SECONDS,
        readTimeoutSeconds = READ_TIMEOUT_SECONDS,
        writeTimeoutSeconds = WRITE_TIMEOUT_SECONDS,
        debug = debug
    )

    /** 创建指定域名的天气接口，JSON 转换和 URL 规范化统一交给 Library。 */
    fun openMeteo(baseUrl: String): OpenMeteoApi =
        NetworkModule.createApiService<OpenMeteoApi>(baseUrl = baseUrl, client = httpClient)

    private companion object {
        /** 与迁移前保持一致的连接、读取和写入时限。 */
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val READ_TIMEOUT_SECONDS = 15L
        const val WRITE_TIMEOUT_SECONDS = 10L
    }
}
