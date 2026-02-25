package io.legado.app.model.opds

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OpdsParser.isValidOpdsUrl.
 * Validates: Requirements 6.5
 */
class OpdsParserUrlValidationTest {

    @Test
    fun `http URL with valid host returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("http://example.com/opds"))
    }

    @Test
    fun `https URL with valid host returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("https://example.com/opds"))
    }

    @Test
    fun `https URL with port returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("https://example.com:8080/opds"))
    }

    @Test
    fun `http URL with IP address returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("http://192.168.1.1/opds"))
    }

    @Test
    fun `http URL with localhost returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("http://localhost:8080/opds"))
    }

    @Test
    fun `https root URL returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("https://calibre.example.org"))
    }

    @Test
    fun `ftp scheme returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("ftp://example.com/opds"))
    }

    @Test
    fun `no scheme returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("example.com/opds"))
    }

    @Test
    fun `empty string returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl(""))
    }

    @Test
    fun `blank string returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("   "))
    }

    @Test
    fun `http with no host returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("http://"))
    }

    @Test
    fun `random text returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("not a url at all"))
    }

    @Test
    fun `file scheme returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("file:///etc/passwd"))
    }

    @Test
    fun `javascript scheme returns false`() {
        assertFalse(OpdsParser.isValidOpdsUrl("javascript:alert(1)"))
    }

    @Test
    fun `https URL with path and query returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("https://example.com/opds?page=1"))
    }

    @Test
    fun `HTTP uppercase scheme returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("HTTP://example.com/opds"))
    }

    @Test
    fun `HTTPS uppercase scheme returns true`() {
        assertTrue(OpdsParser.isValidOpdsUrl("HTTPS://example.com/opds"))
    }
}
