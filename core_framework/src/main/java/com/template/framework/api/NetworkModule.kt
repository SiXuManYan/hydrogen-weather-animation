package com.template.framework.api

import com.template.framework.Framework
import com.template.framework.constants.FrameworkConstants
import com.template.framework.network.HttpConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** HTTP 工厂与可选的共享实例；不引用或初始化 WebSocket。 */
object NetworkModule {
    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    var onTokenExpired: (() -> Unit)? = null

    /** 显式初始化；重复调用可替换服务地址，已有 Service 实例仍指向旧服务器。 */
    @Synchronized
    fun init(config: HttpConfig) {
        val client = config.client ?: createOkHttpClient(
            getToken = config.getToken,
            clearToken = config.clearToken,
            connectTimeoutSeconds = config.connectTimeoutSeconds,
            readTimeoutSeconds = config.readTimeoutSeconds,
            writeTimeoutSeconds = config.writeTimeoutSeconds,
            debug = config.debug,
            clientId = config.clientId,
            includeVersionHeaders = config.includeVersionHeaders,
            configure = config.configureClient
        )
        retrofit = createRetrofit(config.baseUrl, client)
    }

    /** 默认无业务拦截器；可供 API、图片加载等显式复用同一连接池。 */
    fun createOkHttpClient(
        getToken: (suspend () -> String?)? = null,
        clearToken: (suspend () -> Unit)? = null,
        connectTimeoutSeconds: Long = FrameworkConstants.CONNECT_TIMEOUT,
        readTimeoutSeconds: Long = FrameworkConstants.READ_TIMEOUT,
        writeTimeoutSeconds: Long? = FrameworkConstants.WRITE_TIMEOUT,
        debug: Boolean = runCatching { Framework.getConfig().debug }.getOrDefault(false),
        clientId: String? = null,
        includeVersionHeaders: Boolean = false,
        configure: (OkHttpClient.Builder.() -> Unit)? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        writeTimeoutSeconds?.let { builder.writeTimeout(it, TimeUnit.SECONDS) }
        if (debug) builder.addInterceptor(HttpLoggingInterceptor())
        if (includeVersionHeaders) builder.addInterceptor(VersionInterceptor())
        if (getToken != null || clientId != null) {
            builder.addInterceptor(TokenInterceptor(getToken ?: { null }, clientId))
        }
        if (clearToken != null) {
            builder.addInterceptor(AuthErrorInterceptor({ onTokenExpired?.invoke() }, clearToken))
        }
        configure?.invoke(builder)
        return builder.build()
    }

    /** 使用现有客户端创建 Retrofit；自动补齐 URL 末尾斜杠。 */
    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** 兼容简便工厂；需要复用连接池时优先使用带 client 的重载。 */
    fun createRetrofit(
        baseUrl: String,
        getToken: (suspend () -> String?)? = null,
        clearToken: (suspend () -> Unit)? = null
    ): Retrofit = createRetrofit(baseUrl, createOkHttpClient(getToken, clearToken))

    inline fun <reified T> createApiService(baseUrl: String, client: OkHttpClient): T =
        createRetrofit(baseUrl, client).create(T::class.java)

    inline fun <reified T> createApiService(
        baseUrl: String,
        noinline getToken: (suspend () -> String?)? = null,
        noinline clearToken: (suspend () -> Unit)? = null
    ): T = createRetrofit(baseUrl, getToken, clearToken).create(T::class.java)

    /** 未初始化时快速失败，不隐式启动其他能力。 */
    fun getRetrofit(): Retrofit = retrofit ?: error("HTTP 未初始化，请调用 Framework.initHttp 或 NetworkModule.init")

    fun getClient(): OkHttpClient = getRetrofit().callFactory() as? OkHttpClient
        ?: error("当前 Retrofit 使用自定义 Call.Factory，不是 OkHttpClient")

    fun cacheRetrofit(retrofit: Retrofit) { this.retrofit = retrofit }
    fun getCachedRetrofit(): Retrofit? = retrofit
}
