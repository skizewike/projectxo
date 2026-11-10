package com.projectxo.app.feature.home

import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.BalanceHistory
import com.projectxo.app.core.model.DayBalance
import com.projectxo.app.core.model.DefaultGoals
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeFoodRepository
import com.projectxo.app.testing.FakeGoalsRepository
import com.projectxo.app.testing.FakeStepsRepository
import com.projectxo.app.testing.FakeWaterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private fun newVm(water: FakeWaterRepository, scope: kotlinx.coroutines.CoroutineScope) = TodayViewModel(
        BalanceHistory(water, FakeFoodRepository(), FakeStepsRepository(), FakeGoalsRepository()),
        water, InMemorySettingsRepository(AppSettings(onboarded = true)), FakeClock(), scope,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_initialModulesWithDefaultGoals() = runTest {
        val repo = FakeWaterRepository()
        val vm = newVm(repo, backgroundScope)
        runCurrent()
        val parts = vm.state.value!!.modules
        assertEquals(listOf(Module.Water, Module.Food, Module.Steps), parts.map { it.module })
        assertEquals(
            listOf(DefaultGoals.WATER_ML, DefaultGoals.FOOD_KCAL, DefaultGoals.STEPS),
            parts.map { it.goal },
        )
        assertEquals(0f, DayBalance.of(parts), 0f)
    }

    @Test
    fun quickAddWater_addsAndExpiresUndo() = runTest {
        val repo = FakeWaterRepository()
        val vm = newVm(repo, backgroundScope)
        runCurrent()

        vm.quickAddWater()
        runCurrent()
        val entry = repo.all.value.single()
        assertEquals(QuickAdd(entry.id, 250), vm.undo.value)
        val parts = vm.state.value!!.modules
        assertEquals(250L, parts.first { it.module == Module.Water }.value)
        assertEquals((250f / 2000f) / 3f, DayBalance.of(parts), 1e-6f)

        advanceTimeBy(UndoPolicy.WINDOW_MS + 1)
        runCurrent()
        assertNull(vm.undo.value)
        assertEquals(1, repo.all.value.size)
    }

    @Test
    fun undoQuickAdd_removesEntry() = runTest {
        val repo = FakeWaterRepository()
        val vm = newVm(repo, backgroundScope)
        runCurrent()
        vm.quickAddWater()
        runCurrent()
        assertNotNull(vm.undo.value)

        vm.undoQuickAdd()
        runCurrent()
        assertTrue(repo.all.value.isEmpty())
        assertNull(vm.undo.value)
    }
}
