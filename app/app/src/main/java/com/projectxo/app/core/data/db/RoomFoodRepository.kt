package com.projectxo.app.core.data.db

import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.FoodTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomFoodRepository(private val dao: FoodDao) : FoodRepository {
    override fun entriesFor(date: LocalDate): Flow<List<FoodEntry>> =
        dao.entriesFor(date.toString()).map { list -> list.map { it.toModel() } }

    override fun totalsFor(date: LocalDate): Flow<FoodTotals> =
        dao.totalsFor(date.toString()).map { FoodTotals(it.kcal, it.protein, it.fat, it.carbs) }

    override fun kcalBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.kcalBetween(from.toString(), to.toString()).map { it.toDateMap() }

    override suspend fun add(draft: FoodDraft, timestampMs: Long, date: LocalDate): Long =
        dao.insert(
            FoodEntryEntity(
                ts = timestampMs, localDate = date.toString(), name = draft.name,
                kcal = draft.kcal, protein = draft.proteinG, fat = draft.fatG, carbs = draft.carbsG,
                photoFile = draft.photoFile, healthScore = draft.healthScore,
            ),
        )

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun FoodEntryEntity.toModel() = FoodEntry(
        id = id, timestampMs = ts, localDate = LocalDate.parse(localDate),
        name = name, kcal = kcal, proteinG = protein, fatG = fat, carbsG = carbs,
        photoFile = photoFile, healthScore = healthScore,
    )
}
