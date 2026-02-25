package io.legado.app.data.entities

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Property 3: OpdsFeed JSON round-trip consistency
 *
 * For any valid OpdsFeed object, serializing to JSON and deserializing back
 * should produce an equivalent object.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3**
 */
class OpdsFeedJsonRoundTripTest {

    private val gson = Gson()

    // --- Smart Arb generators ---

    private val arbOpdsLink: Arb<OpdsLink> = arbitrary {
        OpdsLink(
            href = Arb.string(1..50).bind(),
            type = Arb.string(1..30).orNull(0.3).bind(),
            rel = Arb.string(1..20).orNull(0.3).bind(),
            title = Arb.string(1..40).orNull(0.3).bind(),
            length = Arb.long(0L..1_000_000L).orNull(0.3).bind()
        )
    }

    private val arbOpdsEntry: Arb<OpdsEntry> = arbitrary {
        OpdsEntry(
            id = Arb.string(1..30).bind(),
            title = Arb.string(1..50).bind(),
            author = Arb.string(1..30).orNull(0.3).bind(),
            summary = Arb.string(1..100).orNull(0.3).bind(),
            content = Arb.string(1..200).orNull(0.3).bind(),
            updated = Arb.string(1..25).orNull(0.3).bind(),
            coverUrl = Arb.string(1..60).orNull(0.3).bind(),
            links = Arb.list(arbOpdsLink, 0..3).bind(),
            acquisitionLinks = Arb.list(arbOpdsLink, 0..3).bind()
        )
    }

    private val arbOpdsFeed: Arb<OpdsFeed> = arbitrary {
        OpdsFeed(
            id = Arb.string(1..30).bind(),
            title = Arb.string(1..50).bind(),
            updated = Arb.string(1..25).orNull(0.3).bind(),
            author = Arb.string(1..30).orNull(0.3).bind(),
            entries = Arb.list(arbOpdsEntry, 0..5).bind(),
            links = Arb.list(arbOpdsLink, 0..3).bind(),
            isNavigation = Arb.boolean().bind(),
            searchUrl = Arb.string(1..60).orNull(0.3).bind()
        )
    }

    @Test
    fun opdsFeedJsonRoundTrip() {
        runBlocking {
            checkAll(100, arbOpdsFeed) { original ->
                val json = gson.toJson(original)
                val restored = gson.fromJson(json, OpdsFeed::class.java)
                assertEquals(original, restored)
            }
        }
    }
}
