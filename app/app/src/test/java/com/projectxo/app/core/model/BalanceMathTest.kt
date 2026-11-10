package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceMathTest {
    private val goals = HistoryGoals(waterMl = 2000, kcal = 2000, steps = 8000)
    private val all = Module.BASE

    @Test
    fun noData_isZero() {
        assertEquals(0f, BalanceMath.balance(null, goals, all), 0f)
        assertEquals(0f, BalanceMath.balance(DayTotals(), goals, all), 0f)
    }

    @Test
    fun average_ofEnabledModules_eachCappedAtFull() {
        // Вода 100 % (перебор не считается), еда 50 %, шаги 0 → (1 + 0,5 + 0) / 3
        val t = DayTotals(waterMl = 3000, kcal = 1000, steps = 0)
        assertEquals(0.5f, BalanceMath.balance(t, goals, all), 0.0001f)
    }

    @Test
    fun disabledModules_areIgnored() {
        val t = DayTotals(waterMl = 1000, kcal = 2000, steps = 8000)
        assertEquals(0.5f, BalanceMath.balance(t, goals, setOf(Module.Water)), 0.0001f)
        assertEquals(1f, BalanceMath.balance(t, goals, setOf(Module.Food, Module.Steps)), 0.0001f)
    }
}
