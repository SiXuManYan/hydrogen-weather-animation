package com.template.framework.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/** 独立 WebSocket 管理器：按需连接、指数退避重连，不依赖 HTTP 模块或 Framework。 */
class WebSocketManager private constructor(private val config: WebSocketConfig) {
    companion object {
        @Volatile
        private var instance: WebSocketManager? = null

        /** 替换全局管理器并释放旧连接；这里只配置客户端，不连接服务器。 */
        @Synchronized
        fun initialize(config: WebSocketConfig): WebSocketManager {
            val next = create(config)
            instance?.release()
            return next.also { instance = it }
        }

        /** 多连接场景由调用方持有和释放独立实例。 */
        fun create(config: WebSocketConfig): WebSocketManager = WebSocketManager(config)

        fun getInstance(): WebSocketManager = instance
            ?: error("WebSocket 未初始化，请调用 Framework.initWebSocket 或 WebSocketManager.initialize")
    }

    private val ownsClient = config.client == null
    private val client = config.client ?: OkHttpClient.Builder()
        .pingInterval(config.pingIntervalSeconds, TimeUnit.SECONDS)
        .apply { config.configureClient?.invoke(this) }
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var generation = 0L
    private var shouldReconnect = false
    private var released = false

    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = state.asStateFlow()

    // 消息是事件：相同消息不能去重，页面重建后不重放旧消息；慢消费者最多缓冲 64 条。
    private val messages = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messageFlow = messages.asSharedFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /** 重复连接会被忽略；forceReconnect 可用于刷新认证 Header。 */
    @Synchronized
    fun connect(forceReconnect: Boolean = false) {
        check(!released) { "WebSocketManager 已释放，请创建新实例" }
        if (!forceReconnect && (state.value is ConnectionState.Connected || state.value is ConnectionState.Connecting)) return
        reconnectJob?.cancel()
        reconnectJob = null
        shouldReconnect = config.autoReconnect
        generation++
        val activeGeneration = generation
        // 旧连接回调会迟到，先递增代次再取消，避免旧回调污染新连接状态。
        socket?.cancel()
        socket = null
        state.value = ConnectionState.Connecting
        try {
            val request = Request.Builder().url(config.url).apply {
                config.headers().forEach { (name, value) -> header(name, value) }
            }.build()
            socket = client.newWebSocket(request, listener(activeGeneration))
        } catch (e: Exception) {
            state.value = ConnectionState.Error(e.javaClass.simpleName)
            scheduleReconnect()
        }
    }

    @Synchronized
    fun disconnect() {
        shouldReconnect = false
        generation++
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        socket?.cancel()
        socket = null
        state.value = ConnectionState.Disconnected
    }

    /** true 仅表示进入发送队列，不代表服务器已收到。 */
    @Synchronized
    fun sendMessage(message: String): Boolean =
        if (state.value is ConnectionState.Connected) socket?.send(message) ?: false else false

    private fun scheduleReconnect() {
        if (!shouldReconnect || released || reconnectJob?.isActive == true) return
        // 首次握手失败也会重试，延迟有上限，避免失败时紧密循环。
        val factor = 1L shl reconnectAttempt.coerceAtMost(20)
        val waitMillis = if (config.reconnectIntervalMillis > config.maxReconnectIntervalMillis / factor) {
            config.maxReconnectIntervalMillis
        } else {
            (config.reconnectIntervalMillis * factor).coerceAtMost(config.maxReconnectIntervalMillis)
        }
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(20)
        reconnectJob = scope.launch {
            delay(waitMillis)
            synchronized(this@WebSocketManager) {
                reconnectJob = null
                if (shouldReconnect && !released) connect()
            }
        }
    }

    private fun listener(activeGeneration: Long) = object : WebSocketListener() {
        private fun withActiveConnection(block: () -> Unit) {
            synchronized(this@WebSocketManager) {
                if (!released && activeGeneration == generation) block()
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) = withActiveConnection {
            reconnectAttempt = 0
            state.value = ConnectionState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) = withActiveConnection {
            messages.tryEmit(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket, bytes.utf8())

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = withActiveConnection {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = withActiveConnection {
            socket = null
            state.value = ConnectionState.Disconnected
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = withActiveConnection {
            socket = null
            state.value = ConnectionState.Error(t.javaClass.simpleName)
            scheduleReconnect()
        }
    }

    /** 释放后不可复用；不关闭调用方传入的共享客户端。 */
    @Synchronized
    fun release() {
        if (released) return
        disconnect()
        released = true
        scope.cancel()
        if (ownsClient) {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
