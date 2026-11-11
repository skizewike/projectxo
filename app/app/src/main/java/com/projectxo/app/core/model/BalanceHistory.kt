package com.projectxo.app.core.model

import com.projectxo.app.core.data.BreathingRepository
import com.projectxo.app.core.data.FastingRepository
import com.projectxo.app.core.data.FoodRepository
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.HabitRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.data.SleepRepository
import com.projectxo.app.core.data.StepsRepository
import com.projectxo.app.core.data.WaterRepository
import com.projectxo.app.core.data.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** Сколько последних записей тренировок читать для итогов дней. */
private const val WORKOUT_HISTORY = 4000

/** Итоги одного дня по модулям в базовых единицах (мл, ккал, шаги, минуты, секунды, штуки). */
data class DayTotals(
    val waterMl: Int = 0,
    val kcal: Int = 0,
    val steps: Int = 0,
    val sleepMin: Int = 0,
    val fastingMin: Int = 0,
    val breathSec: Int = 0,
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    /** Тренировок за день и день из плана тренировок (Р73). */
    val workoutsDone: Int = 0,
    val workoutDay: Boolean = false,
)

/** Цели, с которыми сравниваются дни истории. Берутся текущие: история целей не хранится. */
data class HistoryGoals(
    val waterMl: Long,
    val kcal: Long,
    val steps: Long,
    val sleepMin: Long = 8 * 60L,
    val fastingMin: Long = FastingMath.DEFAULT_PLAN * 60L,
    val breathSec: Long = Wave2Goals.BREATHING_SEC.toLong(),
)

object BalanceMath {
    /** Прогресс по включённым модулям в порядке [order]. Привычки без запланированных на день не считаются. */
    fun progress(totals: DayTotals?, goals: HistoryGoals, modules: Set<Module>, order: List<Module> = Module.entries): List<ModuleProgress> {
        val t = totals ?: DayTotals()
        return order.filter { it in modules }.mapNotNull { m ->
            when (m) {
                Module.Water -> ModuleProgress(m, t.waterMl.toLong(), goals.waterMl)
                Module.Food -> ModuleProgress(m, t.kcal.toLong(), goals.kcal)
                Module.Steps -> ModuleProgress(m, t.steps.toLong(), goals.steps)
                Module.Sleep -> ModuleProgress(m, t.sleepMin.toLong(), goals.sleepMin)
                Module.Fasting -> ModuleProgress(m, t.fastingMin.toLong(), goals.fastingMin)
                Module.Breathing -> ModuleProgress(m, t.breathSec.toLong(), goals.breathSec)
                Module.Workouts -> if (t.workoutDay || t.workoutsDone > 0) ModuleProgress(m, minOf(t.workoutsDone, 1).toLong(), 1L) else null
                Module.Mood -> null // у настроения нет цели: в «Баланс дня» не входит (Р74)
                Module.Habits -> if (t.habitsTotal > 0) ModuleProgress(m, t.habitsDone.toLong(), t.habitsTotal.toLong()) else null
            }
        }
    }

    /** «Баланс дня» по включённым модулям, 0..1. */
    fun balance(totals: DayTotals?, goals: HistoryGoals, modules: Set<Module>): Float {
        if (totals == null) return 0f
        return DayBalance.of(progress(totals, goals, modules))
    }
}

/** Зависимости модулей волны 2 для итогов дня. */
class HistoryExtras(
    val sleep: SleepRepository,
    val fasting: FastingRepository,
    val breathing: BreathingRepository,
    val habits: HabitRepository,
    val workouts: WorkoutRepository,
    val settings: SettingsRepository,
    val nowMs: () -> Long,
)

/** Итоги по дням за период: общий источник для «Сегодня», календаря, полосы недели и профиля. */
class BalanceHistory(
    private val water: WaterRepository,
    private val food: FoodRepository,
    private val steps: StepsRepository,
    private val goals: GoalsRepository,
    private val extras: HistoryExtras? = null,
) {
    private data class Extra(
        val sleep: Map<LocalDate, Int> = emptyMap(),
        val fasting: Map<LocalDate, Int> = emptyMap(),
        val breath: Map<LocalDate, Int> = emptyMap(),
        val habits: Map<LocalDate, Pair<Int, Int>> = emptyMap(),
    )

    private fun extraFlow(from: LocalDate, to: LocalDate): Flow<Extra> {
        val e = extras ?: return flowOf(Extra())
        return combine(
            e.sleep.minutesByDay(from, to),
            e.fasting.minutesByDay(from, to, e.nowMs()),
            e.breathing.secondsByDay(from, to),
            e.habits.habits(),
            e.habits.checks(from.minusDays(7), to),
        ) { sleep, fasting, breath, habits, checks ->
            val progress = if (habits.isEmpty()) {
                emptyMap()
            } else {
                ProfileStats.datesBetween(from, to).associateWith { HabitMath.dayProgress(habits, checks, it) }
            }
            Extra(sleep, fasting, breath, progress)
        }
    }

    /** Тренировки по дням: сколько записано и какие дни входят в план (Р73). Без модуля пусто. */
    private fun workoutFlow(from: LocalDate, to: LocalDate): Flow<Pair<Map<LocalDate, Int>, Int>> {
        val e = extras ?: return flowOf(emptyMap<LocalDate, Int>() to 0)
        return combine(e.workouts.sessions(WORKOUT_HISTORY), e.settings.settings) { sessions, st ->
            sessions.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }.groupingBy { it.date }.eachCount() to st.wave2.workoutDays
        }
    }

    fun totalsBetween(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayTotals>> = combine(
        water.totalsBetween(from, to),
        food.kcalBetween(from, to),
        steps.totalsBetween(from, to),
        extraFlow(from, to),
        workoutFlow(from, to),
    ) { w, f, s, x, wk ->
        val (workouts, planMask) = wk
        val planned = extras != null && planMask != 0
        val dates = w.keys + f.keys + s.keys + x.sleep.keys + x.fasting.keys + x.breath.keys + x.habits.filterValues { it.second > 0 }.keys + workouts.keys + if (planned) ProfileStats.datesBetween(from, to) else emptyList()
        dates.associateWith { d ->
            val h = x.habits[d]
            DayTotals(
                waterMl = w[d] ?: 0, kcal = f[d] ?: 0, steps = s[d] ?: 0,
                sleepMin = x.sleep[d] ?: 0, fastingMin = x.fasting[d] ?: 0, breathSec = x.breath[d] ?: 0,
                habitsDone = h?.first ?: 0, habitsTotal = h?.second ?: 0,
                workoutsDone = workouts[d] ?: 0, workoutDay = planned && HabitMath.dayBit(d.dayOfWeek) and planMask != 0,
            )
        }
    }

    fun goals(): Flow<HistoryGoals> {
        val base = combine(
            goals.goal(GoalType.WaterMl),
            goals.goal(GoalType.FoodKcal),
            goals.goal(GoalType.Steps),
        ) { w, f, s -> Triple(w, f, s) }
        val e = extras ?: return combine(base, flowOf(Unit)) { (w, f, s), _ -> HistoryGoals(w, f, s) }
        return combine(base, e.settings.settings) { (w, f, s), st ->
            HistoryGoals(
                waterMl = w, kcal = f, steps = s,
                sleepMin = SleepMath.goalMinutes(st.wave2.sleepBedMin, st.wave2.sleepWakeMin).toLong().coerceAtLeast(SleepMath.MIN_GOAL_MIN.toLong()),
                fastingMin = st.wave2.fastingPlanHours * 60L,
            )
        }
    }
}
