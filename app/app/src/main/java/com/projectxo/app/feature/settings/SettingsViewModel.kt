package com.projectxo.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectxo.app.core.data.GoalType
import com.projectxo.app.core.data.GoalsRepository
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Reminders
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.TextSize
import com.projectxo.app.core.model.ThemeChoice
import com.projectxo.app.core.model.Units
import com.projectxo.app.core.model.WeightGoal
import com.projectxo.app.core.model.WeightUnit
import com.projectxo.app.core.plan.Plan
import com.projectxo.app.core.plan.PlanCalculator
import com.projectxo.app.core.plan.UnitMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Цели, как они записаны сейчас. */
data class CurrentGoals(val waterMl: Long, val kcal: Long, val proteinG: Long, val fatG: Long, val carbsG: Long, val steps: Long)

/** Расчёт по профилю: null, если возраст вне 18–100. */
data class PlanState(val plan: Plan?, val goals: CurrentGoals) {
    /** Цели уже равны расчётным. */
    val matches: Boolean
        get() = plan != null && goals == CurrentGoals(
            plan.waterMl.toLong(), plan.kcal.toLong(), plan.proteinG.toLong(), plan.fatG.toLong(), plan.carbsG.toLong(), plan.steps.toLong(),
        )
}

/**
 * Настройки (Р50, Р55): вид, единицы, модули, уведомления, профиль и цели.
 * Профиль и цели меняются отдельно: правка профиля цели не трогает, пока не нажали «Применить план».
 */
