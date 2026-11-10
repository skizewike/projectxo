package com.projectxo.app.feature.food

import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeFoodRepository
import com.projectxo.app.testing.FakeGoalsRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Env(scope: TestScope) {
        val repo = FakeFoodRepository()
        val vm = FoodViewModel(repo, FakeGoalsRepository(), FakeClock(), scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    private val oats = FoodDraft("Овсянка", 320, 10, 6, 58)

    @Test
    fun add_valid_updatesTotalsAndList() = runTest {
        val e = Env(this)
        e.vm.add(oats)
        runCurrent()
        val s = e.vm.state.value
        assertEquals(320, s.totals.kcal)
        assertEquals(10, s.totals.proteinG)
        assertEquals(1, s.entries.size)
        assertEquals(1680L, s.leftKcal)
    }

    @Test
    fun add_invalid_isIgnored() = runTest {
        val e = Env(this)
        e.vm.add(oats.copy(kcal = 0))
        e.vm.add(oats.copy(kcal = 5001))
        e.vm.add(oats.copy(proteinG = -1))
        e.vm.add(oats.copy(fatG = 501))
        runCurrent()
        assertTrue(e.repo.all.value.isEmpty())
    }

    @Test
    fun add_trimsAndShortensName() = runTest {
        val e = Env(this)
        e.vm.add(oats.copy(name = "  " + "я".repeat(60) + "  "))
        runCurrent()
        assertEquals(40, e.repo.all.value.single().name.length)
    }

    @Test
    fun over_goalShowsOverKcal() = runTest {
        val e = Env(this)
        e.vm.add(oats.copy(kcal = 2100))
        runCurrent()
        assertEquals(0L, e.vm.state.value.leftKcal)
        assertEquals(100L, e.vm.state.value.overKcal)
    }

    @Test
    fun delete_hidesImmediately_commitsAfterWindow() = runTest {
        val e = Env(this)
        e.vm.add(oats)
        runCurrent()
        val entry = e.vm.state.value.entries.single()

        e.vm.delete(entry)
        runCurrent()
        assertTrue(e.vm.state.value.entries.isEmpty())
        assertEquals(0, e.vm.state.value.totals.kcal)
        assertEquals(entry, e.vm.state.value.pendingDelete)
        assertEquals(1, e.repo.all.value.size)

        advanceTimeBy(UndoPolicy.WINDOW_MS + 1)
        runCurrent()
        assertTrue(e.repo.all.value.isEmpty())
        assertNull(e.vm.state.value.pendingDelete)
    }

    @Test
    fun undoDelete_keepsEntryAndTotals() = runTest {
        val e = Env(this)
        e.vm.add(oats)
        runCurrent()
        val entry = e.vm.state.value.entries.single()

        e.vm.delete(entry)
        runCurrent()
        advanceTimeBy(UndoPolicy.WINDOW_MS - 1000)
        e.vm.undoDelete()
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(1, e.repo.all.value.size)
        assertNull(e.vm.state.value.pendingDelete)
        assertEquals(320, e.vm.state.value.totals.kcal)
    }

    @Test
    fun secondDelete_commitsFirstImmediately() = runTest {
        val e = Env(this)
        e.vm.add(oats)
        e.vm.add(oats.copy(name = "Яблоко", kcal = 80))
        runCurrent()
        val (newer, older) = e.vm.state.value.entries

        e.vm.delete(older)
        runCurrent()
        e.vm.delete(newer)
        runCurrent()

        assertEquals(listOf(newer.id), e.repo.all.value.map { it.id })
        assertEquals(newer, e.vm.state.value.pendingDelete)
    }
}
