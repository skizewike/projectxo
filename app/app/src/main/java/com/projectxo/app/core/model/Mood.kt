package com.projectxo.app.core.model

/** Что повлияло на состояние (Р74). [key] хранится в базе и не меняется. Без медицинских категорий. */
enum class MoodTag(val key: String) {
    Sleep("sleep"),
    Food("food"),
    Work("work"),
    Workout("workout"),
    Walk("walk"),
    Social("social"),
    Weather("weather"),
    Stress("stress"),
    ;

    companion object {
        fun fromKey(key: String): MoodTag? = entries.firstOrNull { it.key == key }
    }
}

/** Отметка настроения и энергии. Обе шкалы от [Feel.MIN] (плохо, нет сил) до [Feel.MAX] (отлично, полно сил). */
data class MoodEntry(
    val id: Long,
    val timestampMs: Long,
    val date: java.time.LocalDate,
    val mood: Int,
    val energy: Int,
    val tags: Set<MoodTag> = emptySet(),
)

object MoodMath {
    /** Отметок за день не больше, чем можно разумно записать: защита от случайных повторов. */
    const val MAX_PER_DAY = 20

    /** Среднее настроения и энергии за день или null, если отметок нет. Округляется до целого деления шкалы. */
    fun dayAverage(entries: List<MoodEntry>): Pair<Int, Int>? {
        if (entries.isEmpty()) return null
        val m = Math.round(entries.sumOf { it.mood }.toDouble() / entries.size).toInt().coerceIn(Feel.MIN, Feel.MAX)
        val e = Math.round(entries.sumOf { it.energy }.toDouble() / entries.size).toInt().coerceIn(Feel.MIN, Feel.MAX)
        return m to e
    }

    fun clampScale(v: Int): Int = v.coerceIn(Feel.MIN, Feel.MAX)

    fun encodeTags(tags: Set<MoodTag>): String = tags.map { it.key }.sorted().joinToString(",")

    fun decodeTags(raw: String): Set<MoodTag> = raw.split(',').mapNotNull { MoodTag.fromKey(it.trim()) }.toSet()
}
