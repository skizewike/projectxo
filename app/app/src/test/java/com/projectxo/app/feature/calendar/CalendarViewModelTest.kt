package com.projectxo.app.feature.calendar

import com.projectxo.app.core.data.DayLog
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.WellbeingTag
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeDayLogRepository
import com.projectxo.app.testing.FakeFoodRepository
import com.projectxo.app.testing.FakeGoalsRepository
import com.projectxo.app.testing.FakeStepsRepository
import com.projectxo.app.testing.FakeWaterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val start = today.minusDays(10)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Env(scope: TestScope, today: LocalDate, start: LocalDate, modules: Set<Module>) {
        val water = FakeWaterRepository()
        val food = FakeFoodRepository()
        val steps = FakeStepsRepository()
        val log = FakeDayLogRepository()
        val goals = FakeGoalsRepository()
        val settings = InMemorySettingsRepository(AppSettings(onboarded = true, startEpochDay = start.toEpochDay(), modules = modules))
        val vm = CalendarViewModel(
            BalanceHistory(water, food, steps, goals), water, food, log, settings,
            FakeClock(today = today), scope.backgroundScope,
        )

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    private fun env(scope: TestScope, modules: Set<Module> = Module.entries.toSet()) = Env(scope, today, start, modules)

    @Test
    fun startsOnToday_withFirstDayFromSettings() = runTest {
        val e = env(this)
        assertEquals(today, e.vm.state.value.selected)
        assertEquals(start, e.vm.state.value.start)
    }

    @Test
    fun select_ignoresFutureAndBeforeStart() = runTest {
        val e = env(this)
        e.vm.select(today.plusDays(1))
        assertEquals(today, e.vm.state.value.selected)
        e.vm.select(start.minusDays(1))
        assertEquals(today, e.vm.state.value.selected)
        e.vm.select(start)
        assertEquals(start, e.vm.state.value.selected)
    }

    @Test
    fun balance_countsOnlyEnabledModules() = runTest {
        val e = env(this, modules = setOf(Module.Water))
        e.water.add(1050, 1_000, today)
        e.food.add(FoodDraft("x", 2000, 0, 0, 0), 1_000, today)
        runCurrent()
        // Вода 1050 из 2000 = 52,5 %; еда не считается.
        assertEquals(0.525f, e.vm.state.value.balances.getValue(today), 0.001f)
        assertEquals(0.525f, e.vm.state.value.detail!!.balance, 0.001f)
    }

    @Test
    fun detail_followsSelectedDay() = runTest {
        val e = env(this)
        val d = today.minusDays(2)
        e.water.add(500, 1_000, d)
        runCurrent()
        e.vm.select(d)
        runCurrent()
        val detail = e.vm.state.value.detail!!
        assertEquals(d, detail.date)
        assertEquals(500, detail.totals.waterMl)
        assertEquals(1, detail.waterEntries.size)
    }

    @Test
    fun picker_saveWritesDayLog_andMarksDayLogged() = runTest {
        val e = env(this)
        e.vm.openPicker()
        runCurrent()
        e.vm.setFeel(3)
        e.vm.toggleTag(WellbeingTag.Tired)
        e.vm.toggleTag(WellbeingTag.Head)
        e.vm.toggleTag(WellbeingTag.Head)
        e.vm.stepWeight(0.1)
        e.vm.setNote("мало сна")
        e.vm.save()
        runCurrent()

        val saved = e.log.all.value.getValue(today)
        assertEquals(3, saved.feel)
        assertEquals(setOf("tired"), saved.tags)
        assertEquals(70.1, saved.weightKg!!, 0.0001)
        assertEquals("мало сна", saved.note)
        assertNull(e.vm.state.value.draft)
        assertTrue(today in e.vm.state.value.logged)
    }

    @Test
    fun picker_closeDiscardsDraft() = runTest {
        val e = env(this)
        e.vm.openPicker()
        e.vm.setFeel(1)
        e.vm.closePicker()
        runCurrent()
        assertNull(e.vm.state.value.draft)
        assertTrue(e.log.all.value.isEmpty())
    }

    @Test
    fun feel_tapTwiceClears() = runTest {
        val e = env(this)
        e.vm.openPicker()
        e.vm.setFeel(2)
        assertEquals(2, e.vm.state.value.draft!!.feel)
        e.vm.setFeel(2)
        assertNull(e.vm.state.value.draft!!.feel)
    }

    @Test
    fun picker_savingEmptyDraftRemovesLog() = runTest {
        val e = env(this)
        e.log.save(DayLog(today, feel = 4))
        runCurrent()
        e.vm.openPicker()
        assertEquals(4, e.vm.state.value.draft!!.feel)
        e.vm.setFeel(4)
        e.vm.save()
        runCurrent()
        assertFalse(today in e.log.all.value)
    }

    @Test
    fun weight_clampsAndClears() = runTest {
        val e = env(this)
        e.vm.openPicker()
        repeat(5) { e.vm.stepWeight(-100.0) }
        assertEquals(20.0, e.vm.state.value.draft!!.weightKg!!, 0.0001)
        e.vm.clearWeight()
        assertNull(e.vm.state.value.draft!!.weightKg)
    }

    @Test
    fun frequentTags_ownHistoryFirst_thenDefaults() = runTest {
        val e = env(this)
        e.log.save(DayLog(today.minusDays(1), tags = setOf("joy", "stress")))
        e.log.save(DayLog(today.minusDays(2), tags = setOf("joy")))
        runCurrent()
        val f = e.vm.state.value.frequent
        assertEquals(WellbeingTag.Joy, f.first())
        assertEquals(4, f.size)
        assertNotNull(f.firstOrNull { it == WellbeingTag.Stress })
    }
}
