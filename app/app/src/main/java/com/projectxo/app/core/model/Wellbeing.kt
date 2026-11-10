package com.projectxo.app.core.model

/** Группы отметок самочувствия в выборе (design/calendar.html, Р48). */
enum class WellbeingGroup { Energy, Pain, Mood, Digestion, Other }

/**
 * Отметка самочувствия. [key] хранится в базе и не меняется; название и иконка берутся в интерфейсе.
 * Без интимных категорий (Р48).
 */
enum class WellbeingTag(val key: String, val group: WellbeingGroup) {
    Energetic("energy", WellbeingGroup.Energy),
    Tired("tired", WellbeingGroup.Energy),
    Sleepy("sleepy", WellbeingGroup.Energy),
    Head("head", WellbeingGroup.Pain),
    Back("back", WellbeingGroup.Pain),
    Muscle("muscle", WellbeingGroup.Pain),
    Joints("joint", WellbeingGroup.Pain),
    Belly("belly", WellbeingGroup.Pain),
    Calm("calm", WellbeingGroup.Mood),
    Joy("joy", WellbeingGroup.Mood),
    Anxiety("anx", WellbeingGroup.Mood),
    Irritation("angry", WellbeingGroup.Mood),
    Sadness("sad", WellbeingGroup.Mood),
    Stress("stress", WellbeingGroup.Mood),
    Bloating("bloat", WellbeingGroup.Digestion),
    Heartburn("heart", WellbeingGroup.Digestion),
    Nausea("nausea", WellbeingGroup.Digestion),
    Cold("cold", WellbeingGroup.Other),
    Allergy("allergy", WellbeingGroup.Other),
    ;

    companion object {
        fun fromKey(key: String): WellbeingTag? = entries.firstOrNull { it.key == key }

        /** Показывается первым блоком «Часто отмечаете», пока нет своей истории. */
        val DEFAULT_FREQUENT = listOf(Tired, Muscle, Head, Calm)
    }
}

/** Оценка дня лицом: 0 (плохо) .. 4 (отлично). */
object Feel {
    const val MIN = 0
    const val MAX = 4
    const val COUNT = 5
}

/** Вес в заметке дня. */
object DayWeight {
    const val MIN_KG = 20.0
    const val MAX_KG = 400.0
    const val STEP_KG = 0.1
    const val DEFAULT_KG = 70.0

    fun clamp(kg: Double): Double = (Math.round(kg.coerceIn(MIN_KG, MAX_KG) * 10) / 10.0)
}

/** Длина заметки дня. */
object DayNote {
    const val MAX_LENGTH = 500
}
