package io.legado.app.model.opds

import io.legado.app.data.entities.OpdsEntry
import io.legado.app.data.entities.OpdsLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OpdsDownloader pure logic methods.
 * Validates: Requirements 4.2, 4.3
 */
class OpdsDownloaderTest {

    @Test
    fun `buildFileName uses entry title and epub extension for epub mime`() {
        val entry = OpdsEntry(id = "1", title = "My Book")
        val link = OpdsLink(href = "http://example.com/book.epub", type = "application/epub+zip")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("My Book.epub", fileName)
    }

    @Test
    fun `buildFileName uses pdf extension for pdf mime`() {
        val entry = OpdsEntry(id = "1", title = "My PDF")
        val link = OpdsLink(href = "http://example.com/book.pdf", type = "application/pdf")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("My PDF.pdf", fileName)
    }

    @Test
    fun `buildFileName uses mobi extension for mobi mime`() {
        val entry = OpdsEntry(id = "1", title = "My Mobi")
        val link = OpdsLink(href = "http://example.com/book.mobi", type = "application/x-mobipocket-ebook")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("My Mobi.mobi", fileName)
    }

    @Test
    fun `buildFileName uses txt extension for text plain mime`() {
        val entry = OpdsEntry(id = "1", title = "Plain Text")
        val link = OpdsLink(href = "http://example.com/book.txt", type = "text/plain")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("Plain Text.txt", fileName)
    }

    @Test
    fun `buildFileName defaults to epub for unknown mime`() {
        val entry = OpdsEntry(id = "1", title = "Unknown")
        val link = OpdsLink(href = "http://example.com/book", type = "application/octet-stream")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("Unknown.epub", fileName)
    }

    @Test
    fun `buildFileName defaults to epub for null mime`() {
        val entry = OpdsEntry(id = "1", title = "No Type")
        val link = OpdsLink(href = "http://example.com/book")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertEquals("No Type.epub", fileName)
    }

    @Test
    fun `buildFileName sanitizes illegal filename characters`() {
        val entry = OpdsEntry(id = "1", title = "Book: A/B\\C*D?E\"F<G>H|I")
        val link = OpdsLink(href = "http://example.com/book.epub", type = "application/epub+zip")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        assertTrue("Should not contain illegal chars", !fileName.contains(Regex("[\\\\/:*?\"<>|]")))
        assertEquals("Book_ A_B_C_D_E_F_G_H_I.epub", fileName)
    }

    @Test
    fun `buildFileName truncates very long titles to 80 chars`() {
        val longTitle = "A".repeat(200)
        val entry = OpdsEntry(id = "1", title = longTitle)
        val link = OpdsLink(href = "http://example.com/book.epub", type = "application/epub+zip")
        val fileName = OpdsDownloader.buildFileName(entry, link)
        // base name is 80 chars + ".epub" = 85
        assertEquals(85, fileName.length)
        assertTrue(fileName.endsWith(".epub"))
    }

    @Test
    fun `mimeToExtension maps all known types`() {
        assertEquals("epub", OpdsDownloader.mimeToExtension("application/epub+zip"))
        assertEquals("pdf", OpdsDownloader.mimeToExtension("application/pdf"))
        assertEquals("mobi", OpdsDownloader.mimeToExtension("application/x-mobipocket-ebook"))
        assertEquals("cbz", OpdsDownloader.mimeToExtension("application/x-cbz"))
        assertEquals("cbr", OpdsDownloader.mimeToExtension("application/x-cbr"))
        assertEquals("txt", OpdsDownloader.mimeToExtension("text/plain"))
    }

    @Test
    fun `mimeToExtension returns epub for null`() {
        assertEquals("epub", OpdsDownloader.mimeToExtension(null))
    }

    @Test
    fun `mimeToExtension returns epub for unknown type`() {
        assertEquals("epub", OpdsDownloader.mimeToExtension("application/unknown"))
    }
}
