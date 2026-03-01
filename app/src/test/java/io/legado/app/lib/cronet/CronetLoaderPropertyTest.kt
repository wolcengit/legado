package io.legado.app.lib.cronet

import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature: security-hardening, Property 4: Cronet SO 预置加载优先
// **Validates: Requirements 3.1, 3.3**

/**
 * Property-based test verifying that CronetLoader's loading strategy
 * NEVER includes any network requests, regardless of SO availability.
 *
 * The key invariant: for any combination of SO availability,
 * the CronetLoader code path must not contain remote download logic.
 */
class CronetLoaderPropertyTest {

    // Known dangerous URL patterns that indicate remote download behavior
    private val remoteDownloadPatterns = listOf(
        "storage.googleapis.com",
        "downloadFileIfNotExist",
        "downloadFile",
        "HttpURLConnection",
        "URLConnection"
    )

    // Safe pattern that should be present
    private val safeLoadPattern = "System.loadLibrary("

    /**
     * Read the CronetLoader source code for static analysis.
     */
    private fun readCronetLoaderSource(): String {
        val sourceFile = java.io.File("src/main/java/io/legado/app/lib/cronet/CronetLoader.kt")
        assertTrue("CronetLoader.kt source file should exist", sourceFile.exists())
        return sourceFile.readText()
    }

    /**
     * Property 4: For any SO availability scenario (preloaded or not),
     * the CronetLoader loading strategy must NOT contain any network request logic.
     *
     * We use Arb.boolean() to simulate whether SO is preloaded,
     * and verify the source code invariant holds regardless.
     */
    @Test
    fun property4_loadingStrategyNeverIncludesNetworkRequests() {
        runBlocking {
            val source = readCronetLoaderSource()

            checkAll(100, Arb.boolean()) { soPreloaded ->
                // Regardless of whether SO is preloaded or not,
                // the CronetLoader source must not contain remote download patterns
                for (pattern in remoteDownloadPatterns) {
                    assertFalse(
                        "CronetLoader must not contain remote download pattern '$pattern' " +
                            "(soPreloaded=$soPreloaded). Loading strategy should never include network requests.",
                        source.contains(pattern)
                    )
                }
            }
        }
    }

    /**
     * Property 4 (supplementary): For any SO availability scenario,
     * loadLibrary() must only use System.loadLibrary() (no System.load with absolute paths).
     *
     * System.loadLibrary loads from the app's native library directory (safe),
     * while System.load with an absolute path could load from arbitrary locations (unsafe).
     */
    @Test
    fun property4_loadLibraryOnlyUsesSystemLoadLibrary() {
        runBlocking {
            val source = readCronetLoaderSource()

            checkAll(100, Arb.boolean()) { soPreloaded ->
                // The source must contain System.loadLibrary (the safe loading method)
                assertTrue(
                    "CronetLoader must use System.loadLibrary() for safe SO loading " +
                        "(soPreloaded=$soPreloaded)",
                    source.contains(safeLoadPattern)
                )

                // Every System.load* call should be System.loadLibrary
                // If System.load( exists, it must only be as part of System.loadLibrary(
                val loadCalls = Regex("""System\.load\(""").findAll(source)
                val loadLibraryCalls = Regex("""System\.loadLibrary\(""").findAll(source)

                val loadPositions = loadCalls.map { it.range.first }.toSet()
                val loadLibraryPositions = loadLibraryCalls.map { it.range.first }.toSet()

                assertTrue(
                    "All System.load calls must be System.loadLibrary (no absolute path loading). " +
                        "soPreloaded=$soPreloaded",
                    loadPositions.all { pos -> loadLibraryPositions.contains(pos) }
                )
            }
        }
    }
}
