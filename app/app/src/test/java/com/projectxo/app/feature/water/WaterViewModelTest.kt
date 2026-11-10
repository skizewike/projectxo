package com.projectxo.app.feature.water

import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeGoalsRepository
import com.projectxo.app.testing.FakeWaterRepository
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
class WaterViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Env(scope: TestScope) {
        val repo = FakeWaterRepository()
        val vm = WaterViewModel(repo, FakeGoalsRepository(), FakeClock(), scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test
    fun add_valid_addsEntry() = runTest {
        val e = Env(this)
        e.vm.add(250)
        runCurrent()
        assertEquals(1, e.repo.all.value.size)
        assertEquals(250, e.vm.state.value.totalMl)
    }

    @Test
    fun add_invalid_isIgnored() = runTest {
        val e = Env(this)
        e.vm.add(0)
        e.vm.add(3001)
        runCurrent()
        assertTrue(e.repo.all.value.isEmpty())
    }

    @Test
    fun delete_hidesImmediately_commitsAfterWindow() = runTest {
        val e = Env(this)
        e.vm.add(250)
        runCurrent()
        val entry = e.vm.state.value.entries.single()

        e.vm.delete(entry)
        runCurrent()
        assertTrue(e.vm.state.value.entries.isEmpty())
        assertEquals(0, e.vm.state.value.totalMl)
        assertEquals(entry, e.vm.state.value.pendingDelete)
        assertEquals(1, e.repo.all.value.size)

        advanceTimeBy(UndoPolicy.WINDOW_MS + 1)
        runCurrent()
        assertTrue(e.repo.all.value.isEmpty())
        assertNull(e.vm.state.value.pendingDelete)
    }

    @Test
    fun undoDelete_keepsEntry() = runTest {
        val e = Env(this)
        e.vm.add(250)
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
        assertEquals(1, e.vm.state.value.entries.size)
        assertEquals(250, e.vm.state.value.totalMl)
    }

    @Test
    fun secondDelete_commitsFirstImmediately() = runTest {
        val e = Env(this)
        e.vm.add(150)
        e.vm.add(250)
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
