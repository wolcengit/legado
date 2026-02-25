package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.OpdsSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Property-based tests for OpdsSource CRUD round-trip via Room in-memory database.
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4**
 */
@RunWith(AndroidJUnit4::class)
class OpdsSourceDaoCrudTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: OpdsSourceDao

    /** Arb generator for random OpdsSource with unique sourceUrl */
    private val arbOpdsSource: Arb<OpdsSource> = arbitrary {
        OpdsSource(
            sourceUrl = "https://${Arb.uuid().bind()}.example.com/opds",
            sourceName = Arb.string(1..50).bind(),
            username = Arb.string(0..30).orNull().bind(),
            password = Arb.string(0..30).orNull().bind(),
            sortOrder = Arb.int(-1000..1000).bind(),
            lastAccessTime = Arb.long(0L..Long.MAX_VALUE).bind(),
            enabled = Arb.boolean().bind()
        )
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.opdsSourceDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Property 7: OpdsSource CRUD round-trip — insert then getByUrl returns equivalent object.
     *
     * **Validates: Requirements 6.1, 6.2, 6.3, 6.4**
     */
    @Test
    fun insertThenGetByUrlReturnsEquivalentObject() {
        runBlocking {
            checkAll(100, arbOpdsSource) { source ->
                dao.insert(source)
                val retrieved = dao.getByUrl(source.sourceUrl)
                assertEquals(source, retrieved)
                // cleanup for next iteration
                dao.delete(source)
            }
        }
    }

    /**
     * Property 7: OpdsSource CRUD round-trip — insert then has returns true.
     *
     * **Validates: Requirements 6.1, 6.4**
     */
    @Test
    fun insertThenHasReturnsTrue() {
        runBlocking {
            checkAll(100, arbOpdsSource) { source ->
                dao.insert(source)
                assertTrue(dao.has(source.sourceUrl))
                dao.delete(source)
            }
        }
    }

    /**
     * Property 7: OpdsSource CRUD round-trip — insert then delete then has returns false.
     *
     * **Validates: Requirements 6.1, 6.3, 6.4**
     */
    @Test
    fun insertThenDeleteThenHasReturnsFalse() {
        runBlocking {
            checkAll(100, arbOpdsSource) { source ->
                dao.insert(source)
                dao.delete(source)
                assertFalse(dao.has(source.sourceUrl))
                assertNull(dao.getByUrl(source.sourceUrl))
            }
        }
    }
}
