package com.projectxo.app.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.projectxo.app.core.data.MoodRepository
import com.projectxo.app.core.model.MoodEntry
import com.projectxo.app.core.model.MoodMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Entity(tableName = "mood_entry", indices = [Index("localDate")])
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val localDate: String,
    val mood: Int,
    val energy: Int,
    val tags: String,
)

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_entry WHERE localDate = :date ORDER BY ts DESC, id DESC")
    fun forDate(date: String): Flow<List<MoodEntryEntity>>

    @Insert
    suspend fun insert(e: MoodEntryEntity): Long

    @Query("DELETE FROM mood_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

class RoomMoodRepository(private val dao: MoodDao) : MoodRepository {
    override fun entriesFor(date: LocalDate) = dao.forDate(date.toString()).map { l ->
        l.map { MoodEntry(it.id, it.ts, LocalDate.parse(it.localDate), it.mood, it.energy, MoodMath.decodeTags(it.tags)) }
    }

    override suspend fun add(entry: MoodEntry) = dao.insert(
        MoodEntryEntity(
            ts = entry.timestampMs, localDate = entry.date.toString(),
            mood = MoodMath.clampScale(entry.mood), energy = MoodMath.clampScale(entry.energy),
            tags = MoodMath.encodeTags(entry.tags),
        ),
    )

    override suspend fun delete(id: Long) = dao.delete(id)
}
