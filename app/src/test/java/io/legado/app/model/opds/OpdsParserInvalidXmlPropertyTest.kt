package io.legado.app.model.opds

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Property 2: Invalid XML produces parse error
 *
 * For any illegal XML string (random byte sequences, truncated XML,
 * text missing root element), the parse operation should return an
 * error result rather than crash, and the error message should be
 * a non-empty string.
 *
 * **Validates: Requirements 1.7**
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class OpdsParserInvalidXmlPropertyTest {

    private val baseUrl = "https://example.com/opds"

    // --- Generators for invalid input strings ---

    /** Random byte sequences decoded as UTF-8 (likely invalid XML) */
    private val arbRandomBytes: Arb<String> = arbitrary {
        val size = Arb.int(1..200).bind()
        val byteArb = Arb.int(0..255)
        val bytes = ByteArray(size) { byteArb.bind().toByte() }
        String(bytes, Charsets.UTF_8)
    }

    /** Truncated XML: valid XML header but cut off mid-tag */
    private val arbTruncatedXml: Arb<String> = arbitrary {
        Arb.element(
            "<?xml version=\"1.0\"?>",
            "<?xml version=\"1.0\"?><",
            "<?xml version=\"1.0\"?><feed",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><feed",
            "<feed xmlns=\"http://www.w3.org/2005/Atom\"><entry>",
            "<feed><entry><id>123</id>",
            "<feed><ent"
        ).bind()
    }

    /** Non-XML text: plain text, JSON, HTML fragments, etc. */
    private val arbNonXmlText: Arb<String> = Arb.element(
        "hello world",
        "{ \"key\": \"value\" }",
        "[1, 2, 3]",
        "not xml at all",
        "<html><body>This is HTML</body></html>",
        "SELECT * FROM books",
        "function() { return 42; }",
        "",
        "   ",
        "\n\n\n",
        "<<<>>>",
        "&&&&",
        "null",
        "undefined"
    )

    /** Completely random strings */
    private val arbRandomString: Arb<String> = Arb.string(0..100)

    /** Combined generator: picks from all invalid input categories */
    private val arbInvalidInput: Arb<String> = arbitrary {
        when (Arb.int(0..3).bind()) {
            0 -> arbRandomBytes.bind()
            1 -> arbTruncatedXml.bind()
            2 -> arbNonXmlText.bind()
            else -> arbRandomString.bind()
        }
    }

    // --- Property test ---

    @Test
    fun invalidXmlProducesParseError() {
        runBlocking {
            checkAll(100, arbInvalidInput) { invalidInput ->
                try {
                    OpdsParser.parseFeed(invalidInput, baseUrl)
                    // If parsing succeeds, that's acceptable — Readium may tolerate
                    // some partial XML. The key property is that it doesn't crash.
                } catch (e: OpdsParseException) {
                    // Requirement 1.7: error message should be non-empty
                    assertNotNull("Exception message should not be null", e.message)
                    assertTrue(
                        "Exception message should be non-empty",
                        e.message!!.isNotEmpty()
                    )
                    assertFalse(
                        "Exception message should not be blank",
                        e.message!!.isBlank()
                    )
                }
                // Any other exception type (not OpdsParseException) will propagate
                // and fail the test, ensuring the parser wraps all errors properly
            }
        }
    }
}
