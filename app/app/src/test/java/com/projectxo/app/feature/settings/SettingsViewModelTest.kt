package com.projectxo.app.feature.settings

import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.Units
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.testing.FakeGoalsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val profile = Profile(Sex.Male, 30, 180, 80.0, Activity.ThreeFour, null)

    private fun vm(
        settings: InMemorySettingsRepository = InMemorySettingsRepository(AppSettings(onboarded = true, profile = profile)),
        goals: FakeGoalsRepository = FakeGoalsRepository(),
        onDelete: () -> Unit = {},
    ) = SettingsViewModel(settings, goals, onDelete)

    @Test
    fun keepsAtLeastOneModule() {
        val repo = InMemorySettingsRepository(AppSettings(modules = setOf(Module.Water)))
        val vm = vm(repo)
        vm.toggleModule(Module.Water)
        assertEquals(setOf(Module.Water), repo.settings.value.modules)
        vm.toggleModule(Module.Food)
        vm.toggleModule(Module.Water)
        assertEquals(setOf(Module.Food), repo.settings.value.modules)
    }

    @Test
    fun profileEdits_areClampedToPlanLimits() {
        val repo = InMemorySettingsRepository(AppSettings(profile = profile))
        val vm = vm(repo)
        repeat(100) { vm.stepAge(-1) }
        assertEquals(18, repo.settings.value.profile!!.ageYears)
        repeat(200) { vm.stepHeight(1) }
        assertEquals(230, repo.settings.value.profile!!.heightCm)
    }

    @Test
    fun weightStep_followsUnits() {
        val repo = InMemorySettingsRepository(AppSettings(profile = profile))
        val vm = vm(repo)
        vm.stepWeight(1)
        assertEquals(80.5, repo.settings.value.profile!!.weightKg, 0.0001)
        repo.update { it.copy(units = Units(weight = WeightUnit.Lb)) }
        vm.stepWeight(1)
        // Фунт = 0,4536 кг, хранение с точностью 0,1 кг.
        assertEquals(81.0, repo.settings.value.profile!!.weightKg, 0.0001)
    }

    @Test
    fun editingProfile_doesNotChangeGoals_untilPlanApplied() = runTest {
        val goals = FakeGoalsRepository()
        val vm = vm(goals = goals)
        val before = goals.goal(GoalType.FoodKcal).first()
        vm.setActivity(Activity.Daily)
        assertEquals(before, goals.goal(GoalType.FoodKcal).first())
        assertFalse(vm.planState.value.matches)

        vm.applyPlan()
        val plan = vm.planState.value.plan!!
        assertEquals(plan.kcal.toLong(), goals.goal(GoalType.FoodKcal).first())
        assertEquals(plan.proteinG.toLong(), goals.goal(GoalType.ProteinG).first())
        assertEquals(plan.waterMl.toLong(), goals.goal(GoalType.WaterMl).first())
        assertTrue(vm.planState.value.matches)
    }

    @Test
    fun quietHours_wrapAroundMidnight() {
        val repo = InMemorySettingsRepository(AppSettings())
        val vm = vm(repo)
        // 22:00 + 5 шагов по 30 мин = 00:30
        repeat(5) { vm.stepQuiet(from = true, deltaSteps = 1) }
        assertEquals(30, repo.settings.value.reminders.quietFromMin)
        // 08:00 − 17 шагов = 23:30
        repeat(17) { vm.stepQuiet(from = false, deltaSteps = -1) }
        assertEquals(23 * 60 + 30, repo.settings.value.reminders.quietToMin)
    }

    @Test
    fun planUnavailable_forMinors() {
        val repo = InMemorySettingsRepository(AppSettings(profile = profile.copy(ageYears = 17)))
        val vm = vm(repo)
        assertNull(vm.planState.value.plan)
        assertFalse(vm.planState.value.matches)
    }

    @Test
    fun deleteEverything_callsWiper() {
        var called = 0
        val vm = vm(onDelete = { called++ })
        vm.deleteEverything()
        assertEquals(1, called)
    }

    @Test
    fun appearanceSetters_writeSettings() {
        val repo = InMemorySettingsRepository(AppSettings())
        val vm = vm(repo)
        vm.setContrast(true)
        vm.setReduceMotion(true)
        assertTrue(repo.settings.value.highContrast)
        assertTrue(repo.settings.value.reduceMotion)
        assertNotNull(repo.settings.value)
    }
}
