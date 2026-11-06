package com.template.framework.websocket

import okhttp3.OkHttpClient
import okhttp3.Request
import com.template.framework.constants.FrameworkConstants

/** WebSocket 独立配置；Header 提供器在每次连接/重连时重新读取。 */
data class WebSocketConfig(
    val url: String,
    val client: OkHttpClient? = null,
    val headers: () -> Map<String, String> = { emptyMap() },
    val autoReconnect: Boolean = true,
    val reconnectIntervalMillis: Long = FrameworkConstants.WS_RECONNECT_INTERVAL,
    val maxReconnectIntervalMillis: Long = 30_000,
    val pingIntervalSeconds: Long = 20,
    val configureClient: (OkHttpClient.Builder.() -> Unit)? = null
) {
    init {
        require(url.startsWith("ws://") || url.startsWith("wss://")) { "WebSocket URL 必须使用 ws:// 或 wss://" }
        Request.Builder().url(url).build()
        require(reconnectIntervalMillis > 0)
        require(maxReconnectIntervalMillis >= reconnectIntervalMillis)
        require(pingIntervalSeconds >= 0)
    }
}
