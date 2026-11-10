package com.projectxo.app.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.FoodTotals
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.model.DefaultGoals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {
    private lateinit var db: XoDatabase
    private lateinit var water: RoomWaterRepository
    private lateinit var goals: RoomGoalsRepository
    private lateinit var food: RoomFoodRepository
    private val day = LocalDate.of(2026, 5, 1)
    private val other = LocalDate.of(2026, 5, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, XoDatabase::class.java)
            .allowMainThreadQueries().build()
        water = RoomWaterRepository(db.waterDao())
        goals = RoomGoalsRepository(db.goalDao())
        food = RoomFoodRepository(db.foodDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun totalIsZeroWithoutEntries() = runTest {
        assertEquals(0, water.totalFor(day).first())
    }

    @Test
    fun totalCountsOnlyOwnDay() = runTest {
        water.add(250, 1_000, day)
        water.add(500, 2_000, day)
        water.add(300, 3_000, other)
        assertEquals(750, water.totalFor(day).first())
        assertEquals(300, water.totalFor(other).first())
    }

    @Test
    fun entriesAreNewestFirst() = runTest {
        water.add(100, 1_000, day)
        water.add(200, 3_000, day)
        water.add(300, 2_000, day)
        assertEquals(listOf(200, 300, 100), water.entriesFor(day).first().map { it.ml })
        assertEquals(day, water.entriesFor(day).first().first().localDate)
    }

    @Test
    fun deleteRemovesEntryAndReducesTotal() = runTest {
        val id = water.add(250, 1_000, day)
        water.add(500, 2_000, day)
        water.delete(id)
        assertEquals(listOf(500), water.entriesFor(day).first().map { it.ml })
        assertEquals(500, water.totalFor(day).first())
    }

    @Test
    fun defaultGoalsForEveryType() = runTest {
        assertEquals(DefaultGoals.WATER_ML, goals.goal(GoalType.WaterMl).first())
        assertEquals(DefaultGoals.FOOD_KCAL, goals.goal(GoalType.FoodKcal).first())
        assertEquals(DefaultGoals.STEPS, goals.goal(GoalType.Steps).first())
    }

    @Test
    fun defaultMacroGoals() = runTest {
        assertEquals(DefaultGoals.PROTEIN_G, goals.goal(GoalType.ProteinG).first())
        assertEquals(DefaultGoals.FAT_G, goals.goal(GoalType.FatG).first())
        assertEquals(DefaultGoals.CARBS_G, goals.goal(GoalType.CarbsG).first())
    }

    @Test
    fun foodTotalsAreZeroWithoutEntries() = runTest {
        assertEquals(FoodTotals(), food.totalsFor(day).first())
    }

    @Test
    fun foodTotalsSumOnlyOwnDay() = runTest {
        food.add(FoodDraft("Овсянка", 320, 10, 6, 58), 1_000, day)
        food.add(FoodDraft("Яблоко", 80, 0, 0, 19), 2_000, day)
        food.add(FoodDraft("Ужин", 700, 40, 20, 80), 3_000, other)
        assertEquals(FoodTotals(400, 10, 6, 77), food.totalsFor(day).first())
        assertEquals(FoodTotals(700, 40, 20, 80), food.totalsFor(other).first())
    }

    @Test
    fun foodEntriesNewestFirstAndDelete() = runTest {
        val a = food.add(FoodDraft("А", 100, 1, 2, 3), 1_000, day)
        food.add(FoodDraft("Б", 200, 4, 5, 6), 3_000, day)
        food.add(FoodDraft("В", 300, 7, 8, 9), 2_000, day)
        assertEquals(listOf("Б", "В", "А"), food.entriesFor(day).first().map { it.name })
        food.delete(a)
        assertEquals(listOf("Б", "В"), food.entriesFor(day).first().map { it.name })
        assertEquals(500, food.totalsFor(day).first().kcal)
    }

    @Test
    fun rangeTotalsGroupByDay() = runTest {
        water.add(250, 1_000, day)
        water.add(500, 2_000, day)
        water.add(300, 3_000, other)
        water.add(999, 4_000, day.minusDays(5))
        assertEquals(mapOf(day to 750, other to 300), water.totalsBetween(day, other).first())
        food.add(FoodDraft("А", 100, 1, 1, 1), 1_000, day)
        food.add(FoodDraft("Б", 250, 1, 1, 1), 2_000, day)
        assertEquals(mapOf(day to 350), food.kcalBetween(day, other).first())
    }

    @Test
    fun dayLogRoundTrip_andEmptyDeletes() = runTest {
        val dayLogs = RoomDayLogRepository(db.dayLogDao())
        val log = com.projectxo.app.core.data.DayLog(day, feel = 3, tags = setOf("tired", "head"), weightKg = 71.8, note = " ok ")
        dayLogs.save(log)
        val read = dayLogs.observe(day).first()!!
        assertEquals(3, read.feel)
        assertEquals(setOf("tired", "head"), read.tags)
        assertEquals(71.8, read.weightKg!!, 0.0001)
        assertEquals("ok", read.note)
        assertEquals(setOf(day), dayLogs.observeBetween(day, other).first().keys)

        dayLogs.save(com.projectxo.app.core.data.DayLog(day))
        assertEquals(null, dayLogs.observe(day).first())
    }

    @Test
    fun setGoalChangesValue() = runTest {
        goals.setGoal(GoalType.WaterMl, 2500)
        assertEquals(2500L, goals.goal(GoalType.WaterMl).first())
        assertEquals(DefaultGoals.STEPS, goals.goal(GoalType.Steps).first())
    }

    @Test
    fun setGoalZeroThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { goals.setGoal(GoalType.Steps, 0) }
        }
    }
}
