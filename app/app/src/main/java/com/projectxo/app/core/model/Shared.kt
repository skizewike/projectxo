package com.projectxo.app.core.model

import java.time.LocalDate

/** Вид общей привычки (Р79): «Пересечение» по умолчанию или «Нить». */
enum class SharedView { Intersection, Thread }

/** Комната общей привычки на этом телефоне. [habitId] — своя привычка, в которую пишутся мои отметки. */
data class SharedRoom(
    val roomId: String,
    val habitId: Long,
    val title: String,
    val freqMask: Int,
    val memberId: String,
    val myNick: String,
    /** Код приглашения, пока комната не заполнена и код не просрочен. */
    val code: String?,
    val codeExpiresMs: Long,
    val lastSyncMs: Long,
)

data class SharedMember(val memberId: String, val nick: String, val isMe: Boolean)

/** Состояние комнаты: участники и дни, когда каждый отметил. Мои отметки на экране берутся из своих отметок привычки. */
data class SharedState(
    val room: SharedRoom,
    val members: List<SharedMember>,
    val marks: Map<String, Set<LocalDate>>,
) {
    val me: SharedMember? get() = members.firstOrNull { it.isMe }
    val friend: SharedMember? get() = members.firstOrNull { !it.isMe }
    fun daysOf(memberId: String?): Set<LocalDate> = memberId?.let { marks[it] }.orEmpty()
}

enum class SharedError { NotConfigured, Network, BadCode, RoomFull, TooMany, Gone, BadInput, Server }

sealed interface SharedResult<out T> {
    data class Ok<T>(val value: T) : SharedResult<T>
    data class Fail(val error: SharedError) : SharedResult<Nothing>
}

object SharedMath {
    const val MAX_NICK = 20
    const val SYNC_DAYS = 14
    const val WEEK = 7
    private const val LOOKBACK_DAYS = 60

    /** Расписание общей привычки для сервера: маска дней, у «каждый день» и «N раз в неделю» все семь. */
    fun maskOf(freq: HabitFreq): Int = when (freq) {
        is HabitFreq.Days -> freq.mask.takeIf { it and HabitMath.ALL_DAYS != 0 }?.and(HabitMath.ALL_DAYS) ?: HabitMath.ALL_DAYS
        else -> HabitMath.ALL_DAYS
    }

    fun freqOf(mask: Int): HabitFreq = if (mask and HabitMath.ALL_DAYS == HabitMath.ALL_DAYS) HabitFreq.Daily else HabitFreq.Days(mask and HabitMath.ALL_DAYS)

    fun planned(mask: Int, date: LocalDate): Boolean = mask and HabitMath.dayBit(date.dayOfWeek) != 0

    /** Последние [n] дней, старые первыми, сегодня последний. */
    fun lastDays(today: LocalDate, n: Int = WEEK): List<LocalDate> = (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

    fun together(mine: Set<LocalDate>, theirs: Set<LocalDate>): Set<LocalDate> = mine intersect theirs

    /**
     * «Вместе подряд»: дни по расписанию, когда отметили оба. Сегодняшний день серию не обрывает, пока ждёт;
     * один пропуск прощается (как в Р37).
     */
    fun streak(mine: Set<LocalDate>, theirs: Set<LocalDate>, mask: Int, today: LocalDate): Int {
        val both = together(mine, theirs)
        var s = 0
        var miss = 0
        var d = if (today in both) today else today.minusDays(1)
        repeat(LOOKBACK_DAYS) {
            if (planned(mask, d)) {
                if (d in both) {
                    s++
                    miss = 0
                } else {
                    miss++
                    if (miss > 1) return s
                }
            }
            d = d.minusDays(1)
        }
        return s
    }

    /** Лучшая серия вместе за последние 60 дней, по тому же правилу. */
    fun bestStreak(mine: Set<LocalDate>, theirs: Set<LocalDate>, mask: Int, today: LocalDate): Int {
        val both = together(mine, theirs)
        var best = 0
        var run = 0
        var miss = 0
        for (k in LOOKBACK_DAYS downTo 0) {
            val d = today.minusDays(k.toLong())
            if (!planned(mask, d)) continue
            if (d in both) {
                run++
                miss = 0
                best = maxOf(best, run)
            } else if (d != today) {
                miss++
                if (miss > 1) run = 0
            }
        }
        return best
    }

    /** Какие дни из [days] отмечены (для рисования недели). */
    fun flags(days: List<LocalDate>, marks: Set<LocalDate>): List<Boolean> = days.map { it in marks }

    fun cleanNick(raw: String): String = raw.replace(Regex("\\s+"), " ").trim().take(MAX_NICK)

    /** Код как на экране: «7K4-M2Q». Принимает любой регистр, пробелы и дефис. */
    fun normalizeCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }.take(CODE_LENGTH)

    fun prettyCode(code: String): String = if (code.length == CODE_LENGTH) code.substring(0, 3) + "-" + code.substring(3) else code

    const val CODE_LENGTH = 6
}
