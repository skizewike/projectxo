package com.projectxo.app.core.data

import com.projectxo.app.core.model.MoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Отметки настроения и энергии (Р74): только на телефоне. */
interface MoodRepository {
    /** Отметки за день, новые сверху. */
    fun entriesFor(date: LocalDate): Flow<List<MoodEntry>>

    suspend fun add(entry: MoodEntry): Long

    suspend fun delete(id: Long)
}
