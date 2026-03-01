package com.script.rhino

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Feature: security-hardening, Property 1: ClassShutter 白名单阻断性
// **Validates: Requirements 5.1, 5.3, 5.5**

/**
 * Property-based test verifying that RhinoClassShutter enforces
 * whitelist + blacklist dual-check access control.
 *
 * Key invariants:
 * - Class names not matching any whitelist prefix return false
 * - Class names matching a blacklist prefix always return false (blacklist takes priority)
 * - Class names matching a whitelist prefix (and not blacklist) return true
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class RhinoClassShutterPropertyTest {

    // Known whitelist prefixes (subset from RhinoClassShutter.allowedPrefixesMatcher)
    private val whitelistPrefixes = listOf(
        "java.lang.String",
        "java.lang.Integer", "java.lang.Long", "java.lang.Double",
        "java.lang.Boolean", "java.lang.Float", "java.lang.Byte",
        "java.lang.Short", "java.lang.Character",
        "java.lang.Math",
        "java.lang.StringBuilder", "java.lang.Number",
        "java.lang.System",
        "java.util.ArrayList", "java.util.HashMap", "java.util.LinkedHashMap",
        "java.util.LinkedList", "java.util.HashSet", "java.util.TreeMap",
        "java.util.Arrays", "java.util.Collections", "java.util.regex",
        "java.io.PrintStream",
        "java.text.SimpleDateFormat", "java.text.DecimalFormat",
        "java.net.URLEncoder", "java.net.URLDecoder",
        "org.jsoup",
        "cn.hutool.core.codec", "cn.hutool.core.util.HexUtil",
        "cn.hutool.core.util.CharsetUtil", "cn.hutool.core.util.StrUtil",
        "cn.hutool.crypto",
        "io.legado.app",
    )

    // Known blacklist prefixes (from RhinoClassShutter.blockedPrefixesMatcher)
    private val blacklistPrefixes = listOf(
        "java.lang.Runtime", "java.lang.ProcessBuilder",
        "java.lang.reflect", "java.lang.invoke",
        "java.io.File", "java.net.URLClassLoader",
        "dalvik.system", "com.script", "org.mozilla",
        "io.legado.app.data.AppDatabase",
        "io.legado.app.data.dao",
    )

    // Prefixes that are definitely not in the whitelist or blacklist
    private val unknownPrefixes = listOf(
        "com.example.unknown",
        "net.custom.library",
        "org.apache.commons",
        "kotlin.collections",
        "android.app",
        "javax.crypto",
    )

    /**
     * Property 1a: For any random class name string that does not match any
     * whitelist prefix, visibleToScripts() must return false.
     *
     * Strategy: Generate random strings that are unlikely to match whitelist prefixes.
     */
    @Test
    fun property1_randomClassNamesNotInWhitelistReturnFalse() {
        // Use unknown prefixes + random suffix to guarantee non-whitelist names
        val arbNonWhitelistClassName = Arb.element(unknownPrefixes).map { prefix ->
            prefix + "." + java.util.UUID.randomUUID().toString().replace("-", "")
        }

        runBlocking {
            checkAll(100, arbNonWhitelistClassName) { className ->
                assertFalse(
                    "Non-whitelisted class '$className' must be denied access",
                    RhinoClassShutter.visibleToScripts(className)
                )
            }
        }
    }

    /**
     * Property 1b: For any class name matching a blacklist prefix,
     * visibleToScripts() must return false — blacklist takes priority over whitelist.
     */
    @Test
    fun property1_blacklistedClassNamesAlwaysReturnFalse() {
        val arbBlacklistClassName = Arb.element(blacklistPrefixes).map { prefix ->
            prefix + ".SomeClass"
        }

        runBlocking {
            checkAll(100, arbBlacklistClassName) { className ->
                assertFalse(
                    "Blacklisted class '$className' must be denied access regardless of whitelist",
                    RhinoClassShutter.visibleToScripts(className)
                )
            }
        }
    }

    /**
     * Property 1c: For any class name matching a whitelist prefix (and NOT matching
     * any blacklist prefix), visibleToScripts() must return true.
     */
    @Test
    fun property1_whitelistedClassNamesNotInBlacklistReturnTrue() {
        // Filter whitelist prefixes that don't overlap with blacklist
        val safeWhitelistPrefixes = whitelistPrefixes.filter { wp ->
            blacklistPrefixes.none { bp -> wp.startsWith(bp) || bp.startsWith(wp) }
        }

        val arbWhitelistClassName = Arb.element(safeWhitelistPrefixes).map { prefix ->
            prefix + ".SubClass"
        }

        runBlocking {
            checkAll(100, arbWhitelistClassName) { className ->
                assertTrue(
                    "Whitelisted (non-blacklisted) class '$className' must be allowed access",
                    RhinoClassShutter.visibleToScripts(className)
                )
            }
        }
    }

    /**
     * Property 1d: For purely random strings (Arb.string()), visibleToScripts()
     * must return false unless the string happens to match a whitelist prefix
     * and does not match a blacklist prefix.
     *
     * This verifies the default-deny behavior of the whitelist mechanism.
     */
    @Test
    fun property1_arbitraryStringsDefaultDeny() {
        runBlocking {
            checkAll(200, Arb.string(minSize = 1, maxSize = 100)) { randomStr ->
                val result = RhinoClassShutter.visibleToScripts(randomStr)

                val matchesBlacklist = blacklistPrefixes.any { prefix ->
                    randomStr == prefix || (randomStr.startsWith(prefix) && randomStr.getOrNull(prefix.length) == '.')
                }
                val matchesWhitelist = whitelistPrefixes.any { prefix ->
                    randomStr == prefix || (randomStr.startsWith(prefix) && randomStr.getOrNull(prefix.length) == '.')
                }

                if (matchesBlacklist) {
                    assertFalse(
                        "Blacklisted random string '$randomStr' must be denied",
                        result
                    )
                } else if (matchesWhitelist) {
                    assertTrue(
                        "Whitelisted random string '$randomStr' must be allowed",
                        result
                    )
                } else {
                    assertFalse(
                        "Non-whitelisted random string '$randomStr' must be denied (default-deny)",
                        result
                    )
                }
            }
        }
    }
}
