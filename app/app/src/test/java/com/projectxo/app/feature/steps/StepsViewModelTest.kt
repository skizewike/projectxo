package com.projectxo.app.feature.steps

import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.core.steps.StepsMath
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeGoalsRepository
import com.projectxo.app.testing.FakeStepsRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class StepsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val day = LocalDate.of(2026, 1, 15)

    private class Env(scope: TestScope, day: LocalDate, settings: AppSettings = AppSettings(onboarded = true)) {
        val repo = FakeStepsRepository()
        val clock = FakeClock(nowMs = day.atTime(14, 20).toInstant(ZoneOffset.UTC).toEpochMilli(), today = day)
        val vm = StepsViewModel(repo, FakeGoalsRepository(), InMemorySettingsRepository(settings), clock, scope.backgroundScope) { ZoneOffset.UTC }

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun addManual_goesToCurrentHour_andOffersUndo() = runTest {
        val e = Env(this, day)
        e.vm.addManual(1200)
        runCurrent()
        assertEquals(1200, e.vm.state.value.hours[14])
        assertEquals(1200, e.vm.state.value.total)
        assertEquals(1200, e.vm.state.value.pendingUndo)
    }

    @Test
    fun undoManual_removesSteps() = runTest {
        val e = Env(this, day)
        e.vm.addManual(500)
        runCurrent()
        e.vm.undoManual()
        runCurrent()
        assertEquals(0, e.vm.state.value.total)
        assertNull(e.vm.state.value.pendingUndo)
    }

    @Test
    fun undo_keepsSensorSteps() = runTest {
        val e = Env(this, day)
        e.repo.addSteps(listOf(com.projectxo.app.core.data.HourSteps(day, 14, 300)))
        e.vm.addManual(500)
        runCurrent()
        e.vm.undoManual()
        runCurrent()
        assertEquals(300, e.vm.state.value.total)
    }

    @Test
    fun undoWindow_expires() = runTest {
        val e = Env(this, day)
        e.vm.addManual(500)
        runCurrent()
        advanceTimeBy(UndoPolicy.WINDOW_MS + 1)
        runCurrent()
        assertNull(e.vm.state.value.pendingUndo)
        e.vm.undoManual()
        runCurrent()
        assertEquals(500, e.vm.state.value.total)
    }

    @Test
    fun addManual_outOfRange_isIgnored() = runTest {
        val e = Env(this, day)
        e.vm.addManual(0)
        e.vm.addManual(StepsMath.MAX_MANUAL + 1)
        runCurrent()
        assertEquals(0, e.vm.state.value.total)
        assertNull(e.vm.state.value.pendingUndo)
    }

    @Test
    fun stride_comesFromHeight() = runTest {
        val profile = Profile(Sex.Female, 30, 170, 60.0, Activity.entries.first(), null)
        val e = Env(this, day, AppSettings(onboarded = true, profile = profile))
        assertEquals(170 * StepsMath.STRIDE_PER_HEIGHT / 100.0, e.vm.state.value.strideM, 1e-9)
    }

    @Test
    fun stride_withoutProfile_isDefault() = runTest {
        val e = Env(this, day)
        assertEquals(StepsMath.STRIDE_M, e.vm.state.value.strideM, 1e-9)
    }
}
