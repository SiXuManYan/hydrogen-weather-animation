package com.template.framework.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.template.framework.constants.FrameworkConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * 框架默认的 DataStore 实例
 *
 * 注：DataStore 名称在编译期固定为 "framework_preferences"。
 * 业务需要不同名称时，可在自己的模块中创建独立的 Manager，
 * 通过相同的方式使用 `preferencesDataStore` 委托。
 */
private val Context.frameworkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "framework_preferences"
)

/**
 * Preferences 管理器
 *
 * 框架提供完整 HTTP/WS URL、Token、语言，以及兼容的 IP/Port Key：
 * - [serverIp]   - 服务器 IP
 * - [serverPort] - 服务器端口
 * - [accessToken] - 访问令牌
 * - [language]   - 语言
 *
 * 业务可在自己的 Manager 中继承或包装此类，添加自定义 Key。
 *
 * ## DataStore + SharedPreferences 双写双读
 * 通过 [DataStoreBackupHelper] 实现，防止 DataStore 异常导致配置丢失。
 *
 * ## 同步访问
 * 旧同步方法内部阻塞等待 DataStore，只适用于后台线程；UI 使用 Flow 或语言备份。
 *
 */
open class PreferencesManager(context: Context) {

    private val appContext = context.applicationContext

    private val dataStore: DataStore<Preferences> = appContext.frameworkDataStore

    private val backup = DataStoreBackupHelper(appContext, dataStore)

    private companion object {
        val SERVER_IP = stringPreferencesKey(FrameworkConstants.PREF_SERVER_IP)
        val SERVER_PORT = stringPreferencesKey(FrameworkConstants.PREF_SERVER_PORT)
        val ACCESS_TOKEN = stringPreferencesKey(FrameworkConstants.PREF_ACCESS_TOKEN)
        val LANGUAGE = stringPreferencesKey(FrameworkConstants.PREF_LANGUAGE)
        val HTTP_BASE_URL = stringPreferencesKey("http_base_url")
        val WEBSOCKET_URL = stringPreferencesKey("websocket_url")
    }

    // region [Server IP]

    /** 完整 HTTP 地址，协议和路径由业务显式选择，不按 Debug 自动拼接。 */
    val httpBaseUrl: Flow<String> = backup.getStringFlow(HTTP_BASE_URL, "").map { it.orEmpty() }
    val webSocketUrl: Flow<String> = backup.getStringFlow(WEBSOCKET_URL, "").map { it.orEmpty() }
    suspend fun saveHttpBaseUrl(url: String) = backup.putString(HTTP_BASE_URL, url)
    suspend fun saveWebSocketUrl(url: String) = backup.putString(WEBSOCKET_URL, url)

    /** 服务器 IP（Flow，默认为空字符串） */
    val serverIp: Flow<String> = backup.getStringFlow(SERVER_IP, "")
        .map { it ?: "" }
        .distinctUntilChanged()

    /**
     * 同步读取服务器 IP
     * 内部使用 runBlocking，不可在主线程调用
     */
    fun getServerIpSync(): String = runBlocking { backup.getString(SERVER_IP, "") ?: "" }

    /** 保存服务器 IP */
    suspend fun saveServerIp(ip: String) = backup.putString(SERVER_IP, ip)

    // endregion

    // region [Server Port]

    val serverPort: Flow<String> = backup.getStringFlow(SERVER_PORT, "")
        .map { it ?: "" }
        .distinctUntilChanged()

    fun getServerPortSync(): String = runBlocking { backup.getString(SERVER_PORT, "") ?: "" }

    suspend fun saveServerPort(port: String) = backup.putString(SERVER_PORT, port)

    // endregion

    // region [Access Token]

    val accessToken: Flow<String?> = backup.getStringFlow(ACCESS_TOKEN).distinctUntilChanged()

    fun getAccessTokenSync(): String? = runBlocking { backup.getString(ACCESS_TOKEN) }

    suspend fun saveAccessToken(token: String) = backup.putString(ACCESS_TOKEN, token)

    suspend fun clearAccessToken() = backup.remove(ACCESS_TOKEN)

    // endregion

    // region [Language]

    val language: Flow<String> = backup.getStringFlow(LANGUAGE, "")
        .map { it ?: "" }
        .distinctUntilChanged()

    fun getLanguageSync(): String = runBlocking { backup.getString(LANGUAGE, "") ?: "" }

    /** 首帧只读取已有备份；权威值仍通过 language Flow 异步读取。 */
    fun getLanguageBackup(): String = backup.getStringBackup(LANGUAGE, "") ?: ""

    suspend fun saveLanguage(lang: String) = backup.putString(LANGUAGE, lang)

    // endregion

    /**
     * 清除所有框架 Key
     */
    suspend fun clearAll() {
        backup.remove(SERVER_IP)
        backup.remove(SERVER_PORT)
        backup.remove(ACCESS_TOKEN)
        backup.remove(LANGUAGE)
        backup.remove(HTTP_BASE_URL)
        backup.remove(WEBSOCKET_URL)
    }
}
