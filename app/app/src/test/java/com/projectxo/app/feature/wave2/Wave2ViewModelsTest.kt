package com.projectxo.app.feature.wave2

import com.projectxo.app.core.data.WorkoutProgram
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Exercise
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.HabitFreq
import com.projectxo.app.core.model.HabitKind
import com.projectxo.app.core.model.HabitMath
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.core.model.WorkoutMath
import com.projectxo.app.core.model.WorkoutRating
import com.projectxo.app.feature.fasting.FastingViewModel
import com.projectxo.app.feature.habits.HabitSeed
import com.projectxo.app.feature.habits.HabitsViewModel
import com.projectxo.app.feature.sleep.SleepViewModel
import com.projectxo.app.feature.workout.WorkoutStep
import com.projectxo.app.feature.workout.WorkoutViewModel
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeFastingRepository
import com.projectxo.app.testing.FakeHabitRepository
import com.projectxo.app.testing.FakeSleepRepository
import com.projectxo.app.testing.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val HOUR = 3_600_000L
private const val NOW = 10 * 24 * HOUR

@OptIn(ExperimentalCoroutinesApi::class)
class Wave2ViewModelsTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ——— Голодание ———

    private class FastEnv(scope: TestScope) {
        val repo = FakeFastingRepository()
        val settings = InMemorySettingsRepository(AppSettings(onboarded = true))
        val clock = FakeClock(nowMs = NOW)
        val vm = FastingViewModel(repo, settings, clock, scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun fasting_start_usesPlan_andFinishRecordsEnd() = runTest {
        val e = FastEnv(this)
        e.vm.start()
        runCurrent()
        assertTrue(e.vm.state.value.fasting)
        assertEquals(16, e.vm.state.value.current?.planHours)
        e.clock.nowMs = NOW + 3 * HOUR
        e.vm.finish()
        runCurrent()
        assertFalse(e.vm.state.value.fasting)
        assertEquals(NOW + 3 * HOUR, e.vm.state.value.last?.endMs)
    }

    @Test
    fun fasting_start_clampsToLast24Hours_andIgnoresSecondStart() = runTest {
        val e = FastEnv(this)
        e.vm.start(NOW - 40 * HOUR)
        runCurrent()
        assertEquals(NOW - 24 * HOUR, e.vm.state.value.current?.startMs)
        e.vm.start(NOW)
        runCurrent()
        assertEquals(1, e.repo.all.value.size)
    }

    @Test
    fun fasting_setPlan_onlyKnownPlans_andUpdatesRunning() = runTest {
        val e = FastEnv(this)
        e.vm.setPlan(20)
        runCurrent()
        assertEquals(16, e.vm.state.value.planHours)
        e.vm.start()
        runCurrent()
        e.vm.setPlan(18)
        runCurrent()
        assertEquals(18, e.vm.state.value.current?.planHours)
    }

    @Test
    fun fasting_finish_cannotEndBeforeStart() = runTest {
        val e = FastEnv(this)
        e.vm.start()
        runCurrent()
        e.vm.finish(NOW - 5 * HOUR)
        runCurrent()
        assertEquals(NOW + 60_000L, e.repo.all.value.single().endMs)
    }

    @Test
    fun fasting_safetyAccepted_isSaved() = runTest {
        val e = FastEnv(this)
        assertFalse(e.vm.state.value.safetyAccepted)
        e.vm.acceptSafety()
        runCurrent()
        assertTrue(e.vm.state.value.safetyAccepted)
    }

    // ——— Сон ———

    private class SleepEnv(scope: TestScope) {
        val repo = FakeSleepRepository()
        val settings = InMemorySettingsRepository(AppSettings(onboarded = true))
        val clock = FakeClock(nowMs = NOW)
        val vm = SleepViewModel(repo, settings, clock, scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun sleep_fullNight_isRecordedAndAsksForRating() = runTest {
        val e = SleepEnv(this)
        e.vm.goToBed()
        runCurrent()
        assertTrue(e.vm.state.value.sleeping)
        e.clock.nowMs = NOW + 8 * HOUR
        e.vm.wake()
        runCurrent()
        assertFalse(e.vm.state.value.sleeping)
        assertEquals(8 * 60L, (e.vm.state.value.last!!.endMs!! - e.vm.state.value.last!!.startMs) / 60_000L)
        assertNotNull(e.vm.state.value.ratingFor)
        e.vm.rate(3)
        runCurrent()
        assertNull(e.vm.state.value.ratingFor)
        assertEquals(3, e.vm.state.value.last?.rate)
    }

    @Test
    fun sleep_shortNight_isDropped() = runTest {
        val e = SleepEnv(this)
        e.vm.goToBed()
        runCurrent()
        e.clock.nowMs = NOW + 5 * 60_000L
        e.vm.wake()
        runCurrent()
        assertTrue(e.repo.all.value.isEmpty())
        assertNull(e.vm.state.value.ratingFor)
    }

    @Test
    fun sleep_schedule_rejectsGoalUnderSevenHours() = runTest {
        val e = SleepEnv(this)
        e.vm.setSchedule(0, 6 * 60)
        runCurrent()
        assertEquals(23 * 60, e.vm.state.value.bedMin)
        e.vm.setSchedule(-60, 7 * 60)
        runCurrent()
        assertEquals(23 * 60, e.vm.state.value.bedMin)
        e.vm.setSchedule(22 * 60 + 30, 7 * 60)
        runCurrent()
        assertEquals(22 * 60 + 30, e.vm.state.value.bedMin)
    }

    @Test
    fun sleep_skipRating_keepsNightWithoutRate() = runTest {
        val e = SleepEnv(this)
        e.vm.goToBed()
        runCurrent()
        e.clock.nowMs = NOW + 7 * HOUR
        e.vm.wake()
        runCurrent()
        e.vm.skipRating()
        runCurrent()
        assertNull(e.vm.state.value.last?.rate)
        assertNull(e.vm.state.value.ratingFor)
    }

    // ——— Привычки ———

    private class HabitEnv(scope: TestScope, seeds: List<HabitSeed> = emptyList()) {
        val repo = FakeHabitRepository()
        val settings = InMemorySettingsRepository(AppSettings(onboarded = true))
        val clock = FakeClock()
        val vm = HabitsViewModel(repo, settings, clock, scope.backgroundScope, seeds)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun habits_seedsOnce() = runTest {
        val seeds = listOf(HabitSeed("Утро", HabitFreq.Daily, null), HabitSeed("Чтение", HabitFreq.Daily, 21 * 60))
        val e = HabitEnv(this, seeds)
        runCurrent()
        assertEquals(2, e.repo.habitsState.value.size)
        assertTrue(e.settings.settings.value.wave2.habitsSeeded)
        // Второй вход: привычки уже заведены, повторно не добавляются.
        HabitsViewModel(e.repo, e.settings, e.clock, backgroundScope, seeds)
        runCurrent()
        assertEquals(2, e.repo.habitsState.value.size)
    }

    @Test
    fun habits_toggleToday_marksAndUnmarks() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "Вода", HabitFreq.Daily))
        runCurrent()
        val item = e.vm.state.value.items.single()
        assertFalse(item.doneToday)
        e.vm.toggle(item, 0)
        runCurrent()
        assertTrue(e.vm.state.value.items.single().doneToday)
        e.vm.toggle(e.vm.state.value.items.single(), 0)
        runCurrent()
        assertFalse(e.vm.state.value.items.single().doneToday)
    }

    @Test
    fun habits_toggle_futureAndNotPlannedDaysIgnored() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "Бег", HabitFreq.Days(0))) // нет ни одного дня
        runCurrent()
        val item = e.vm.state.value.items.single()
        e.vm.toggle(item, -1)
        e.vm.toggle(item, 2)
        runCurrent()
        assertTrue(e.repo.checksState.value.isEmpty())
    }

    @Test
    fun habits_counter_staysBetweenZeroAndTarget() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "Стаканы", HabitFreq.Daily, target = 3))
        runCurrent()
        repeat(5) { e.vm.count(e.vm.state.value.items.single(), +1); runCurrent() }
        runCurrent()
        assertEquals(3, e.vm.state.value.items.single().valueToday)
        repeat(5) { e.vm.count(e.vm.state.value.items.single(), -1); runCurrent() }
        runCurrent()
        assertEquals(0, e.vm.state.value.items.single().valueToday)
    }

    @Test
    fun habits_save_trimsAndRejectsEmptyName() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "   ", HabitFreq.Daily))
        e.vm.save(Habit(0, "  " + "а".repeat(HabitMath.MAX_NAME_LENGTH + 20) + "  ", HabitFreq.Daily, target = 500))
        runCurrent()
        val h = e.repo.habitsState.value.single()
        assertEquals(HabitMath.MAX_NAME_LENGTH, h.name.length)
        assertEquals(HabitMath.MAX_TARGET, h.target)
    }

    @Test
    fun habits_delete_isSoftAndUndoRestores() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "Йога", HabitFreq.Daily))
        runCurrent()
        e.vm.toggle(e.vm.state.value.items.single(), 0)
        runCurrent()
        e.vm.delete(e.vm.state.value.items.single())
        runCurrent()
        // Скрыта на экране, но ещё не удалена.
        assertTrue(e.vm.state.value.items.isEmpty())
        assertEquals(1, e.repo.habitsState.value.size)
        e.vm.undoDelete()
        advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        runCurrent()
        assertEquals(1, e.vm.state.value.items.size)
        assertEquals(1, e.repo.checksState.value.size)
    }

    @Test
    fun habits_delete_commitsAfterWindow() = runTest {
        val e = HabitEnv(this)
        e.vm.save(Habit(0, "Йога", HabitFreq.Daily))
        runCurrent()
        e.vm.delete(e.vm.state.value.items.single())
        runCurrent()
        advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        runCurrent()
        assertTrue(e.repo.habitsState.value.isEmpty())
    }

    @Test
    fun habits_enableWorkout_createsHabitOnce() = runTest {
        val e = HabitEnv(this)
        e.vm.setWorkoutEnabled(true, "Тренировка")
        e.vm.setWorkoutEnabled(true, "Тренировка")
        runCurrent()
        val w = e.repo.habitsState.value.filter { it.kind == HabitKind.Workout }
        assertEquals(1, w.size)
        assertEquals(HabitFreq.Days(HabitMath.WORKOUT_DAYS), w.single().freq)
        assertTrue(e.settings.settings.value.wave2.workoutEnabled)
        e.vm.setWorkoutEnabled(false, "Тренировка")
        runCurrent()
        assertFalse(e.settings.settings.value.wave2.workoutEnabled)
        assertEquals(1, e.repo.habitsState.value.size)
    }

    // ——— Тренировка ———

    private class WorkEnv(scope: TestScope) {
        val repo = FakeWorkoutRepository()
        val habits = FakeHabitRepository()
        val clock = FakeClock(nowMs = NOW)
        val vm = WorkoutViewModel(repo, habits, InMemorySettingsRepository(AppSettings(onboarded = true)), clock, scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun workout_firstOpen_asksLevel_thenStartsSets() = runTest {
        val e = WorkEnv(this)
        e.vm.open(Exercise.Pushups)
        runCurrent()
        val check = e.vm.state.value.step as WorkoutStep.CheckLevel
        assertEquals(10, check.value)
        e.vm.setCheck(+5)
        e.vm.confirmLevel()
        runCurrent()
        val sets = e.vm.state.value.step as WorkoutStep.Sets
        assertEquals(15, sets.level)
        assertEquals(WorkoutMath.plan(15, Exercise.Pushups), sets.plan)
        assertEquals(15, e.repo.programsState.value.single().level)
    }

    @Test
    fun workout_checkLevel_staysInRange() = runTest {
        val e = WorkEnv(this)
        e.vm.open(Exercise.Pushups)
        runCurrent()
        e.vm.setCheck(-100)
        assertEquals(1, (e.vm.state.value.step as WorkoutStep.CheckLevel).value)
        e.vm.setCheck(+10_000)
        assertEquals(WorkoutMath.MAX_LEVEL, (e.vm.state.value.step as WorkoutStep.CheckLevel).value)
    }

    @Test
    fun workout_set_startsRest_andRestExtends() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Squats.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Squats)
        runCurrent()
        assertTrue(e.vm.state.value.step is WorkoutStep.Sets)
        e.vm.finishSet()
        runCurrent()
        val rest = e.vm.state.value.step as WorkoutStep.Sets
        assertEquals(1, rest.actual.size)
        val ends = rest.restEndsAtMs!!
        assertEquals(NOW + WorkoutMath.restSeconds(20, Exercise.Squats) * 1000L, ends)
        e.vm.addRest()
        assertEquals(ends + 30_000L, (e.vm.state.value.step as WorkoutStep.Sets).restEndsAtMs)
        e.vm.endRest()
        assertNull((e.vm.state.value.step as WorkoutStep.Sets).restEndsAtMs)
    }

    @Test
    fun workout_fullSession_raisesLevel_andChecksHabit() = runTest {
        val e = WorkEnv(this)
        e.habits.add(Habit(0, "Тренировка", HabitFreq.Days(HabitMath.WORKOUT_DAYS), kind = HabitKind.Workout))
        e.repo.saveProgram(WorkoutProgram(Exercise.Pushups.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Pushups)
        runCurrent()
        repeat(5) {
            e.vm.finishSet()
            e.vm.endRest()
        }
        runCurrent()
        val rate = e.vm.state.value.step as WorkoutStep.Rate
        assertTrue(rate.allDone)
        e.vm.rate(WorkoutRating.NORMAL)
        runCurrent()
        val done = e.vm.state.value.step as WorkoutStep.Done
        assertEquals(20, done.oldLevel)
        assertEquals(21, done.newLevel)
        assertEquals(21, e.repo.programsState.value.single().level)
        assertEquals(1, e.repo.sessionsState.value.size)
        assertEquals(1, e.habits.checksState.value.size)
    }

    @Test
    fun workout_abort_writesNothing() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Pushups.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Pushups)
        e.vm.finishSet()
        e.vm.abort()
        runCurrent()
        assertTrue(e.vm.state.value.step is WorkoutStep.Overview)
        assertTrue(e.repo.sessionsState.value.isEmpty())
        assertEquals(20, e.repo.programsState.value.single().level)
    }

    @Test
    fun workout_tapCount_usedAsSetResult() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Pushups.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Pushups)
        repeat(7) { e.vm.tap() }
        e.vm.finishSet()
        runCurrent()
        assertEquals(listOf(7), (e.vm.state.value.step as WorkoutStep.Sets).actual)
    }

    @Test
    fun workout_plankHold_prepThenEarlyStopRecordsElapsed() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Plank.id, 40, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Plank)
        runCurrent()
        e.vm.startHold()
        val hold = (e.vm.state.value.step as WorkoutStep.Sets).holdStartMs!!
        assertEquals(NOW + 3000L, hold)
        // Отмена во время обратного отсчёта ничего не записывает.
        e.vm.stopHold()
        assertNull((e.vm.state.value.step as WorkoutStep.Sets).holdStartMs)
        assertTrue((e.vm.state.value.step as WorkoutStep.Sets).actual.isEmpty())
        e.vm.startHold()
        e.clock.nowMs = NOW + 3000L + 12_000L
        e.vm.stopHold()
        val sets = e.vm.state.value.step as WorkoutStep.Sets
        assertEquals(listOf(12), sets.actual)
        assertNull(sets.holdStartMs)
        assertNotNull(sets.restEndsAtMs)
    }

    @Test
    fun workout_plankHold_isCappedAtTarget_andOnlyForSeconds() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Plank.id, 40, HabitMath.WORKOUT_DAYS))
        e.repo.saveProgram(WorkoutProgram(Exercise.Squats.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Plank)
        runCurrent()
        val target = (e.vm.state.value.step as WorkoutStep.Sets).plan[0]
        e.vm.startHold()
        e.clock.nowMs = NOW + 3000L + 600_000L
        e.vm.stopHold()
        assertEquals(listOf(target), (e.vm.state.value.step as WorkoutStep.Sets).actual)
        e.vm.abort()
        e.vm.open(Exercise.Squats)
        runCurrent()
        e.vm.startHold()
        assertNull((e.vm.state.value.step as WorkoutStep.Sets).holdStartMs)
    }

    @Test
    fun workout_untap_removesOneRepButNotBelowZero() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Pushups.id, 20, HabitMath.WORKOUT_DAYS))
        runCurrent()
        e.vm.open(Exercise.Pushups)
        repeat(3) { e.vm.tap() }
        e.vm.untap()
        assertEquals(2, (e.vm.state.value.step as WorkoutStep.Sets).current)
        repeat(5) { e.vm.untap() }
        assertEquals(0, (e.vm.state.value.step as WorkoutStep.Sets).current)
    }

    @Test
    fun workout_retestAfterSixSessions_andTotalOnDone() = runTest {
        val e = WorkEnv(this)
        e.repo.saveProgram(WorkoutProgram(Exercise.Pushups.id, 20, HabitMath.WORKOUT_DAYS))
        repeat(6) { i ->
            e.repo.addSession(
                com.projectxo.app.core.data.WorkoutSessionRecord(0, java.time.LocalDate.of(2026, 1, 1 + i), Exercise.Pushups.id, 20, listOf(5), listOf(5), 1, NOW + i),
            )
        }
        runCurrent()
        e.vm.open(Exercise.Pushups)
        runCurrent()
        val check = e.vm.state.value.step as WorkoutStep.CheckLevel
        assertTrue(check.retest)
        assertEquals(20, check.value)
        e.vm.confirmLevel()
        repeat(5) { e.vm.finishSet(); e.vm.endRest() }
        runCurrent()
        e.vm.rate(WorkoutRating.HARD)
        runCurrent()
        val done = e.vm.state.value.step as WorkoutStep.Done
        assertEquals(WorkoutMath.plan(20, Exercise.Pushups).sum(), done.total)
        assertEquals(7, e.vm.state.value.sessionsOf(Exercise.Pushups).size)
    }

    @Test
    fun workout_suggested_isLeastRecentExercise() = runTest {
        val e = WorkEnv(this)
        runCurrent()
        assertEquals(Exercise.Pushups, e.vm.state.value.suggested)
        e.repo.addSession(com.projectxo.app.core.data.WorkoutSessionRecord(0, java.time.LocalDate.of(2026, 1, 1), Exercise.Pushups.id, 10, listOf(5), listOf(5), 1, 100L))
        runCurrent()
        assertEquals(Exercise.Squats, e.vm.state.value.suggested)
    }

    @Test
    fun habits_timerStyle_defaultsToRing_andIsSaved() = runTest {
        val e = HabitEnv(this)
        assertEquals(com.projectxo.app.core.model.WorkoutTimerStyle.Ring, e.vm.state.value.timer)
        e.vm.setTimer(com.projectxo.app.core.model.WorkoutTimerStyle.Ticks)
        runCurrent()
        assertEquals(com.projectxo.app.core.model.WorkoutTimerStyle.Ticks, e.vm.state.value.timer)
        assertEquals(com.projectxo.app.core.model.WorkoutTimerStyle.Ticks, e.settings.settings.value.wave2.workoutTimer)
    }
}
