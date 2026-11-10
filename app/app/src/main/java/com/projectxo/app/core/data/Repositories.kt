package com.projectxo.app.core.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Контракты хранилища. Чистый Kotlin без android.*: экраны знают только эти интерфейсы,
// поэтому Room можно заменить или дополнить облаком (волна 2), не трогая интерфейс.
// java.time.LocalDate при переходе на Compose Multiplatform заменяется на kotlinx-datetime.

/** Одна запись воды. Хранится в мл; [localDate] — день, который видел пользователь. */
data class WaterEntry(
    val id: Long,
    /** Момент записи, UTC epoch millis. */
    val timestampMs: Long,
    val localDate: LocalDate,
    val ml: Int,
)

interface WaterRepository {
    /** Записи за день, новые сверху. */
    fun entriesFor(date: LocalDate): Flow<List<WaterEntry>>

    /** Сумма за день в мл (0, если записей нет). Считается запросом в базе. */
    fun totalFor(date: LocalDate): Flow<Int>

    /** Добавить запись. Возвращает её id. */
    suspend fun add(ml: Int, timestampMs: Long, date: LocalDate): Long

    suspend fun delete(id: Long)

    /** Сумма мл по дням за период (включительно). Дни без записей в карту не попадают. */
    fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
}

/** Последнее показание датчика шагов: общий счётчик с момента включения телефона. */
data class SensorReading(val counter: Long, val timestampMs: Long)

/** Шаги за один час одного дня. [hour] 0..23 по местному времени. */
data class HourSteps(val date: LocalDate, val hour: Int, val steps: Int)

interface StepsRepository {
    /** Шаги по часам за день: всегда 24 числа, индекс = час. */
    fun hoursFor(date: LocalDate): Flow<List<Int>>

    /** Сумма за день. Считается запросом в базе. */
    fun totalFor(date: LocalDate): Flow<Int>

    /** Шаги по дням за период (включительно). Дни без шагов в карту не попадают. */
    fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    /** Прибавить шаги к часам (одна транзакция). Нули и отрицательные пропускаются. */
    suspend fun addSteps(parts: List<HourSteps>)

    /** Снять шаги с часов (отмена ручной записи). Ниже нуля час не уходит. */
    suspend fun removeSteps(parts: List<HourSteps>)

    /** Последнее сохранённое показание датчика или null. */
    suspend fun lastReading(): SensorReading?

    suspend fun saveReading(reading: SensorReading)
}

/** Одна запись еды. Калории и БЖУ хранятся числами: названия порций не нужны для подсчёта. */
data class FoodEntry(
    val id: Long,
    /** Момент записи, UTC epoch millis. */
    val timestampMs: Long,
    val localDate: LocalDate,
    val name: String,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    /** Имя файла снимка в [com.projectxo.app.core.data.photo.FoodPhotoStore], если запись сделана по фото (Р71). */
    val photoFile: String? = null,
    /** Оценка пользы 1..10 от ИИ, только у записей по фото. */
    val healthScore: Int? = null,
)

/** Итоги дня по еде. */
data class FoodTotals(val kcal: Int = 0, val proteinG: Int = 0, val fatG: Int = 0, val carbsG: Int = 0)

/** То, что вводит пользователь; время и день добавляет репозиторий. */
data class FoodDraft(
    val name: String, val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int,
    val photoFile: String? = null, val healthScore: Int? = null,
)

interface FoodRepository {
    /** Записи за день, новые сверху. */
    fun entriesFor(date: LocalDate): Flow<List<FoodEntry>>

    /** Итоги за день (нули, если записей нет). Считаются запросом в базе. */
    fun totalsFor(date: LocalDate): Flow<FoodTotals>

    /** Добавить запись. Возвращает её id. */
    suspend fun add(draft: FoodDraft, timestampMs: Long, date: LocalDate): Long

    suspend fun delete(id: Long)

    /** Калории по дням за период (включительно). Дни без записей в карту не попадают. */
    fun kcalBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
}

/**
 * Заметки дня: самочувствие, вес, текст. Одна строка на день.
 * Пустую запись (ничего не отмечено) [DayLogRepository.save] удаляет.
 */
data class DayLog(
    val date: LocalDate,
    /** 0 (плохо) .. 4 (отлично) или null. */
    val feel: Int? = null,
    /** Ключи [com.projectxo.app.core.model.WellbeingTag]. */
    val tags: Set<String> = emptySet(),
    /** Вес в кг с шагом 0,1 или null. */
    val weightKg: Double? = null,
    val note: String = "",
) {
    val isEmpty: Boolean get() = feel == null && tags.isEmpty() && weightKg == null && note.isBlank()
}

interface DayLogRepository {
    fun observe(date: LocalDate): Flow<DayLog?>

    /** Заметки за период (включительно). */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayLog>>

    suspend fun save(log: DayLog)
}

enum class GoalType { WaterMl, FoodKcal, ProteinG, FatG, CarbsG, Steps }

interface GoalsRepository {
    /** Цель; если пользователь её не задавал, значение по умолчанию из DefaultGoals. */
    fun goal(type: GoalType): Flow<Long>

    suspend fun setGoal(type: GoalType, target: Long)
}

/** Время и текущий день. Подменяется в тестах. */
interface AppClock {
    fun nowMs(): Long
    fun today(): LocalDate
}

object SystemClock : AppClock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
}
