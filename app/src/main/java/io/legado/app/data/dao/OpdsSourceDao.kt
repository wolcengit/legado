package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.OpdsSource
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsSourceDao {

    @Query("SELECT * FROM opdsSources ORDER BY sortOrder")
    fun getAll(): List<OpdsSource>

    @Query("SELECT * FROM opdsSources ORDER BY sortOrder")
    fun flowAll(): Flow<List<OpdsSource>>

    @Query("SELECT * FROM opdsSources WHERE sourceUrl = :url")
    fun getByUrl(url: String): OpdsSource?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(source: OpdsSource)

    @Update
    fun update(source: OpdsSource)

    @Delete
    fun delete(source: OpdsSource)

    @Query("SELECT EXISTS(SELECT 1 FROM opdsSources WHERE sourceUrl = :url)")
    fun has(url: String): Boolean

}
