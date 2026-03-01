package io.legado.app.help.http

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

// Feature: security-hardening, Property 5: 域级 SSL 信任隔离
// **Validates: Requirements 4.2, 4.5**

/**
 * Property-based test verifying that SSLHelper.createPerSourceClient()
 * provides proper domain-level SSL trust isolation.
 *
 * Key invariants:
 * - When enableUnsafeSSL=true, the returned client has different SSL config from base
 * - When enableUnsafeSSL=false, the returned client is the same as base
 * - Creating an unsafe client does NOT modify the base client's SSL settings
 */
class SSLHelperPropertyTest {

    /**
     * Property 5a: For any random SSL bypass configuration, the returned client's
     * SSL configuration must match expectations — unsafe clients differ from base,
     * safe clients are identical to base.
     */
    @Test
    fun property5_perSourceClientSSLConfigMatchesExpectation() {
        val baseClient = OkHttpClient.Builder().build()

        runBlocking {
            checkAll(100, Arb.boolean()) { enableUnsafeSSL ->
                val result = SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL)

                if (enableUnsafeSSL) {
                    assertNotSame(
                        "When enableUnsafeSSL=true, returned client must be a new instance",
                        baseClient,
                        result
                    )
                    assertSame(
                        "Unsafe client must use SSLHelper.unsafeSSLSocketFactory",
                        SSLHelper.unsafeSSLSocketFactory,
                        result.sslSocketFactory
                    )
                    assertSame(
                        "Unsafe client must use SSLHelper.unsafeHostnameVerifier",
                        SSLHelper.unsafeHostnameVerifier,
                        result.hostnameVerifier
                    )
                } else {
                    assertSame(
                        "When enableUnsafeSSL=false, returned client must be the same base instance",
                        baseClient,
                        result
                    )
                }
            }
        }
    }

    /**
     * Property 5b: For any random SSL bypass configuration, creating a per-source
     * client must NEVER modify the base client's SSL settings.
     * This ensures global okHttpClient isolation (Requirement 4.5).
     */
    @Test
    fun property5_perSourceClientNeverModifiesBaseClient() {
        val baseClient = OkHttpClient.Builder().build()
        val originalSslSocketFactory = baseClient.sslSocketFactory
        val originalHostnameVerifier = baseClient.hostnameVerifier

        runBlocking {
            checkAll(100, Arb.boolean()) { enableUnsafeSSL ->
                SSLHelper.createPerSourceClient(baseClient, enableUnsafeSSL)

                assertSame(
                    "Base client sslSocketFactory must remain unchanged after " +
                        "createPerSourceClient(enableUnsafeSSL=$enableUnsafeSSL)",
                    originalSslSocketFactory,
                    baseClient.sslSocketFactory
                )
                assertSame(
                    "Base client hostnameVerifier must remain unchanged after " +
                        "createPerSourceClient(enableUnsafeSSL=$enableUnsafeSSL)",
                    originalHostnameVerifier,
                    baseClient.hostnameVerifier
                )
            }
        }
    }

    /**
     * Property 5c: For any pair of random SSL configurations, per-source clients
     * are isolated from each other — one client's SSL config does not leak to another.
     */
    @Test
    fun property5_multiplePerSourceClientsAreIsolated() {
        val baseClient = OkHttpClient.Builder().build()

        runBlocking {
            checkAll(100, Arb.boolean(), Arb.boolean()) { config1, config2 ->
                val client1 = SSLHelper.createPerSourceClient(baseClient, config1)
                val client2 = SSLHelper.createPerSourceClient(baseClient, config2)

                if (config1 != config2) {
                    // One unsafe, one safe — they must differ
                    assertNotSame(
                        "Clients with different SSL configs (config1=$config1, config2=$config2) " +
                            "must be different instances",
                        client1,
                        client2
                    )
                }

                if (config1 && !config2) {
                    // client1 is unsafe, client2 is safe (same as base)
                    assertSame(
                        "Safe client must be the base client",
                        baseClient,
                        client2
                    )
                    assertNotSame(
                        "Unsafe client's sslSocketFactory must differ from base",
                        baseClient.sslSocketFactory,
                        client1.sslSocketFactory
                    )
                }

                if (!config1 && config2) {
                    // client1 is safe (same as base), client2 is unsafe
                    assertSame(
                        "Safe client must be the base client",
                        baseClient,
                        client1
                    )
                    assertNotSame(
                        "Unsafe client's sslSocketFactory must differ from base",
                        baseClient.sslSocketFactory,
                        client2.sslSocketFactory
                    )
                }
            }
        }
    }
}
