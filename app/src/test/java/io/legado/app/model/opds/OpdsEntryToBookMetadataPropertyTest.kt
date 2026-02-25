package io.legado.app.model.opds

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.OpdsEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * Property 4: OpdsEntry to Book metadata preservation
 *
 * For any OpdsEntry with title, author, summary, and coverUrl,
 * after calling OpdsDownloader.fillMetadata(book, entry),
 * the Book's name, author, intro, and coverUrl fields should match
 * the corresponding OpdsEntry values (when non-blank/non-null).
 *
 * **Validates: Requirements 4.3**
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class OpdsEntryToBookMetadataPropertyTest {

    @Before
    fun setUp() {
        // Use plain Application to avoid App.onCreate() side effects.
        // Initialize Splitties appCtx so that appDb (used by book.save()) can be created.
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    /**
     * Generate non-blank strings suitable for metadata fields.
     * Uses alphanumeric characters to avoid encoding issues.
     */
    private val arbNonBlankText: Arb<String> = Arb.string(3..50).map { raw ->
        raw.filter { it.isLetterOrDigit() || it == ' ' }.trim().ifEmpty { "value" }
    }

    /** Generate a URL-like string for cover URLs */
    private val arbCoverUrl: Arb<String> = arbNonBlankText.map { "https://example.com/covers/$it.jpg" }

    /** Generate a random OpdsEntry with non-blank metadata fields */
    private val arbOpdsEntry: Arb<OpdsEntry> = arbitrary {
        OpdsEntry(
            id = arbNonBlankText.bind(),
            title = arbNonBlankText.bind(),
            author = arbNonBlankText.orNull(0.1).bind(),
            summary = arbNonBlankText.orNull(0.1).bind(),
            coverUrl = arbCoverUrl.orNull(0.1).bind()
        )
    }

    @Test
    fun fillMetadataPreservesOpdsEntryFieldsInBook() {
        runBlocking {
            checkAll(100, arbOpdsEntry) { entry ->
                // Create a fresh Book with a unique bookUrl so save() does insert
                val book = Book(bookUrl = "opds://test/${entry.id}/${System.nanoTime()}")

                OpdsDownloader.fillMetadata(book, entry)

                // Title is always non-blank in our generator, so name must match
                if (entry.title.isNotBlank()) {
                    assertEquals(
                        "book.name should equal entry.title",
                        entry.title,
                        book.name
                    )
                }

                // Author: only set when non-null and non-blank
                if (!entry.author.isNullOrBlank()) {
                    assertEquals(
                        "book.author should equal entry.author",
                        entry.author,
                        book.author
                    )
                }

                // Summary -> intro: only set when non-null and non-blank
                if (!entry.summary.isNullOrBlank()) {
                    assertEquals(
                        "book.intro should equal entry.summary",
                        entry.summary,
                        book.intro
                    )
                }

                // CoverUrl: only set when non-null and non-blank
                if (!entry.coverUrl.isNullOrBlank()) {
                    assertEquals(
                        "book.coverUrl should equal entry.coverUrl",
                        entry.coverUrl,
                        book.coverUrl
                    )
                }
            }
        }
    }
}