class SettingsViewModel(
    private val repo: SettingsRepository,
    private val goals: GoalsRepository,
    private val deleteAll: () -> Unit,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings

    /** Профиль, а если его нет (не должно быть после первого запуска), значения по умолчанию из онбординга. */
    val profile: StateFlow<Profile> = repo.settings
        .map { it.profile ?: DEFAULT_PROFILE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.settings.value.profile ?: DEFAULT_PROFILE)

    private val currentGoals = combine(
        goals.goal(GoalType.WaterMl), goals.goal(GoalType.FoodKcal), goals.goal(GoalType.ProteinG),
        goals.goal(GoalType.FatG), goals.goal(GoalType.CarbsG), goals.goal(GoalType.Steps),
    ) { v -> CurrentGoals(v[0], v[1], v[2], v[3], v[4], v[5]) }

    /** Цель воды в мл: для примера в выборе единиц. */
    val waterGoalMl: StateFlow<Long> = goals.goal(GoalType.WaterMl)
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.projectxo.app.core.model.DefaultGoals.WATER_ML)

    val planState: StateFlow<PlanState> = combine(profile, currentGoals) { p, g ->
        PlanState(runCatching { PlanCalculator.calculate(p) }.getOrNull(), g)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlanState(null, CurrentGoals(0, 0, 0, 0, 0, 0)))

    // Вид
    fun setTheme(t: ThemeChoice) = repo.update { it.copy(theme = t) }
    fun setTextSize(s: TextSize) = repo.update { it.copy(textSize = s) }
    fun setContrast(on: Boolean) = repo.update { it.copy(highContrast = on) }
    fun setReduceMotion(on: Boolean) = repo.update { it.copy(reduceMotion = on) }
    fun setUnits(u: Units) = repo.update { it.copy(units = u) }

    /** Ключ для «Еды по фото»: пробелы и переводы строк убираются; пустой или неполный не сохраняется. */
    fun setFoodAiKey(raw: String): Boolean {
        val key = raw.filterNot(Char::isWhitespace)
        if (key.length !in 30..80) return false
        repo.update { it.copy(foodAiKey = key) }
        return true
    }

    fun removeFoodAiKey() = repo.update { it.copy(foodAiKey = "") }

    fun setLanguage(l: com.projectxo.app.core.model.AppLanguage) = repo.update { it.copy(language = l) }

    /** Нужен хотя бы один модуль. */
    fun toggleModule(m: Module) = repo.update {
        val next = if (m in it.modules) it.modules - m else it.modules + m
        if (next.isEmpty()) it else it.copy(modules = next)
    }

    /** Сдвинуть модуль в порядке вкладок на [delta] мест. */
    fun moveModule(m: Module, delta: Int) = repo.update {
        val list = it.moduleOrder.toMutableList()
        val i = list.indexOf(m)
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) {
            it
        } else {
            list.removeAt(i)
            list.add(j, m)
            it.copy(moduleOrder = list)
        }
    }

    /** Поставить модуль на место [slot] нижней панели (Р76); если он уже закреплён в другом месте, они меняются местами. */
    fun setTabSlot(slot: Int, m: Module) = repo.update {
        val current = com.projectxo.app.core.model.BarLayout.pinned(it.modules, it.moduleOrder, it.tabPins)
        it.copy(tabPins = com.projectxo.app.core.model.BarLayout.withSlot(current, slot, m))
    }

    /** Вернуть выбор по умолчанию: вода, еда, тренировки. */
    fun resetTabPins() = repo.update { it.copy(tabPins = null) }

    fun setReminders(r: Reminders) = repo.update { it.copy(reminders = r) }

    /** Тихие часы двигаются по 30 минут по кругу суток. */
    fun stepQuiet(from: Boolean, deltaSteps: Int) = repo.update {
        val r = it.reminders
        val cur = if (from) r.quietFromMin else r.quietToMin
        val next = ((cur + deltaSteps * QUIET_STEP_MIN) % DAY_MIN + DAY_MIN) % DAY_MIN
        it.copy(reminders = if (from) r.copy(quietFromMin = next) else r.copy(quietToMin = next))
    }

    // Профиль
    private fun editProfile(f: (Profile) -> Profile) = repo.update { it.copy(profile = f(it.profile ?: DEFAULT_PROFILE)) }

    fun setSex(s: Sex) = editProfile { it.copy(sex = s) }
    fun setActivity(a: Activity) = editProfile { it.copy(activity = a) }
    fun setGoal(g: WeightGoal) = editProfile { it.copy(goal = g) }
    fun stepAge(d: Int) = editProfile { it.copy(ageYears = (it.ageYears + d).coerceIn(PlanCalculator.MIN_AGE, PlanCalculator.MAX_AGE)) }
    fun stepHeight(d: Int) = editProfile {
        it.copy(heightCm = (it.heightCm + d).coerceIn(PlanCalculator.MIN_HEIGHT_CM, PlanCalculator.MAX_HEIGHT_CM))
    }

    /** Шаг веса: 0,5 кг или 1 фунт — по единицам. Хранится кг с точностью до 0,1. */
    fun stepWeight(d: Int) = repo.update {
        val p = it.profile ?: DEFAULT_PROFILE
        val stepKg = if (it.units.weight == WeightUnit.Kg) 0.5 else 1 / UnitMath.LB_PER_KG
        val w = ((p.weightKg + d * stepKg) * 10).roundToInt() / 10.0
        it.copy(profile = p.copy(weightKg = w.coerceIn(PlanCalculator.MIN_WEIGHT_KG, PlanCalculator.MAX_WEIGHT_KG)))
    }

    /** Записать расчётные цели. Ничего не делает, если плана нет. */
    fun applyPlan() {
        val plan = planState.value.plan ?: return
        viewModelScope.launch {
            goals.setGoal(GoalType.WaterMl, plan.waterMl.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.FoodKcal, plan.kcal.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.ProteinG, plan.proteinG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.FatG, plan.fatG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.CarbsG, plan.carbsG.toLong().coerceAtLeast(1))
            goals.setGoal(GoalType.Steps, plan.steps.toLong().coerceAtLeast(1))
        }
    }

    /** Удалить всё. Приложение после этого стартует как новое: онбординг сбрасывается вместе с настройками. */
    fun deleteEverything() = deleteAll()

    private companion object {
        const val QUIET_STEP_MIN = 30
        const val DAY_MIN = 24 * 60
        val DEFAULT_PROFILE = Profile(Sex.Unspecified, 30, 170, 70.0, Activity.OneTwo, null)
    }
}
