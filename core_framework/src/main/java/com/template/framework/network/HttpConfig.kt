package com.template.framework.network

import com.template.framework.constants.FrameworkConstants
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** HTTP 专属配置；URL 与环境由业务显式选择，默认不添加认证或版本头。 */
data class HttpConfig(
    val baseUrl: String,
    val client: OkHttpClient? = null,
    val debug: Boolean = false,
    val connectTimeoutSeconds: Long = FrameworkConstants.CONNECT_TIMEOUT,
    val readTimeoutSeconds: Long = FrameworkConstants.READ_TIMEOUT,
    val writeTimeoutSeconds: Long? = FrameworkConstants.WRITE_TIMEOUT,
    val getToken: (suspend () -> String?)? = null,
    val clearToken: (suspend () -> Unit)? = null,
    val clientId: String? = null,
    val includeVersionHeaders: Boolean = false,
    val configureClient: (OkHttpClient.Builder.() -> Unit)? = null
) {
    init {
        // URL 不允许内嵌凭证或查询参数，认证与业务参数应放在各自请求中。
        val url = baseUrl.toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty()) { "Base URL 不得包含凭证" }
        require(url.query == null && url.fragment == null) { "Base URL 不得包含查询或片段" }
        require(connectTimeoutSeconds >= 0 && readTimeoutSeconds >= 0)
        require(writeTimeoutSeconds == null || writeTimeoutSeconds >= 0)
    }
}
