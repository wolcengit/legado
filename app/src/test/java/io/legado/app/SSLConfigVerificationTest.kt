package io.legado.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * SSL 配置静态分析单元测试
 *
 * 验证安全加固后的 SSL 相关配置：
 * - 全局 okHttpClient 不使用 unsafeTrustManager
 * - CronetHelper 不包含 disableCertificateVerify 方法
 * - Release network_security_config.xml 禁止明文流量
 *
 * Validates: Requirements 4.1, 4.3, 4.4
 */
class SSLConfigVerificationTest {

    private lateinit var httpHelperSource: String
    private lateinit var cronetHelperSource: String

    @Before
    fun setUp() {
        val httpHelperFile = File("src/main/java/io/legado/app/help/http/HttpHelper.kt")
        assertTrue("HttpHelper.kt source file should exist", httpHelperFile.exists())
        httpHelperSource = httpHelperFile.readText()

        val cronetHelperFile = File("src/main/java/io/legado/app/lib/cronet/CronetHelper.kt")
        assertTrue("CronetHelper.kt source file should exist", cronetHelperFile.exists())
        cronetHelperSource = cronetHelperFile.readText()
    }

    /**
     * Validates: Requirement 4.1
     * 全局 okHttpClient 构建器不应引用 unsafeTrustManager。
     * 系统默认证书校验链应被使用。
     */
    @Test
    fun globalOkHttpClient_doesNotUseUnsafeTrustManager() {
        // Extract the okHttpClient lazy block
        val clientBlockRegex = Regex(
            """val\s+okHttpClient\s*:\s*OkHttpClient\s+by\s+lazy\s*\{([\s\S]*?)\n\}"""
        )
        val match = clientBlockRegex.find(httpHelperSource)
        assertTrue("okHttpClient lazy block should exist in HttpHelper.kt", match != null)

        val clientBlock = match!!.groupValues[1]

        assertFalse(
            "Global okHttpClient must not reference 'unsafeTrustManager' (Requirement 4.1)",
            clientBlock.contains("unsafeTrustManager")
        )
        assertFalse(
            "Global okHttpClient must not call 'sslSocketFactory' with unsafe parameters",
            clientBlock.contains("unsafeSSLSocketFactory")
        )
        assertFalse(
            "Global okHttpClient must not reference 'unsafeHostnameVerifier'",
            clientBlock.contains("unsafeHostnameVerifier")
        )
    }

    /**
     * Validates: Requirement 4.4
     * CronetHelper 不应包含 disableCertificateVerify 方法。
     */
    @Test
    fun cronetHelper_doesNotContainDisableCertificateVerify() {
        assertFalse(
            "CronetHelper must not contain 'disableCertificateVerify' method (Requirement 4.4)",
            Regex("""\bfun\s+disableCertificateVerify\b""").containsMatchIn(cronetHelperSource)
        )
        assertFalse(
            "CronetHelper must not reference 'disableCertificateVerify' at all",
            cronetHelperSource.contains("disableCertificateVerify")
        )
    }

    /**
     * Validates: Requirement 4.4
     * CronetHelper 不应包含通过反射替换 X509Util 信任管理器的代码。
     */
    @Test
    fun cronetHelper_doesNotContainX509UtilReflection() {
        assertFalse(
            "CronetHelper must not reference 'X509Util' (Requirement 4.4)",
            cronetHelperSource.contains("X509Util")
        )
        assertFalse(
            "CronetHelper must not reference 'sDefaultTrustManager' reflection target",
            cronetHelperSource.contains("sDefaultTrustManager")
        )
    }

    /**
     * Validates: Requirement 4.3
     * Release network_security_config.xml 中 cleartextTrafficPermitted 应为 false。
     */
    @Test
    fun releaseNetworkSecurityConfig_disablesCleartextTraffic() {
        val configFile = File("src/release/res/xml/network_security_config.xml")
        assertTrue(
            "Release network_security_config.xml should exist",
            configFile.exists()
        )

        val factory = DocumentBuilderFactory.newInstance()
        val doc = factory.newDocumentBuilder().parse(configFile)

        val baseConfigNodes = doc.getElementsByTagName("base-config")
        assertTrue(
            "network_security_config.xml must contain a <base-config> element",
            baseConfigNodes.length > 0
        )

        val baseConfig = baseConfigNodes.item(0) as org.w3c.dom.Element
        val cleartextPermitted = baseConfig.getAttribute("cleartextTrafficPermitted")

        assertTrue(
            "Release base-config must have cleartextTrafficPermitted attribute",
            cleartextPermitted.isNotEmpty()
        )
        assertTrue(
            "Release base-config cleartextTrafficPermitted must be 'false' (Requirement 4.3)",
            cleartextPermitted == "false"
        )
    }
}
