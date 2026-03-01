package io.legado.app

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 签名配置单元测试
 *
 * 验证 APK 签名配置满足安全加固要求：
 * - V1-V4 签名方案均已启用
 * - 签名密钥强度为 RSA 2048 位或更高
 *
 * Validates: Requirements 7.1, 7.2
 */
class SigningConfigTest {

    private lateinit var buildGradleContent: String

    @Before
    fun setUp() {
        val buildGradleFile = File("build.gradle")
        assertTrue("build.gradle should exist", buildGradleFile.exists())
        buildGradleContent = buildGradleFile.readText()
    }

    /**
     * Validates: Requirement 7.1
     * 验证 V1 签名方案已启用
     */
    @Test
    fun signingConfig_enablesV1Signing() {
        assertTrue(
            "Signing config must enable V1 signing (enableV1Signing = true)",
            buildGradleContent.contains("enableV1Signing = true") ||
                buildGradleContent.contains("enableV1Signing true")
        )
    }

    /**
     * Validates: Requirement 7.1
     * 验证 V2 签名方案已启用
     */
    @Test
    fun signingConfig_enablesV2Signing() {
        assertTrue(
            "Signing config must enable V2 signing (enableV2Signing = true)",
            buildGradleContent.contains("enableV2Signing = true") ||
                buildGradleContent.contains("enableV2Signing true")
        )
    }

    /**
     * Validates: Requirement 7.1
     * 验证 V3 签名方案已启用
     */
    @Test
    fun signingConfig_enablesV3Signing() {
        assertTrue(
            "Signing config must enable V3 signing (enableV3Signing = true)",
            buildGradleContent.contains("enableV3Signing = true") ||
                buildGradleContent.contains("enableV3Signing true")
        )
    }

    /**
     * Validates: Requirement 7.1
     * 验证 V4 签名方案已启用
     */
    @Test
    fun signingConfig_enablesV4Signing() {
        assertTrue(
            "Signing config must enable V4 signing (enableV4Signing = true)",
            buildGradleContent.contains("enableV4Signing = true") ||
                buildGradleContent.contains("enableV4Signing true")
        )
    }

    /**
     * Validates: Requirement 7.2
     * 验证签名密钥强度为 RSA 2048 位或更高
     *
     * 通过 keytool 检查 legado-release.jks 的密钥信息
     */
    @Test
    fun signingKey_isRsa2048OrHigher() {
        val keystoreFile = File("../legado-release.jks")
        if (!keystoreFile.exists()) {
            // If keystore doesn't exist in CI, skip but don't fail
            println("WARNING: legado-release.jks not found, skipping key strength verification")
            return
        }

        val process = ProcessBuilder(
            "keytool", "-list", "-v",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", "legado123"
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertTrue(
            "keytool should execute successfully (exit code: $exitCode)",
            exitCode == 0
        )

        // Check for RSA key with 2048 bits or higher
        val keyAlgorithmPattern = Regex("""(\d+)\s*位\s*RSA\s*密钥|(\d+)[\s-]*bit\s*RSA\s*key""", RegexOption.IGNORE_CASE)
        val match = keyAlgorithmPattern.find(output)

        assertTrue(
            "Keystore must contain an RSA key. keytool output:\n$output",
            match != null
        )

        val keySize = (match!!.groupValues[1].ifEmpty { match.groupValues[2] }).toInt()
        assertTrue(
            "RSA key must be 2048 bits or higher, but found $keySize bits",
            keySize >= 2048
        )
    }
}
