package io.legado.app.lib.cronet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for CronetLoader security hardening.
 * These are static analysis tests that read the source file and verify patterns.
 *
 * Validates: Requirements 3.1, 3.3
 */
class CronetLoaderTest {

    private lateinit var sourceContent: String

    @Before
    fun setUp() {
        val sourceFile = File("src/main/java/io/legado/app/lib/cronet/CronetLoader.kt")
        assertTrue("CronetLoader.kt source file should exist", sourceFile.exists())
        sourceContent = sourceFile.readText()
    }

    /**
     * Verify that CronetLoader does not contain the storage.googleapis.com URL.
     * Requirement 3.3: Remove remote download logic from storage.googleapis.com.
     */
    @Test
    fun sourceDoesNotContainGoogleApisUrl() {
        assertFalse(
            "CronetLoader must not contain 'storage.googleapis.com' URL (Requirement 3.3)",
            sourceContent.contains("storage.googleapis.com")
        )
    }

    /**
     * Verify that loadLibrary() only calls System.loadLibrary() and never System.load()
     * with an absolute path.
     * Requirement 3.1: Use pre-bundled SO libraries via System.loadLibrary().
     */
    @Test
    fun loadLibraryOnlyCallsSystemLoadLibrary() {
        // Extract the loadLibrary method body
        val loadLibraryMethodRegex = Regex(
            """override\s+fun\s+loadLibrary\s*\([^)]*\)\s*\{([\s\S]*?)\n    \}"""
        )
        val match = loadLibraryMethodRegex.find(sourceContent)
        assertTrue("loadLibrary() method should exist in CronetLoader", match != null)

        val methodBody = match!!.groupValues[1]

        // Must contain System.loadLibrary call
        assertTrue(
            "loadLibrary() must call System.loadLibrary()",
            methodBody.contains("System.loadLibrary(")
        )

        // Must NOT contain System.load( without "Library" suffix (absolute path loading)
        val systemLoadCalls = Regex("""System\.load\(""").findAll(methodBody).toList()
        val systemLoadLibraryCalls = Regex("""System\.loadLibrary\(""").findAll(methodBody).toList()

        // Every System.load( must be part of System.loadLibrary(
        val loadPositions = systemLoadCalls.map { it.range.first }.toSet()
        val loadLibraryPositions = systemLoadLibraryCalls.map { it.range.first }.toSet()

        assertTrue(
            "loadLibrary() must only use System.loadLibrary(), not System.load() with absolute paths",
            loadPositions.all { pos -> loadLibraryPositions.contains(pos) }
        )
    }

    /**
     * Verify that no remote download-related methods remain in the source.
     * Requirement 3.3: Remove all remote download logic.
     */
    @Test
    fun sourceDoesNotContainDownloadMethods() {
        val downloadPatterns = listOf(
            "downloadFileIfNotExist",
            "fun download(",
            "HttpURLConnection",
            "URLConnection"
        )
        for (pattern in downloadPatterns) {
            assertFalse(
                "CronetLoader must not contain download-related pattern '$pattern'",
                sourceContent.contains(pattern)
            )
        }
    }

    /**
     * Verify that no soUrl or downloadFile fields remain in the source.
     * Requirement 3.3: Remove download-related fields.
     */
    @Test
    fun sourceDoesNotContainDownloadFields() {
        assertFalse(
            "CronetLoader must not contain 'soUrl' field",
            Regex("""\bsoUrl\b""").containsMatchIn(sourceContent)
        )
        assertFalse(
            "CronetLoader must not contain 'downloadFile' field",
            Regex("""\bdownloadFile\b""").containsMatchIn(sourceContent)
        )
    }
}
