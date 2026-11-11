package com.projectxo.app.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.ThemeChoice
import com.projectxo.app.core.ui.theme.XoTheme
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeGoalsRepository
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Снимки шагов первого запуска (build/outputs/screens) и проверка, что «Принять план» сохраняет цели. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class OnboardingTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = InMemorySettingsRepository()
    private val goals = FakeGoalsRepository()
    private val clock = FakeClock(today = LocalDate.of(2026, 11, 9))
    private val vm = OnboardingViewModel(settings, goals, clock)

    private fun shoot(name: String, step: OnbStep, theme: ThemeChoice = ThemeChoice.Warm, dark: Boolean = false, advanceMs: Long? = null) {
        while (vm.state.value.step != step) vm.next()
        settings.update { it.copy(theme = theme) }
        if (advanceMs != null) compose.mainClock.autoAdvance = false
        compose.setContent {
            val s by settings.settings.collectAsStateWithLifecycle()
            XoTheme(darkTheme = dark, theme = s.theme, textScale = s.textSize.scale) { OnboardingScreen(vm) }
        }
        if (advanceMs != null) compose.mainClock.advanceTimeBy(advanceMs) else compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(File("build/outputs/screens").apply { mkdirs() }, name)
        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    @Test fun privacy() = shoot("onb_1_privacy.png", OnbStep.Privacy)
    @Test fun comfortAcid() = shoot("onb_2_comfort_acid.png", OnbStep.Comfort, ThemeChoice.Acid)
    @Test fun modules() = shoot("onb_3_modules.png", OnbStep.Modules, dark = true)
    @Test fun about() = shoot("onb_4_about.png", OnbStep.About)
    @Test fun activity() = shoot("onb_5_activity.png", OnbStep.Activity, ThemeChoice.Volt)
    @Test fun goal() = shoot("onb_6_goal.png", OnbStep.Goal)
    @Test fun reminders() = shoot("onb_7_reminders.png", OnbStep.Reminders, ThemeChoice.Blood)
    @Test fun calc() = shoot("onb_8_calc.png", OnbStep.Calc, advanceMs = 1200)
    @Test fun plan() = shoot("onb_9_plan.png", OnbStep.Plan)

    @Test fun backFromPlanSkipsCalc() {
        while (vm.state.value.step != OnbStep.Plan) vm.next()
        vm.back()
        assertEquals(OnbStep.Reminders, vm.state.value.step)
    }

    @Test fun acceptSavesGoalsAndProfile() = runBlocking {
        vm.finish()
        // finish() пишет в viewModelScope; в тесте ждём, пока настройки отметят онбординг пройденным.
        repeat(50) { if (!settings.settings.value.onboarded) Thread.sleep(20) }
        val s: AppSettings = settings.settings.value
        assertTrue(s.onboarded)
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), s.startEpochDay)
        assertEquals(vm.state.value.plan.waterMl.toLong(), goals.goal(GoalType.WaterMl).first())
        assertEquals(vm.state.value.plan.kcal.toLong(), goals.goal(GoalType.FoodKcal).first())
    }

    @Test fun workoutsModuleIsChosenInModulesStep() {
        vm.toggleModule(com.projectxo.app.core.model.Module.Workouts)
        while (vm.state.value.step != OnbStep.Plan) vm.next()
        vm.finish()
        assertTrue(com.projectxo.app.core.model.Module.Workouts in settings.settings.value.modules)
    }

    @Test fun noWorkoutStepAnyMore() {
        assertTrue(OnbStep.entries.none { it.name == "Workout" })
        while (vm.state.value.step != OnbStep.Goal) vm.next()
        vm.next()
        assertEquals(OnbStep.Reminders, vm.state.value.step)
        vm.back()
        assertEquals(OnbStep.Goal, vm.state.value.step)
    }
}
