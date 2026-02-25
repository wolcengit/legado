package io.legado.app.model.opds

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * Property 6: URL validation correctness
 *
 * For any string, the URL validation function should return true
 * for strings starting with http:// or https:// that have a valid
 * (non-empty) hostname, and false for all other strings.
 *
 * **Validates: Requirements 6.5**
 */
class OpdsParserUrlValidationPropertyTest {

    // --- Generators ---

    /** Generate a valid hostname (alphanumeric segments joined by dots) */
    private val arbHostname: Arb<String> = arbitrary {
        val segCount = Arb.int(1..3).bind()
        val segments = (1..segCount).map {
            Arb.string(2..8).bind().filter { c -> c.isLetterOrDigit() }.ifEmpty { "host" }
        }
        val tld = Arb.element("com", "org", "net", "io", "de", "cn").bind()
        segments.joinToString(".") + ".$tld"
    }

    /** Generate an optional path segment */
    private val arbPath: Arb<String> = arbitrary {
        val hasPath = Arb.int(0..2).bind()
        when (hasPath) {
            0 -> ""
            1 -> "/" + Arb.string(1..10).bind().filter { it.isLetterOrDigit() }.ifEmpty { "path" }
            else -> {
                val seg1 = Arb.string(1..6).bind().filter { it.isLetterOrDigit() }.ifEmpty { "a" }
                val seg2 = Arb.string(1..6).bind().filter { it.isLetterOrDigit() }.ifEmpty { "b" }
                "/$seg1/$seg2"
            }
        }
    }

    /** Generate valid OPDS URLs (http or https with valid hostname) */
    private val arbValidUrl: Arb<String> = arbitrary {
        val scheme = Arb.element("http", "https").bind()
        val host = arbHostname.bind()
        val path = arbPath.bind()
        "$scheme://$host$path"
    }

    /** Generate strings that should fail URL validation */
    private val arbInvalidUrl: Arb<String> = arbitrary {
        val style = Arb.int(0..7).bind()
        when (style) {
            0 -> "" // empty string
            1 -> Arb.string(1..30).bind() // random string, no scheme
            2 -> "ftp://" + arbHostname.bind() // wrong scheme
            3 -> "http://" // http with no host
            4 -> "https://" // https with no host
            5 -> "mailto:user@example.com" // different URI scheme
            6 -> "file:///tmp/test" // file scheme
            7 -> "not a url at all!" // clearly not a URL
            else -> "garbage"
        }
    }

    // --- Property tests ---

    @Test
    fun validUrlsAreAccepted() {
        runBlocking {
            checkAll(100, arbValidUrl) { url ->
                assertTrue(
                    "Valid URL should be accepted: $url",
                    OpdsParser.isValidOpdsUrl(url)
                )
            }
        }
    }

    @Test
    fun invalidUrlsAreRejected() {
        runBlocking {
            checkAll(100, arbInvalidUrl) { url ->
                assertFalse(
                    "Invalid URL should be rejected: $url",
                    OpdsParser.isValidOpdsUrl(url)
                )
            }
        }
    }

    @Test
    fun validationNeverThrows() {
        runBlocking {
            checkAll(100, Arb.string(0..100)) { input ->
                // Should never throw, regardless of input
                val result = OpdsParser.isValidOpdsUrl(input)
                // Result is a boolean — just verify it doesn't crash
                assertTrue(
                    "isValidOpdsUrl should return a boolean without throwing",
                    result || !result
                )
            }
        }
    }
}
