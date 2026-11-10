package com.projectxo.app.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.projectxo.app.core.data.HourSteps
import com.projectxo.app.core.data.SensorReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class RoomStepsRepositoryTest {
    private lateinit var db: XoDatabase
    private lateinit var steps: RoomStepsRepository
    private val day = LocalDate.of(2026, 5, 1)
    private val other = LocalDate.of(2026, 5, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, XoDatabase::class.java)
            .allowMainThreadQueries().build()
        steps = RoomStepsRepository(db.stepsDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun emptyDayHas24ZerosAndZeroTotal() = runTest {
        assertEquals(List(24) { 0 }, steps.hoursFor(day).first())
        assertEquals(0, steps.totalFor(day).first())
    }

    @Test
    fun addsToSameHourAccumulate() = runTest {
        steps.addSteps(listOf(HourSteps(day, 9, 100)))
        steps.addSteps(listOf(HourSteps(day, 9, 50), HourSteps(day, 10, 7)))
        val hours = steps.hoursFor(day).first()
        assertEquals(150, hours[9])
        assertEquals(7, hours[10])
        assertEquals(157, steps.totalFor(day).first())
    }

    @Test
    fun daysDoNotMix() = runTest {
        steps.addSteps(listOf(HourSteps(day, 8, 100), HourSteps(other, 8, 40)))
        assertEquals(100, steps.totalFor(day).first())
        assertEquals(40, steps.hoursFor(other).first()[8])
    }

    @Test
    fun invalidPartsAreSkipped() = runTest {
        steps.addSteps(
            listOf(
                HourSteps(day, 5, 0),
                HourSteps(day, 6, -10),
                HourSteps(day, 24, 30),
                HourSteps(day, -1, 30),
                HourSteps(day, 7, 12),
            ),
        )
        assertEquals(12, steps.totalFor(day).first())
        assertEquals(12, steps.hoursFor(day).first()[7])
    }

    @Test
    fun removeSubtractsAndNeverGoesBelowZero() = runTest {
        steps.addSteps(listOf(HourSteps(day, 9, 300), HourSteps(day, 10, 100)))
        steps.removeSteps(listOf(HourSteps(day, 9, 120), HourSteps(day, 10, 500)))
        val hours = steps.hoursFor(day).first()
        assertEquals(180, hours[9])
        assertEquals(0, hours[10])
        assertEquals(180, steps.totalFor(day).first())
        // Пустой час удалён: в итогах периода остаётся только реальный день.
        steps.removeSteps(listOf(HourSteps(day, 9, 180)))
        assertEquals(emptyMap<LocalDate, Int>(), steps.totalsBetween(day, other).first())
    }

    @Test
    fun readingIsNullThenSavedThenOverwritten() = runTest {
        assertNull(steps.lastReading())
        steps.saveReading(SensorReading(1_000L, 10L))
        assertEquals(SensorReading(1_000L, 10L), steps.lastReading())
        steps.saveReading(SensorReading(2_500L, 20L))
        assertEquals(SensorReading(2_500L, 20L), steps.lastReading())
    }
}
