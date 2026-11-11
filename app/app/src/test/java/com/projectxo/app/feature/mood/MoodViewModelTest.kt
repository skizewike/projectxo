package com.projectxo.app.feature.mood

import com.projectxo.app.core.data.MoodRepository
import com.projectxo.app.core.model.Feel
import com.projectxo.app.core.model.MoodEntry
import com.projectxo.app.core.model.MoodMath
import com.projectxo.app.core.model.MoodTag
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.testing.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakeMoodRepository : MoodRepository {
    val state = MutableStateFlow<List<MoodEntry>>(emptyList())
    private var nextId = 1L

    override fun entriesFor(date: LocalDate): Flow<List<MoodEntry>> =
        state.map { l -> l.filter { it.date == date }.sortedByDescending { it.timestampMs } }

    override suspend fun add(entry: MoodEntry): Long {
        val id = nextId++
        state.value = state.value + entry.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MoodViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class Env(scope: TestScope) {
        val repo = FakeMoodRepository()
        val clock = FakeClock()
        val vm = MoodViewModel(repo, clock, scope.backgroundScope)

        init {
            scope.backgroundScope.launch { vm.state.collect {} }
            scope.runCurrent()
        }
    }

    @Test fun startsEmptyAndCannotSave() = runTest {
        val e = Env(this)
        val s = e.vm.state.value
        assertTrue(s.entries.isEmpty())
        assertNull(s.mood)
        assertFalse(s.canSave)
    }

    @Test fun needsBothScalesToSave() = runTest {
        val e = Env(this)
        e.vm.setMood(3)
        runCurrent()
        assertFalse(e.vm.state.value.canSave)
        e.vm.save()
        runCurrent()
        assertTrue(e.repo.state.value.isEmpty())
        e.vm.setEnergy(1)
        runCurrent()
        assertTrue(e.vm.state.value.canSave)
    }

    @Test fun saveWritesEntryAndClearsForm() = runTest {
        val e = Env(this)
        e.vm.setMood(4)
        e.vm.setEnergy(2)
        e.vm.toggleTag(MoodTag.Sleep)
        e.vm.toggleTag(MoodTag.Walk)
        e.vm.toggleTag(MoodTag.Walk)
        runCurrent()
        e.vm.save()
        runCurrent()
        val saved = e.repo.state.value.single()
        assertEquals(4, saved.mood)
        assertEquals(2, saved.energy)
        assertEquals(setOf(MoodTag.Sleep), saved.tags)
        assertEquals(e.clock.today, saved.date)
        val s = e.vm.state.value
        assertNull(s.mood)
        assertNull(s.energy)
        assertTrue(s.tags.isEmpty())
        assertEquals(1, s.entries.size)
    }

    @Test fun scaleValuesAreClamped() = runTest {
        val e = Env(this)
        e.vm.setMood(99)
        e.vm.setEnergy(-5)
        runCurrent()
        assertEquals(Feel.MAX, e.vm.state.value.mood)
        assertEquals(Feel.MIN, e.vm.state.value.energy)
    }

    @Test fun severalEntriesPerDayAreKept() = runTest {
        val e = Env(this)
        repeat(3) {
            e.clock.nowMs += 1000
            e.vm.setMood(it)
            e.vm.setEnergy(it)
            e.vm.save()
            runCurrent()
        }
        assertEquals(3, e.vm.state.value.entries.size)
        assertEquals(listOf(2, 1, 0), e.vm.state.value.entries.map { it.mood })
    }

    @Test fun deleteHidesAtOnceAndCommitsAfterWindow() = runTest {
        val e = Env(this)
        e.vm.setMood(2); e.vm.setEnergy(2); e.vm.save()
        runCurrent()
        e.vm.delete(e.vm.state.value.entries.single())
        runCurrent()
        assertTrue(e.vm.state.value.entries.isEmpty())
        assertEquals(1, e.repo.state.value.size)
        advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        runCurrent()
        assertTrue(e.repo.state.value.isEmpty())
    }

    @Test fun undoRestoresEntry() = runTest {
        val e = Env(this)
        e.vm.setMood(2); e.vm.setEnergy(2); e.vm.save()
        runCurrent()
        e.vm.delete(e.vm.state.value.entries.single())
        runCurrent()
        e.vm.undoDelete()
        advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        runCurrent()
        assertEquals(1, e.vm.state.value.entries.size)
        assertEquals(1, e.repo.state.value.size)
    }

    @Test fun dayLimitStopsSaving() = runTest {
        val e = Env(this)
        repeat(MoodMath.MAX_PER_DAY) {
            e.clock.nowMs += 1000
            e.vm.setMood(1); e.vm.setEnergy(1); e.vm.save()
            runCurrent()
        }
        e.vm.setMood(1); e.vm.setEnergy(1)
        runCurrent()
        assertFalse(e.vm.state.value.canSave)
        e.vm.save()
        runCurrent()
        assertEquals(MoodMath.MAX_PER_DAY, e.repo.state.value.size)
    }
}

class MoodMathTest {
    private fun entry(m: Int, e: Int) = MoodEntry(0, 0, LocalDate.of(2026, 1, 1), m, e)

    @Test fun dayAverageIsNullWithoutEntries() = assertNull(MoodMath.dayAverage(emptyList()))

    @Test fun dayAverageRoundsToScale() {
        assertEquals(3 to 1, MoodMath.dayAverage(listOf(entry(4, 0), entry(3, 2), entry(3, 1))))
        assertEquals(4 to 4, MoodMath.dayAverage(listOf(entry(4, 4))))
    }

    @Test fun tagsRoundTripAndUnknownKeysAreIgnored() {
        val raw = MoodMath.encodeTags(setOf(MoodTag.Work, MoodTag.Sleep))
        assertEquals("sleep,work", raw)
        assertEquals(setOf(MoodTag.Work, MoodTag.Sleep), MoodMath.decodeTags(raw))
        assertEquals(setOf(MoodTag.Food), MoodMath.decodeTags("food,unknown,"))
        assertTrue(MoodMath.decodeTags("").isEmpty())
    }

    @Test fun moodIsNotPartOfBalance() {
        val parts = com.projectxo.app.core.model.BalanceMath.progress(
            com.projectxo.app.core.model.DayTotals(waterMl = 1000),
            com.projectxo.app.core.model.HistoryGoals(2000, 2000, 8000),
            setOf(com.projectxo.app.core.model.Module.Water, com.projectxo.app.core.model.Module.Mood),
        )
        assertEquals(listOf(com.projectxo.app.core.model.Module.Water), parts.map { it.module })
    }
}
