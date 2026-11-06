package com.template.framework.network

import android.content.Context
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** 独立 TLS 工具，HTTP 与 WebSocket 只共享证书策略，不共享初始化。 */
object TlsConfig {
    /** 指定证书替代系统信任库；仍保留 OkHttp 默认的主机名校验。 */
    fun trustCertificate(builder: OkHttpClient.Builder, context: Context, rawResId: Int) {
        val certificate = context.resources.openRawResource(rawResId).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("custom_ca", certificate)
        }
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }.trustManagers.filterIsInstance<X509TrustManager>().single()
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    }
}
