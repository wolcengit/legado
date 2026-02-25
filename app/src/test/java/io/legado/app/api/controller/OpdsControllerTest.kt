package io.legado.app.api.controller

import io.legado.app.model.opds.OpdsParseException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unit tests for OpdsController.fetchFeed.
 * Uses MockWebServer to verify HTTP request behavior, Basic auth, and error handling.
 * Validates: Requirements 3.1, 3.5, 3.6, 6.6
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33], application = android.app.Application::class)
class OpdsControllerTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val validFeedXml = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"
      xmlns:opds="http://opds-spec.org/2010/catalog">
    <id>urn:uuid:test-feed</id>
    <title>Test OPDS Catalog</title>
    <updated>2024-01-01T00:00:00Z</updated>
    <entry>
        <id>urn:uuid:book-1</id>
        <title>Test Book</title>
        <author><name>Test Author</name></author>
        <summary>A test book summary</summary>
        <updated>2024-01-01T00:00:00Z</updated>
        <link href="/book1.epub" type="application/epub+zip" rel="http://opds-spec.org/acquisition"/>
    </entry>
</feed>"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetchFeed parses valid OPDS feed`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        val feed = OpdsController.fetchFeed(
            url = server.url("/opds").toString(),
            client = client
        )

        assertEquals("Test OPDS Catalog", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("Test Book", feed.entries[0].title)
        assertEquals("Test Author", feed.entries[0].author)
        assertTrue(feed.entries[0].acquisitionLinks.isNotEmpty())
    }

    @Test
    fun `fetchFeed sends Basic auth header when credentials provided`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        OpdsController.fetchFeed(
            url = server.url("/opds").toString(),
            username = "user",
            password = "pass",
            client = client
        )

        val request = server.takeRequest()
        val authHeader = request.headers["Authorization"]
        assertNotNull("Authorization header should be present", authHeader)
        assertTrue(
            "Authorization header should be Basic",
            authHeader!!.startsWith("Basic ")
        )
    }

    @Test
    fun `fetchFeed does not send auth header when credentials are null`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        OpdsController.fetchFeed(
            url = server.url("/opds").toString(),
            client = client
        )

        val request = server.takeRequest()
        val authHeader = request.headers["Authorization"]
        assertNull("Authorization header should not be present", authHeader)
    }

    @Test
    fun `fetchFeed does not send auth header when credentials are blank`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        OpdsController.fetchFeed(
            url = server.url("/opds").toString(),
            username = "",
            password = "",
            client = client
        )

        val request = server.takeRequest()
        val authHeader = request.headers["Authorization"]
        assertNull(
            "Authorization header should not be present for blank credentials",
            authHeader
        )
    }

    @Test(expected = OpdsParseException::class)
    fun `fetchFeed throws OpdsParseException for invalid XML`() {
        runBlocking {
            server.enqueue(
                MockResponse.Builder()
                    .body("<html><body>Not an OPDS feed</body></html>")
                    .addHeader("Content-Type", "text/html")
                    .build()
            )

            OpdsController.fetchFeed(
                url = server.url("/opds").toString(),
                client = client
            )
        }
    }

    @Test(expected = IOException::class)
    fun `fetchFeed throws IOException for network failure`() {
        runBlocking {
            // Use a port that nothing is listening on
            val port = server.port
            server.close()

            OpdsController.fetchFeed(
                url = "http://localhost:$port/opds",
                client = client
            )
        }
    }

    // --- search() tests ---

    @Test
    fun `search constructs URL from template and returns parsed feed`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        val baseUrl = server.url("/search").toString()
        val template = "${baseUrl}?q={searchTerms}"

        val feed = OpdsController.search(
            searchUrlTemplate = template,
            query = "kotlin",
            client = client
        )

        assertEquals("Test OPDS Catalog", feed.title)
        val request = server.takeRequest()
        assertTrue(
            "Request URL should contain the encoded query",
            request.url.toString().contains("q=kotlin")
        )
    }

    @Test
    fun `search URL-encodes the query parameter`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        val baseUrl = server.url("/search").toString()
        val template = "${baseUrl}?q={searchTerms}"

        OpdsController.search(
            searchUrlTemplate = template,
            query = "hello world",
            client = client
        )

        val request = server.takeRequest()
        assertTrue(
            "Request URL should contain URL-encoded query",
            request.url.toString().contains("q=hello+world") ||
                request.url.toString().contains("q=hello%20world")
        )
    }

    @Test
    fun `search passes credentials to fetchFeed`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(validFeedXml)
                .addHeader("Content-Type", "application/atom+xml")
                .build()
        )

        val baseUrl = server.url("/search").toString()
        val template = "${baseUrl}?q={searchTerms}"

        OpdsController.search(
            searchUrlTemplate = template,
            query = "test",
            username = "user",
            password = "pass",
            client = client
        )

        val request = server.takeRequest()
        val authHeader = request.headers["Authorization"]
        assertNotNull("Authorization header should be present", authHeader)
        assertTrue(
            "Authorization header should be Basic",
            authHeader!!.startsWith("Basic ")
        )
    }

    @Test(expected = IOException::class)
    fun `search throws IOException for network failure`() {
        runBlocking {
            val port = server.port
            server.close()

            OpdsController.search(
                searchUrlTemplate = "http://localhost:$port/search?q={searchTerms}",
                query = "test",
                client = client
            )
        }
    }

    @Test(expected = OpdsParseException::class)
    fun `search throws OpdsParseException for invalid response`() {
        runBlocking {
            server.enqueue(
                MockResponse.Builder()
                    .body("<html>Not OPDS</html>")
                    .addHeader("Content-Type", "text/html")
                    .build()
            )

            val baseUrl = server.url("/search").toString()
            val template = "${baseUrl}?q={searchTerms}"

            OpdsController.search(
                searchUrlTemplate = template,
                query = "test",
                client = client
            )
        }
    }
}
