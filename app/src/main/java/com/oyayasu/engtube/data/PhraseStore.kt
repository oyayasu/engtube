package com.oyayasu.engtube.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "phrases", indices = [Index(value = ["videoId"]), Index(value = ["collection"])])
data class Phrase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val meaning: String = "",
    val notes: String = "",
    val ipaUk: String = "",
    val ipaUs: String = "",
    val videoId: String = "",
    val videoUrl: String = "",
    val title: String = "",
    val startMs: Long = 0,
    val endMs: Long = 0,
    val audioUri: String? = null,
    val collection: String = "General",
    val status: String = "New",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Phrase>>

    @Query("SELECT * FROM phrases WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Phrase?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(phrase: Phrase): Long

    @Delete
    suspend fun delete(phrase: Phrase)
}

@Database(entities = [Phrase::class], version = 1, exportSchema = false)
abstract class PhraseDatabase : RoomDatabase() {
    abstract fun phraseDao(): PhraseDao
}
