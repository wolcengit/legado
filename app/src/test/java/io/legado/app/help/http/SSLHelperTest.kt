package io.legado.app.help.http

import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SSLHelper.createPerSourceClient().
 *
 * Validates: Requirements 4.2, 4.5
 */
class SSLHelperTest {

    private val baseClient = OkHttpClient.Builder().build()

    /**
     * When enableUnsafeSSL is false, the exact same base client instance is returned.
     */
    @Test
    fun createPerSourceClient_returnsSameInstance_whenUnsafeDisabled() {
        val result = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = false)
        assertSame(
            "Should return the exact same base client when SSL bypass is disabled",
            baseClient,
            result
        )
    }

    /**
     * When enableUnsafeSSL is true, a new distinct client instance is returned.
     */
    @Test
    fun createPerSourceClient_returnsNewInstance_whenUnsafeEnabled() {
        val result = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = true)
        assertNotSame(
            "Should return a new client instance when SSL bypass is enabled",
            baseClient,
            result
        )
    }

    /**
     * The unsafe client uses the unsafeHostnameVerifier.
     */
    @Test
    fun createPerSourceClient_usesUnsafeHostnameVerifier_whenUnsafeEnabled() {
        val result = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = true)
        assertSame(
            "Unsafe client should use SSLHelper.unsafeHostnameVerifier",
            SSLHelper.unsafeHostnameVerifier,
            result.hostnameVerifier
        )
    }

    /**
     * The unsafe client uses the unsafeSSLSocketFactory.
     */
    @Test
    fun createPerSourceClient_usesUnsafeSSLSocketFactory_whenUnsafeEnabled() {
        val result = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = true)
        assertSame(
            "Unsafe client should use SSLHelper.unsafeSSLSocketFactory",
            SSLHelper.unsafeSSLSocketFactory,
            result.sslSocketFactory
        )
    }

    /**
     * The base client's SSL configuration is NOT modified after creating an unsafe per-source client.
     * This ensures per-source isolation (Requirement 4.5).
     */
    @Test
    fun createPerSourceClient_doesNotModifyBaseClient() {
        val originalHostnameVerifier = baseClient.hostnameVerifier
        val originalSslSocketFactory = baseClient.sslSocketFactory

        SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = true)

        assertSame(
            "Base client hostnameVerifier must remain unchanged",
            originalHostnameVerifier,
            baseClient.hostnameVerifier
        )
        assertSame(
            "Base client sslSocketFactory must remain unchanged",
            originalSslSocketFactory,
            baseClient.sslSocketFactory
        )
    }

    /**
     * Multiple per-source clients are independent of each other.
     */
    @Test
    fun createPerSourceClient_multipleClients_areIndependent() {
        val unsafeClient = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = true)
        val safeClient = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL = false)

        assertNotSame(
            "Unsafe and safe clients should be different instances",
            unsafeClient,
            safeClient
        )
        assertSame(
            "Safe client should be the base client",
            baseClient,
            safeClient
        )
        assertNotSame(
            "Unsafe client hostnameVerifier should differ from base",
            baseClient.hostnameVerifier,
            unsafeClient.hostnameVerifier
        )
    }
}
