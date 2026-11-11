package com.projectxo.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.AppClock
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Reminders
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.Units
import com.projectxo.app.core.model.WeightGoal
import com.projectxo.app.core.plan.Plan
import com.projectxo.app.core.plan.PlanCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Шаги первого запуска (Р51: план считается в самом конце). */
enum class OnbStep { Privacy, Network, Comfort, Modules, About, Activity, Goal, Reminders, Calc, Plan }

data class OnbState(
    val step: OnbStep = OnbStep.Privacy,
    val forward: Boolean = true,
    val modules: Set<Module> = Module.BASE,
    val sex: Sex = Sex.Unspecified,
    val age: Int = 30,
    val heightCm: Int = 170,
    val weightKg: Double = 70.0,
    val units: Units = Units(),
    val activity: Activity = Activity.OneTwo,
    /** null — пользователь не выбирал: берётся подсказка по ИМТ. */
    val goal: WeightGoal? = null,
    val reminders: Reminders = Reminders(),
    val policyOpen: Boolean = false,
    /** null — ещё не отвечал (Р81). */
    val usesVpn: Boolean? = null,
) {
    val profile: Profile get() = Profile(sex, age, heightCm, weightKg, activity, goal)
    val bmi: Double get() = PlanCalculator.bmi(weightKg, heightCm)
    val plan: Plan get() = PlanCalculator.calculate(profile)
    val effectiveGoal: WeightGoal get() = plan.goal
}

class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val goals: GoalsRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnbState(units = settings.settings.value.units))
    val state: StateFlow<OnbState> = _state.asStateFlow()

    /** Настройки вида меняются сразу — пользователь видит тему и размер текста на этом же экране. */
    val appSettings: StateFlow<AppSettings> = settings.settings
    fun updateAppearance(transform: (AppSettings) -> AppSettings) = settings.update(transform)

    fun next() = _state.update {
        var i = it.step.ordinal + 1
        if (i > OnbStep.entries.lastIndex) it else it.copy(step = OnbStep.entries[i], forward = true)
    }

    /** Назад; с плана — мимо экрана расчёта. */
    fun back() = _state.update {
        var i = it.step.ordinal - 1
        if (i >= 0 && OnbStep.entries[i] == OnbStep.Calc) i--
        if (i < 0) it else it.copy(step = OnbStep.entries[i], forward = false)
    }

    fun toggleModule(m: Module) = _state.update {
        val next = if (m in it.modules) it.modules - m else it.modules + m
        it.copy(modules = next)
    }
    fun setSex(s: Sex) = _state.update { it.copy(sex = s) }
    fun stepAge(d: Int) = _state.update { it.copy(age = (it.age + d).coerceIn(PlanCalculator.MIN_AGE, PlanCalculator.MAX_AGE)) }
    fun stepHeight(d: Int) = _state.update { it.copy(heightCm = (it.heightCm + d).coerceIn(PlanCalculator.MIN_HEIGHT_CM, PlanCalculator.MAX_HEIGHT_CM)) }

    /** Шаг веса: 0,5 кг или 1 фунт — в зависимости от единиц. Храним кг с точностью до 0,1. */
    fun stepWeight(d: Int) = _state.update {
        val stepKg = if (it.units.weight == com.projectxo.app.core.model.WeightUnit.Kg) 0.5 else 1 / 2.20462
        val w = ((it.weightKg + d * stepKg) * 10).roundToInt() / 10.0
        it.copy(weightKg = w.coerceIn(PlanCalculator.MIN_WEIGHT_KG, PlanCalculator.MAX_WEIGHT_KG))
    }
    fun setUnits(u: Units) = _state.update { it.copy(units = u) }
    fun setActivity(a: Activity) = _state.update { it.copy(activity = a) }
    fun setGoal(g: WeightGoal) = _state.update { it.copy(goal = g) }
    fun setReminders(r: Reminders) = _state.update { it.copy(reminders = r) }

    fun setUsesVpn(v: Boolean) = _state.update { it.copy(usesVpn = v) }

    fun setPolicyOpen(open: Boolean) = _state.update { it.copy(policyOpen = open) }

    /** Принять план: сохранить профиль, единицы, модули, цели и отметить первый запуск пройденным. */
    fun finish() {
        val s = _state.value
        val plan = s.plan
        viewModelScope.launch {
            goals.setGoal(GoalType.WaterMl, plan.waterMl.toLong())
            goals.setGoal(GoalType.FoodKcal, plan.kcal.toLong())
            goals.setGoal(GoalType.ProteinG, plan.proteinG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.FatG, plan.fatG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.CarbsG, plan.carbsG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.Steps, plan.steps.toLong())
            settings.update {
                it.copy(
                    onboarded = true,
                    profile = s.profile.copy(goal = plan.goal),
                    units = s.units,
                    modules = s.modules.ifEmpty { Module.BASE },
                    reminders = s.reminders,
                    usesVpn = s.usesVpn ?: false,
                    startEpochDay = it.startEpochDay ?: clock.today().toEpochDay(),
                )
            }
        }
    }
}
