package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarLayoutTest {
    private val order = Module.entries
    private val all = Module.entries.toSet()

    @Test fun defaultBarIsWaterFoodWorkouts() {
        val r = BarLayout.resolve(all, order, null)
        assertEquals(listOf(Module.Water, Module.Food, Module.Workouts), r.bar)
        assertTrue(r.hasMore)
        assertEquals(all.size - 3, r.overflow.size)
        assertFalse(Module.Water in r.overflow)
    }

    @Test fun userChoiceWinsAndKeepsItsOrder() {
        val r = BarLayout.resolve(all, order, listOf(Module.Sleep, Module.Water, Module.Steps))
        assertEquals(listOf(Module.Sleep, Module.Water, Module.Steps), r.bar)
        assertFalse(Module.Sleep in r.overflow)
    }

    @Test fun disabledPinsAreReplacedInOrder() {
        val modules = all - Module.Workouts
        val r = BarLayout.resolve(modules, order, null)
        assertEquals(listOf(Module.Water, Module.Food, Module.Steps), r.bar)
    }

    @Test fun fourOrFewerModulesAllSitInTheBarWithoutMore() {
        val modules = setOf(Module.Water, Module.Food, Module.Steps, Module.Workouts)
        val r = BarLayout.resolve(modules, order, null)
        assertFalse(r.hasMore)
        assertEquals(4, r.bar.size)
        assertEquals(listOf(Module.Water, Module.Food, Module.Workouts, Module.Steps), r.bar)
        assertEquals(setOf(Module.Water), BarLayout.resolve(setOf(Module.Water), order, null).bar.toSet())
    }

    @Test fun neverMoreThanFiveTabsInTotal() {
        for (n in 1..Module.entries.size) {
            val r = BarLayout.resolve(Module.entries.take(n).toSet(), order, null)
            val tabs = 1 + r.bar.size + if (r.hasMore) 1 else 0
            assertTrue("n=$n tabs=$tabs", tabs <= 5)
        }
    }

    @Test fun pinsWithDuplicatesAreCleaned() {
        val r = BarLayout.resolve(all, order, listOf(Module.Food, Module.Food, Module.Sleep, Module.Steps, Module.Habits))
        assertEquals(listOf(Module.Food, Module.Sleep, Module.Steps), r.bar)
    }

    @Test fun withSlotReplacesOrSwaps() {
        val cur = listOf(Module.Water, Module.Food, Module.Workouts)
        assertEquals(listOf(Module.Water, Module.Sleep, Module.Workouts), BarLayout.withSlot(cur, 1, Module.Sleep))
        assertEquals(listOf(Module.Workouts, Module.Food, Module.Water), BarLayout.withSlot(cur, 0, Module.Workouts))
        assertEquals(cur, BarLayout.withSlot(cur, 1, Module.Food))
        assertEquals(cur, BarLayout.withSlot(cur, 7, Module.Sleep))
    }
}
