package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DayBalanceTest {

    @Test
    fun noModulesMeansZero() {
        assertEquals(0f, DayBalance.of(emptyList()), 0f)
    }

    @Test
    fun averageOfEnabledModules() {
        val parts = listOf(
            ModuleProgress(Module.Water, 1000, 2000),
            ModuleProgress(Module.Steps, 8000, 8000),
        )
        assertEquals(0.75f, DayBalance.of(parts), 1e-6f)
        assertEquals(75, DayBalance.percent(parts))
    }

    @Test
    fun overshootIsCappedAt100Percent() {
        val parts = listOf(
            ModuleProgress(Module.Water, 5000, 2000),
            ModuleProgress(Module.Food, 0, 2000),
        )
        assertEquals(0.5f, DayBalance.of(parts), 1e-6f)
    }

    @Test
    fun zeroGoalDoesNotCrash() {
        assertEquals(0f, ModuleProgress(Module.Food, 100, 0).fraction, 0f)
    }
}
