package com.projectxo.app.core.model

import com.projectxo.app.core.data.DayLog
import java.time.LocalDate

/**
 * Выгрузка данных в CSV (волна 2). Один день — одна строка, заголовки на английском для программ,
 * разделитель запятая, десятичная точка, вес всегда в килограммах. В начале метка UTF-8, чтобы Excel не ломал кириллицу.
 */
object CsvExport {
    const val BOM = "\uFEFF"
    val HEADER = listOf(
        "date", "water_ml", "food_kcal", "steps", "sleep_min", "fasting_min", "breathing_sec",
        "habits_done", "habits_planned", "workouts_done", "feel_0_to_4", "weight_kg", "tags", "note",
    )

    fun build(from: LocalDate, to: LocalDate, totals: Map<LocalDate, DayTotals>, logs: Map<LocalDate, DayLog>): String {
        val sb = StringBuilder(BOM)
        sb.append(HEADER.joinToString(",")).append("\r\n")
        var d = from
        while (!d.isAfter(to)) {
            val t = totals[d] ?: DayTotals()
            val log = logs[d]
            val row = listOf(
                d.toString(), t.waterMl.toString(), t.kcal.toString(), t.steps.toString(), t.sleepMin.toString(),
                t.fastingMin.toString(), t.breathSec.toString(), t.habitsDone.toString(), t.habitsTotal.toString(), t.workoutsDone.toString(),
                log?.feel?.toString().orEmpty(), log?.weightKg?.let { "%.1f".format(java.util.Locale.ROOT, it) }.orEmpty(),
                log?.tags?.sorted()?.joinToString(";").orEmpty(), log?.note.orEmpty(),
            )
            sb.append(row.joinToString(",") { cell(it) }).append("\r\n")
            d = d.plusDays(1)
        }
        return sb.toString()
    }

    /** Ячейка: кавычки при запятой, кавычке или переводе строки; формулы (=, +, -, @) обезврежены апострофом. */
    fun cell(raw: String): String {
        var v = raw
        // Минус у числа — это число, а не формула: проверяем только текстовые поля с опасным началом.
        if (v.isNotEmpty() && v[0] in "=+@\t\r" || (v.startsWith("-") && v.length > 1 && !v[1].isDigit())) v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
