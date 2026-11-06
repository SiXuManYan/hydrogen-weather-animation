package com.template.framework.repository

import android.content.Context
import com.template.framework.api.NetworkModule
import com.template.framework.datastore.PreferencesManager
import kotlinx.coroutines.flow.Flow

/** 兼容旧的偏好设置入口；业务仓库建议组合 PreferencesManager 与自定义 API。 */
open class FrameworkRepository(
    @Suppress("UNUSED_PARAMETER") context: Context,
    protected val preferencesManager: PreferencesManager
) {
    val serverIp: Flow<String> = preferencesManager.serverIp
    val serverPort: Flow<String> = preferencesManager.serverPort
    val accessToken: Flow<String?> = preferencesManager.accessToken
    val language: Flow<String> = preferencesManager.language

    suspend fun saveServerIp(ip: String) = preferencesManager.saveServerIp(ip)
    suspend fun saveServerPort(port: String) = preferencesManager.saveServerPort(port)
    suspend fun saveAccessToken(token: String) = preferencesManager.saveAccessToken(token)
    suspend fun clearAccessToken() = preferencesManager.clearAccessToken()

    /** API 类型由业务定义，HTTP 必须先单独初始化。 */
    inline fun <reified T> getApiService(): T = NetworkModule.getRetrofit().create(T::class.java)
}
