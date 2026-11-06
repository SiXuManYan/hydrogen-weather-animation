package com.template.framework.websocket

import org.junit.Assert.assertEquals
import org.junit.Test

/** WebSocket 可单独创建且不会在创建时连接；释放后禁止复用。 */
class WebSocketConfigTest {
    @Test
    fun createDoesNotRequireFrameworkOrHttpInitialization() {
        val manager = WebSocketManager.create(WebSocketConfig("wss://com.exmpale.xxxx/example/socket"))
        try {
            assertEquals(WebSocketManager.ConnectionState.Disconnected, manager.connectionState.value)
        } finally {
            manager.release()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun releasedManagerCannotReconnect() {
        val manager = WebSocketManager.create(WebSocketConfig("wss://com.exmpale.xxxx/example/socket"))
        manager.release()
        manager.connect()
    }

    @Test(expected = IllegalArgumentException::class)
    fun httpSchemeIsRejected() { WebSocketConfig("https://com.exmpale.xxxx/") }

    @Test(expected = IllegalArgumentException::class)
    fun reconnectIntervalMustBePositive() {
        WebSocketConfig("wss://com.exmpale.xxxx/", reconnectIntervalMillis = 0)
    }
}
