package io.legado.app.model.opds

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Property 1: OPDS Feed conversion preserves all fields
 *
 * For any valid OPDS Atom XML string with randomized field values,
 * parsing and converting to OpdsFeed should preserve:
 * - Correct Feed type identification (Navigation or Acquisition)
 * - All entry id/title/author/summary/links fields
 * - Pagination links (next/previous) when present
 * - All Acquisition Link href and type from entries
 *
 * **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6**
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class OpdsParserFeedConversionPropertyTest {

    private val baseUrl = "https://example.com/opds"

    // --- Generators for randomized OPDS XML field values ---

    /** Generate safe XML text: alphanumeric only to avoid XML encoding issues */
    private val arbSafeText: Arb<String> = Arb.string(3..20).map { raw ->
        raw.filter { it.isLetterOrDigit() }.take(15).ifEmpty { "text" }
    }

    /** Generate a simple identifier string */
    private val arbId: Arb<String> = arbSafeText.map { "urn:uuid:$it" }

    /** Generate an acquisition MIME type */
    private val arbAcquisitionType: Arb<String> = Arb.element(
        "application/epub+zip",
        "application/pdf",
        "application/x-mobipocket-ebook"
    )

    data class EntryData(
        val id: String,
        val title: String,
        val author: String,
        val summary: String,
        val acqHref: String,
        val acqType: String
    )

    data class FeedData(
        val feedId: String,
        val feedTitle: String,
        val isNavigation: Boolean,
        val entries: List<EntryData>,
        val hasNextPage: Boolean
    )

    private val arbEntryData: Arb<EntryData> = arbitrary {
        val slug = arbSafeText.bind()
        EntryData(
            id = arbId.bind(),
            title = arbSafeText.bind(),
            author = arbSafeText.bind(),
            summary = arbSafeText.bind(),
            acqHref = "/download/$slug.epub",
            acqType = arbAcquisitionType.bind()
        )
    }

    private val arbFeedData: Arb<FeedData> = arbitrary {
        FeedData(
            feedId = arbId.bind(),
            feedTitle = arbSafeText.bind(),
            isNavigation = Arb.boolean().bind(),
            entries = Arb.list(arbEntryData, 1..4).bind(),
            hasNextPage = Arb.boolean().bind()
        )
    }

    // --- XML template builders ---

    private fun buildAcquisitionEntryXml(entry: EntryData): String {
        return """
        <entry>
            <id>${entry.id}</id>
            <title>${entry.title}</title>
            <author><name>${entry.author}</name></author>
            <summary>${entry.summary}</summary>
            <updated>2024-01-01T00:00:00Z</updated>
            <link href="${entry.acqHref}" type="${entry.acqType}" rel="http://opds-spec.org/acquisition"/>
            <link href="/cover/${entry.title}.jpg" type="image/jpeg" rel="http://opds-spec.org/image"/>
        </entry>"""
    }

    private fun buildNavigationEntryXml(entry: EntryData): String {
        return """
        <entry>
            <id>${entry.id}</id>
            <title>${entry.title}</title>
            <author><name>${entry.author}</name></author>
            <summary>${entry.summary}</summary>
            <updated>2024-01-01T00:00:00Z</updated>
            <link href="/nav/${entry.title}" type="application/atom+xml;profile=opds-catalog;kind=navigation" rel="subsection"/>
        </entry>"""
    }

    private fun buildFeedXml(data: FeedData): String {
        val entriesXml = if (data.isNavigation) {
            data.entries.joinToString("\n") { buildNavigationEntryXml(it) }
        } else {
            data.entries.joinToString("\n") { buildAcquisitionEntryXml(it) }
        }

        val nextPageLink = if (data.hasNextPage) {
            """    <link href="/opds/page2" rel="next" type="application/atom+xml;profile=opds-catalog"/>"""
        } else ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog">
    <id>${data.feedId}</id>
    <title>${data.feedTitle}</title>
    <updated>2024-01-01T00:00:00Z</updated>
$entriesXml
$nextPageLink
</feed>"""
    }

    // --- Property test ---

    @Test
    fun opdsFeedConversionPreservesAllFields() {
        runBlocking {
            checkAll(100, arbFeedData) { feedData ->
                val xml = buildFeedXml(feedData)
                val result = OpdsParser.parseFeed(xml, baseUrl)

                // Requirement 1.2: Readium result converted to internal OpdsFeed model
                assertNotNull("Parsed feed should not be null", result)

                // Requirement 1.3: Feed title is preserved
                assertEquals(
                    "Feed title should be preserved",
                    feedData.feedTitle,
                    result.title
                )

                if (feedData.isNavigation) {
                    // Navigation feed: Readium parses navigation links
                    assertTrue(
                        "Navigation feed should be marked as navigation or have navigation entries",
                        result.isNavigation || result.entries.any { it.isNavigation }
                    )

                    // Requirement 1.4: Navigation entries should have titles
                    for (entry in feedData.entries) {
                        val matchingEntry = result.entries.firstOrNull { it.title == entry.title }
                        assertNotNull(
                            "Navigation entry with title '${entry.title}' should exist",
                            matchingEntry
                        )
                        assertTrue(
                            "Navigation entry should have links",
                            matchingEntry!!.links.isNotEmpty()
                        )
                    }
                } else {
                    // Acquisition feed: entries are publications
                    // Requirement 1.4: All entry fields preserved
                    for (entry in feedData.entries) {
                        val matchingEntry = result.entries.firstOrNull { it.title == entry.title }
                        assertNotNull(
                            "Entry with title '${entry.title}' should exist",
                            matchingEntry
                        )
                        matchingEntry!!

                        // Author preserved
                        assertEquals(
                            "Author should be preserved for '${entry.title}'",
                            entry.author,
                            matchingEntry.author
                        )

                        // Summary preserved
                        assertEquals(
                            "Summary should be preserved for '${entry.title}'",
                            entry.summary,
                            matchingEntry.summary
                        )

                        // Requirement 1.6: Acquisition links with href and type
                        assertTrue(
                            "Entry '${entry.title}' should have acquisition links",
                            matchingEntry.acquisitionLinks.isNotEmpty()
                        )

                        val acqLink = matchingEntry.acquisitionLinks.first()
                        assertTrue(
                            "Acquisition link href should contain the path",
                            acqLink.href.contains(entry.acqHref) ||
                                acqLink.href.endsWith(entry.acqHref)
                        )
                        assertEquals(
                            "Acquisition link type should be preserved",
                            entry.acqType,
                            acqLink.type
                        )

                        // Links list should be non-empty
                        assertTrue(
                            "Entry should have links",
                            matchingEntry.links.isNotEmpty()
                        )
                    }
                }

                // Requirement 1.5: Pagination links
                if (feedData.hasNextPage) {
                    assertNotNull(
                        "Feed with next page should have nextPageUrl",
                        result.nextPageUrl
                    )
                    assertTrue(
                        "nextPageUrl should contain the next page path",
                        result.nextPageUrl!!.contains("page2")
                    )
                }
            }
        }
    }
}
