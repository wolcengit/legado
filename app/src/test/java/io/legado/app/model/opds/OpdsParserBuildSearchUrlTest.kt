package io.legado.app.model.opds

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for OpdsParser.buildSearchUrl.
 * Validates: Requirements 5.2
 */
class OpdsParserBuildSearchUrlTest {

    @Test
    fun `replaces searchTerms placeholder with query`() {
        val result = OpdsParser.buildSearchUrl(
            "https://example.com/search?q={searchTerms}",
            "kotlin"
        )
        assertEquals("https://example.com/search?q=kotlin", result)
    }

    @Test
    fun `URL-encodes spaces in query`() {
        val result = OpdsParser.buildSearchUrl(
            "https://example.com/search?q={searchTerms}",
            "hello world"
        )
        assertEquals("https://example.com/search?q=hello+world", result)
    }

    @Test
    fun `URL-encodes special characters`() {
        val result = OpdsParser.buildSearchUrl(
            "https://example.com/search?q={searchTerms}",
            "a&b=c"
        )
        assertEquals("https://example.com/search?q=a%26b%3Dc", result)
    }

    @Test
    fun `URL-encodes unicode characters`() {
        val result = OpdsParser.buildSearchUrl(
            "https://example.com/search?q={searchTerms}",
            "三体"
        )
        // URLEncoder.encode with UTF-8 encodes each byte of the UTF-8 representation
        assert(result.startsWith("https://example.com/search?q="))
        assert(!result.contains("三体"))
        // Decode back to verify round-trip
        val decoded = java.net.URLDecoder.decode(
            result.removePrefix("https://example.com/search?q="), "UTF-8"
        )
        assertEquals("三体", decoded)
    }

    @Test
    fun `template without placeholder returns template unchanged`() {
        val template = "https://example.com/search?q=fixed"
        val result = OpdsParser.buildSearchUrl(template, "anything")
        assertEquals(template, result)
    }

    @Test
    fun `handles empty query`() {
        val result = OpdsParser.buildSearchUrl(
            "https://example.com/search?q={searchTerms}",
            ""
        )
        assertEquals("https://example.com/search?q=", result)
    }
}
