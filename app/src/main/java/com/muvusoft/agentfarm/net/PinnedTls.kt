package com.muvusoft.agentfarm.net

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * A farm's certificate is its own (self-signed on a LAN); no CA vouches for it. The one thing trusted is
 * the SHA-256 fingerprint the QR carried: a certificate with any other fingerprint is refused, so the
 * host name is not what identifies the farm and is not checked.
 */
object PinnedTls {
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    class PinTrust(private val fp: String) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
            if (fingerprint(leaf) != fp.lowercase()) throw CertificateException("certificate fingerprint mismatch")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("client certificates are not used")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun client(fp: String, connectTimeoutMs: Long = 5_000): OkHttpClient {
        val trust = PinTrust(fp)
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    /** Whether a failure was the pin refusing the certificate (a different answer than "unreachable"). */
    fun isPinFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is CertificateException && it.message?.contains("fingerprint") == true }
}
