package io.legado.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ProGuard 规则单元测试
 *
 * 验证 proguard-rules.pro 中包含关键的安全加固规则：
 * - 包层级扁平化规则
 * - Log 移除规则（所有日志等级）
 * - 不包含过于宽泛的 keep 规则
 *
 * Validates: Requirements 1.1, 1.3, 1.5
 */
class ProGuardRulesTest {

    private lateinit var proguardContent: String

    @Before
    fun setUp() {
        val proguardFile = File("proguard-rules.pro")
        assertTrue("proguard-rules.pro should exist", proguardFile.exists())
        proguardContent = proguardFile.readText()
    }

    /**
     * Validates: Requirement 1.3
     * 混淆器应将包层级扁平化，使混淆后的类分布在单一或少量包路径下。
     */
    @Test
    fun proguardRules_containsFlattenPackageHierarchy() {
        val hasRule = proguardContent.lines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && trimmed.contains("-flattenpackagehierarchy")
        }
        assertTrue(
            "proguard-rules.pro must contain '-flattenpackagehierarchy' rule (Requirement 1.3)",
            hasRule
        )
    }

    /**
     * Validates: Requirement 1.5
     * 混淆器应移除 release 构建产物中所有 android.util.Log 的调用。
     * 验证 -assumenosideeffects 规则存在且覆盖所有日志等级 (v, i, w, d, e)。
     */
    @Test
    fun proguardRules_containsLogRemovalRule() {
        val hasLogRule = proguardContent.contains("-assumenosideeffects class android.util.Log")
        assertTrue(
            "proguard-rules.pro must contain '-assumenosideeffects class android.util.Log' rule (Requirement 1.5)",
            hasLogRule
        )

        // Extract the assumenosideeffects block for android.util.Log
        val blockRegex = Regex(
            """-assumenosideeffects\s+class\s+android\.util\.Log\s*\{([\s\S]*?)\}"""
        )
        val match = blockRegex.find(proguardContent)
        assertTrue(
            "Log removal rule must have a method block defining which methods to strip",
            match != null
        )

        val blockContent = match!!.groupValues[1]
        val requiredLevels = listOf("v", "i", "w", "d", "e")
        for (level in requiredLevels) {
            assertTrue(
                "Log removal rule must include log level '$level(...)' (Requirement 1.5)",
                blockContent.contains("int $level(")
            )
        }
    }

    /**
     * Validates: Requirement 1.1
     * ProGuard 规则不应包含过于宽泛的 keep 规则（不带 allowobfuscation 的全量保留）。
     * 例如 `-keep class okhttp3.* { *; }` 会阻止混淆，应使用 `-keep,allowobfuscation` 代替。
     */
    @Test
    fun proguardRules_doesNotContainOverlyBroadKeepRules() {
        // Check each non-comment line for broad keep rules without allowobfuscation
        val broadKeepPattern = Regex(
            """-keep\s+class\s+(okhttp3|okio|com\.jayway\.jsonpath|org\.jsoup)\.\*\*?\s*\{[^}]*\*;\s*\}"""
        )
        val lines = proguardContent.lines()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) continue
            if (broadKeepPattern.containsMatchIn(trimmed)) {
                assertFalse(
                    "Line ${index + 1}: Found overly broad keep rule without 'allowobfuscation': '$trimmed'. " +
                        "Use '-keep,allowobfuscation' for internal classes (Requirement 1.1)",
                    !trimmed.contains("allowobfuscation")
                )
            }
        }
    }
}
