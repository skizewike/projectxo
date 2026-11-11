package com.projectxo.app.core.model

/** Группы каталога упражнений (Р75). */
enum class ExerciseGroup { ChestArms, Back, Legs, Core, FullBody }

/**
 * Каталог домашних упражнений (docs/SPORT.md, Р47, Р75). [seconds] — упражнение считается секундами (планка, стульчик).
 * [defaultCheck] — с чего начинается проверка уровня, пока нет своего результата.
 * Старые идентификаторы (pushups, squats, plank, crunches) не меняются: по ним лежат записи в базе.
 */
enum class Exercise(val id: String, val group: ExerciseGroup, val seconds: Boolean, val defaultCheck: Int) {
    Pushups("pushups", ExerciseGroup.ChestArms, false, 10),
    Dips("dips", ExerciseGroup.ChestArms, false, 8),
    Pullups("pullups", ExerciseGroup.Back, false, 3),
    Superman("superman", ExerciseGroup.Back, false, 12),
    Hinge("hinge", ExerciseGroup.Back, false, 15),
    Squats("squats", ExerciseGroup.Legs, false, 20),
    JumpSquat("jump_squat", ExerciseGroup.Legs, false, 12),
    Lunges("lunges", ExerciseGroup.Legs, false, 12),
    StepUp("step_up", ExerciseGroup.Legs, false, 12),
    Bridge("bridge", ExerciseGroup.Legs, false, 15),
    CalfRaise("calf_raise", ExerciseGroup.Legs, false, 20),
    WallSit("wall_sit", ExerciseGroup.Legs, true, 30),
    DonkeyKick("donkey_kick", ExerciseGroup.Legs, false, 12),
    Crunches("crunches", ExerciseGroup.Core, false, 15),
    Bicycle("bicycle", ExerciseGroup.Core, false, 16),
    LegRaise("leg_raise", ExerciseGroup.Core, false, 10),
    Flutter("flutter", ExerciseGroup.Core, false, 20),
    Plank("plank", ExerciseGroup.Core, true, 30),
    SidePlank("side_plank", ExerciseGroup.Core, true, 20),
    BirdDog("bird_dog", ExerciseGroup.Core, false, 10),
    Burpee("burpee", ExerciseGroup.FullBody, false, 6),
    Jacks("jacks", ExerciseGroup.FullBody, false, 25),
    Mountain("mountain", ExerciseGroup.FullBody, false, 20),
    HighKnees("high_knees", ExerciseGroup.FullBody, false, 30),
    ;

    /** Фигура для показа: у отжиманий она зависит от варианта по уровню [level]. */
    fun figure(level: Int? = null): FigureSpec {
        val key = if (this == Pushups) "Pushups/" + WorkoutMath.pushupVariant(level ?: defaultCheck).name else name
        return ExerciseFigureData.specs.getValue(key)
    }

    companion object {
        fun fromId(id: String): Exercise? = entries.firstOrNull { it.id == id }

        /** Базовая четвёрка из первой версии: с неё начинаются подсказки «дальше». */
        val STARTER: Set<Exercise> = setOf(Pushups, Squats, Plank, Crunches)

        /** Упражнения группы в порядке каталога. */
        fun of(group: ExerciseGroup): List<Exercise> = entries.filter { it.group == group }
    }
}

/** Как было после тренировки (docs/SPORT.md): 0 легко, 1 нормально, 2 тяжело. */
object WorkoutRating {
    const val EASY = 0
    const val NORMAL = 1
    const val HARD = 2
}

/** Вариант отжиманий по уровню: от стены, с колен, классические. */
enum class PushupVariant { Wall, Knees, Classic }

object WorkoutMath {
    const val SETS = 5
    const val PLANK_STEP = 5
    const val PLANK_MIN = 10
    const val RETEST_EVERY = 6
    const val MAX_LEVEL = 200
    private val FACTORS = listOf(0.6, 0.55, 0.5, 0.45)

    private fun round(v: Double, ex: Exercise): Int {
        val whole = Math.floor(v + 1e-9).toInt()
        return if (ex.seconds) (whole / PLANK_STEP * PLANK_STEP).coerceAtLeast(PLANK_MIN) else whole.coerceAtLeast(1)
    }

    /** Пять подходов от уровня [m]: 0,6·M, 0,55·M, 0,5·M, 0,45·M и последний «сколько сможете, но не меньше 0,5·M». */
    fun plan(m: Int, ex: Exercise): List<Int> = FACTORS.map { round(m * it, ex) } + round(m * 0.5, ex)

    /** Отдых между подходами: до 15 повторов — 60 с, больше — 90 с, планка — 60 с. */
    fun restSeconds(m: Int, ex: Exercise): Int = when {
        ex.seconds -> 60
        m * 0.6 <= 15 -> 60
        else -> 90
    }

    fun allDone(plan: List<Int>, actual: List<Int>): Boolean = actual.size == plan.size && plan.indices.all { actual[it] >= plan[it] }

    /**
     * Следующий уровень (docs/SPORT.md): все подходы и «легко» — ×1,12 (не меньше +1), «нормально» — ×1,08,
     * «тяжело» или недобор — без изменений, недобор две тренировки подряд — ×0,92.
     */
    fun nextLevel(m: Int, ex: Exercise, allDone: Boolean, rating: Int, previousMissed: Boolean): Int {
        val minStep = if (ex.seconds) PLANK_STEP else 1
        val next = when {
            allDone && rating == WorkoutRating.EASY -> maxOf(m + minStep, Math.floor(m * 1.12).toInt())
            allDone && rating == WorkoutRating.NORMAL -> maxOf(m + minStep, Math.floor(m * 1.08).toInt())
            !allDone && previousMissed -> Math.floor(m * 0.92).toInt()
            else -> m
        }
        val clamped = next.coerceIn(if (ex.seconds) PLANK_MIN else 1, MAX_LEVEL)
        return if (ex.seconds) (clamped / PLANK_STEP * PLANK_STEP).coerceAtLeast(PLANK_MIN) else clamped
    }

    fun pushupVariant(m: Int): PushupVariant = when {
        m < 6 -> PushupVariant.Wall
        m < 12 -> PushupVariant.Knees
        else -> PushupVariant.Classic
    }

    /** Предлагать повторную проверку уровня после каждых [RETEST_EVERY] тренировок упражнения. */
    fun needsRetest(sessionsOfExercise: Int): Boolean = sessionsOfExercise > 0 && sessionsOfExercise % RETEST_EVERY == 0
}
