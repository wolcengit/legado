package io.legado.app.model.opds

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

/**
 * Property 5: OpenSearch URL template substitution
 *
 * For any valid OpenSearch URL template containing {searchTerms}
 * and any non-empty search keyword, the constructed search URL
 * should contain the URL-encoded keyword and the {searchTerms}
 * placeholder should be fully replaced.
 *
 * **Validates: Requirements 5.2**
 */
class OpdsParserSearchUrlPropertyTest {

    // --- Generators ---

    /** Generate a random URL path segment (alphanumeric, safe for URLs) */
    private val arbPathSegment: Arb<String> = Arb.string(3..15).map { raw ->
        raw.filter { it.isLetterOrDigit() }.take(10).ifEmpty { "path" }
    }

    /** Generate random URL templates containing {searchTerms} */
    private val arbTemplate: Arb<String> = arbitrary {
        val host = arbPathSegment.bind()
        val path = arbPathSegment.bind()
        val scheme = Arb.element("http", "https").bind()
        val style = Arb.int(0..2).bind()
        when (style) {
            0 -> "$scheme://$host.example.com/$path?q={searchTerms}"
            1 -> "$scheme://$host.example.com/search/{searchTerms}"
            else -> "$scheme://$host.example.com/$path?query={searchTerms}&page=1"
        }
    }

    /** Generate non-empty search keywords including special characters */
    private val arbQuery: Arb<String> = arbitrary {
        val style = Arb.int(0..3).bind()
        when (style) {
            0 -> arbPathSegment.bind() // simple alphanumeric
            1 -> {
                // keywords with spaces
                val w1 = arbPathSegment.bind()
                val w2 = arbPathSegment.bind()
                "$w1 $w2"
            }
            2 -> Arb.element(
                "科幻小说",
                "日本語テスト",
                "café résumé",
                "Ünïcödé"
            ).bind()
            else -> Arb.element(
                "hello&world",
                "a+b=c",
                "test@email",
                "100%done",
                "path/to/book"
            ).bind()
        }
    }

    // --- Property test ---

    @Test
    fun searchUrlTemplateSubstitution() {
        runBlocking {
            checkAll(100, arbTemplate, arbQuery) { template, query ->
                val result = OpdsParser.buildSearchUrl(template, query)
                val encoded = URLEncoder.encode(query, "UTF-8")

                // The result should contain the URL-encoded keyword
                assertTrue(
                    "Result URL should contain the URL-encoded query " +
                        "'$encoded', but was: $result",
                    result.contains(encoded)
                )

                // The {searchTerms} placeholder should be fully replaced
                assertTrue(
                    "Result URL should not contain {searchTerms} placeholder, " +
                        "but was: $result",
                    !result.contains("{searchTerms}")
                )

                // The non-template parts of the URL should be preserved
                val prefix = template.substringBefore("{searchTerms}")
                assertTrue(
                    "URL prefix before placeholder should be preserved",
                    result.startsWith(prefix)
                )

                val suffix = template.substringAfter("{searchTerms}")
                assertTrue(
                    "URL suffix after placeholder should be preserved",
                    result.endsWith(suffix)
                )
            }
        }
    }
}
