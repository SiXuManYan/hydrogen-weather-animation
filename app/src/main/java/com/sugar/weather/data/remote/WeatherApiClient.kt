package com.sugar.weather.data.remote

import android.content.pm.ApplicationInfo
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 装配(DESIGN.md §10.2)。
 *
 * 三个 baseUrl 的实例共享同一个 OkHttpClient(连接 10s / 读 15s);
 * 日志拦截器仅 debug 构建启用,release 零开销。
 */
object WeatherApiClient {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 15L

    @Volatile
    private var sharedClient: OkHttpClient? = null

    /** 创建挂到 [baseUrl](必须以 `/` 结尾)的 Open-Meteo 接口实例。 */
    fun openMeteo(baseUrl: String, debug: Boolean): OpenMeteoApi {
        val builder = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient(debug))
            .addConverterFactory(GsonConverterFactory.create())
        return builder.build().create(OpenMeteoApi::class.java)
    }

    private fun httpClient(debug: Boolean): OkHttpClient {
        // 共享客户端:同进程三实例复用连接池;debug 标志在一次运行内不变,直接缓存。
        sharedClient?.let { return it }
        synchronized(this) {
            sharedClient?.let { return it }
            val client = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply {
                    if (debug) {
                        addInterceptor(
                            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
                        )
                    }
                }
                .build()
            sharedClient = client
            return client
        }
    }

    /** 由 Context 判断是否 debug 构建,AppContainer 传入。 */
    fun isDebugBuild(context: android.content.Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
