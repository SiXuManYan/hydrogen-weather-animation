package com.template.framework.api

import com.template.framework.network.HttpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只检查工厂配置，不初始化 Application，也不发起实际网络请求。 */
class NetworkModuleTest {
    @Test
    fun defaultClientHasNoBusinessInterceptors() {
        val client = NetworkModule.createOkHttpClient(debug = false)
        assertTrue(client.interceptors.isEmpty())
    }

    @Test
    fun loggingAndAuthAreOptIn() {
        val client = NetworkModule.createOkHttpClient(
            debug = true, getToken = { "example_token" }, clearToken = {}, includeVersionHeaders = true
        )
        assertEquals(4, client.interceptors.size)
        assertTrue(client.interceptors.any { it is TokenInterceptor })
    }

    @Test
    fun retrofitReusesSuppliedClientAndNormalizesUrl() {
        val client = NetworkModule.createOkHttpClient(debug = false, writeTimeoutSeconds = null)
        val retrofit = NetworkModule.createRetrofit("https://com.exmpale.xxxx", client)
        assertSame(client, retrofit.callFactory())
        assertEquals("https://com.exmpale.xxxx/", retrofit.baseUrl().toString())
        assertEquals(10_000, client.writeTimeoutMillis)
    }

    @Test
    fun sharedHttpInitializationDoesNotAlterExternalClient() {
        val client = NetworkModule.createOkHttpClient(debug = false)
        NetworkModule.init(HttpConfig(baseUrl = "https://com.exmpale.xxxx/", client = client, debug = true))
        assertSame(client, NetworkModule.getClient())
        assertTrue(client.interceptors.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun baseUrlCannotContainCredentials() {
        HttpConfig("https://example_user@com.exmpale.xxxx/")
    }
}
