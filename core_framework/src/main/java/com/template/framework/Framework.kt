package com.template.framework

import android.app.Application
import android.content.Context
import com.template.framework.api.NetworkModule
import com.template.framework.datastore.PreferencesManager
import com.template.framework.network.HttpConfig
import com.template.framework.repository.FrameworkRepository
import com.template.framework.util.TimberUtil
import com.template.framework.websocket.WebSocketConfig
import com.template.framework.websocket.WebSocketManager

/**
 * 框架统一入口
 *
 * 使用步骤：
 * 1. 在 Application.onCreate 中调用 [init]
 * 2. 按需调用 [initHttp] / [initWebSocket]，两者互不依赖
 * 3. 偏好设置与仓库首次访问时创建，不随基础初始化启动
 *
 * 框架依赖运行时配置（版本号、是否 Debug 等），由 App 通过 [init] 注入，
 * 这样框架模块就不需要 BuildConfig.
 *
 */
object Framework {

    @Volatile
    private var application: Application? = null

    @Volatile
    private var config: FrameworkConfig? = null

    private val preferencesManager by lazy { PreferencesManager(getContext()) }

    /** 延迟创建兼容仓库，属性名避开公开 getRepository 的 JVM 签名。 */
    private val cachedRepository by lazy { FrameworkRepository(getContext(), getPreferences()) }

    /**
     * 初始化框架
     * 必须在 Application.onCreate 中调用，且只能调用一次
     *
     * @param app Application 实例
     * @param config 框架运行配置
     */
    @Synchronized
    fun init(app: Application, config: FrameworkConfig) {
        if (this.application != null) {
            return
        }
        this.config = config

        // 初始化日志
        TimberUtil.init(config.debug)

        // 最后发布初始化状态；此处不启动存储、HTTP 或 WebSocket。
        this.application = app
    }

    /** 按需初始化共享 HTTP 客户端与 Retrofit，不创建 WebSocket。 */
    fun initHttp(config: HttpConfig) {
        getContext()
        NetworkModule.init(config)
    }

    /** 按需初始化 WebSocket 客户端，不创建 Retrofit，也不自动连接。 */
    fun initWebSocket(config: WebSocketConfig): WebSocketManager {
        getContext()
        return WebSocketManager.initialize(config)
    }

    /**
     * 获取 Application 上下文
     */
    fun getContext(): Context {
        return application ?: throw IllegalStateException(
            "Framework 未初始化，请先在 Application.onCreate 中调用 Framework.init()"
        )
    }

    /**
     * 获取框架配置
     */
    fun getConfig(): FrameworkConfig {
        return config ?: throw IllegalStateException("Framework 未初始化")
    }

    /**
     * 获取偏好设置管理器
     */
    fun getPreferences(): PreferencesManager {
        return preferencesManager
    }

    /**
     * 获取仓库实例
     */
    fun getRepository(): FrameworkRepository {
        return cachedRepository
    }

    /**
     * Token 失效回调，由 App 层注入（通常用于跳转到登录页）。
     * 回调位于 OkHttp 工作线程，UI 操作需切换到主线程。
     */
    fun setOnTokenExpired(callback: () -> Unit) {
        NetworkModule.onTokenExpired = callback
    }
}
