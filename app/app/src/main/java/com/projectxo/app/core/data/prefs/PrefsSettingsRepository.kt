package com.projectxo.app.core.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.projectxo.app.core.data.SettingsRepository
import com.projectxo.app.core.model.Activity
import com.projectxo.app.core.model.BreathRhythm
import com.projectxo.app.core.model.BreathingMath
import com.projectxo.app.core.model.FastingMath
import com.projectxo.app.core.model.HabitsView
import com.projectxo.app.core.model.Wave2Settings
import com.projectxo.app.core.model.WorkoutCounting
import com.projectxo.app.core.model.WorkoutTimerStyle
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.HeightUnit
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.Profile
import com.projectxo.app.core.model.Reminders
import com.projectxo.app.core.model.Sex
import com.projectxo.app.core.model.TextSize
import com.projectxo.app.core.model.ThemeChoice
import com.projectxo.app.core.model.Units
import com.projectxo.app.core.model.VolumeUnit
import com.projectxo.app.core.model.WeightGoal
import com.projectxo.app.core.model.AppLanguage
import com.projectxo.app.core.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки в SharedPreferences (Р55). Отдельные ключи, без сериализации: файл маленький,
 * а неизвестное значение после обновления приложения просто даёт значение по умолчанию.
 */
class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read(prefs))
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    @Synchronized
    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        write(prefs, next)
        state.value = next
    }

    companion object {
        const val FILE = "xo_settings"

        private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
            getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

        /** Порядок модулей: сохранённый, а новые модули (после обновления) добавляются в конец. */
        private fun readOrder(raw: String?): List<Module> {
            val saved = raw?.split(',')?.mapNotNull { n -> Module.entries.firstOrNull { it.name == n } }.orEmpty().distinct()
            return saved + Module.entries.filterNot { it in saved }
        }

        internal fun read(p: SharedPreferences): AppSettings {
            val d = AppSettings()
            val profile = if (p.contains("p_age")) {
                Profile(
                    sex = p.enum("p_sex", Sex.Unspecified),
                    ageYears = p.getInt("p_age", 30),
                    heightCm = p.getInt("p_height", 170),
                    weightKg = p.getFloat("p_weight", 70f).toDouble(),
                    activity = p.enum("p_activity", Activity.OneTwo),
                    goal = p.getString("p_goal", null)?.let { n -> WeightGoal.entries.firstOrNull { it.name == n } },
                )
            } else {
                null
            }
            val modules = p.getStringSet("modules", null)
                ?.mapNotNull { n -> Module.entries.firstOrNull { it.name == n } }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: d.modules
            return AppSettings(
                onboarded = p.getBoolean("onboarded", false),
                theme = p.enum("theme", d.theme),
                textSize = p.enum("text_size", d.textSize),
                highContrast = p.getBoolean("high_contrast", false),
                reduceMotion = p.getBoolean("reduce_motion", false),
                language = p.enum("language", AppLanguage.System),
                units = Units(p.enum("u_weight", WeightUnit.Kg), p.enum("u_height", HeightUnit.Cm), p.enum("u_volume", VolumeUnit.Ml)),
                modules = modules,
                moduleOrder = readOrder(p.getString("module_order", null)),
                wave2 = Wave2Settings(
                    habitsView = p.enum("w_habits_view", HabitsView.Ring),
                    habitsSeeded = p.getBoolean("w_habits_seeded", false),
                    workoutEnabled = p.getBoolean("w_workout", false),
                    workoutCounting = p.enum("w_workout_count", WorkoutCounting.Ladder),
                    workoutTimer = p.enum("w_workout_timer", WorkoutTimerStyle.Ring),
                    sleepBedMin = p.getInt("w_sleep_bed", 23 * 60),
                    sleepWakeMin = p.getInt("w_sleep_wake", 7 * 60),
                    sleepRemind = p.getBoolean("w_sleep_remind", true),
                    fastingPlanHours = p.getInt("w_fast_plan", FastingMath.DEFAULT_PLAN),
                    fastingSafetyAccepted = p.getBoolean("w_fast_safety", false),
                    breathRhythm = BreathRhythm.fromId(p.getString("w_breath_rhythm", null)),
                    breathMinutes = p.getInt("w_breath_min", BreathingMath.DEFAULT_MIN),
                ),
                profile = profile,
                reminders = Reminders(
                    water = p.getBoolean("r_water", true),
                    dailySummary = p.getBoolean("r_summary", false),
                    weeklySummary = p.getBoolean("r_weekly", false),
                    quietFromMin = p.getInt("r_quiet_from", 22 * 60),
                    quietToMin = p.getInt("r_quiet_to", 8 * 60),
                ),
                notificationPermissionAsked = p.getBoolean("notif_asked", false),
                startEpochDay = if (p.contains("start_day")) p.getLong("start_day", 0) else null,
                foodAiKey = p.getString("food_ai_key", "").orEmpty(),
                installId = p.getString("install_id", "").orEmpty(),
            )
        }

        internal fun write(p: SharedPreferences, s: AppSettings) = p.edit {
            putBoolean("onboarded", s.onboarded)
            putString("theme", s.theme.name)
            putString("text_size", s.textSize.name)
            putBoolean("high_contrast", s.highContrast)
            putBoolean("reduce_motion", s.reduceMotion)
            putString("language", s.language.name)
            putString("u_weight", s.units.weight.name)
            putString("u_height", s.units.height.name)
            putString("u_volume", s.units.volume.name)
            putStringSet("modules", s.modules.map { it.name }.toSet())
            putString("module_order", s.moduleOrder.joinToString(",") { it.name })
            putString("w_habits_view", s.wave2.habitsView.name)
            putBoolean("w_habits_seeded", s.wave2.habitsSeeded)
            putBoolean("w_workout", s.wave2.workoutEnabled)
            putString("w_workout_count", s.wave2.workoutCounting.name)
            putString("w_workout_timer", s.wave2.workoutTimer.name)
            putInt("w_sleep_bed", s.wave2.sleepBedMin)
            putInt("w_sleep_wake", s.wave2.sleepWakeMin)
            putBoolean("w_sleep_remind", s.wave2.sleepRemind)
            putInt("w_fast_plan", s.wave2.fastingPlanHours)
            putBoolean("w_fast_safety", s.wave2.fastingSafetyAccepted)
            putString("w_breath_rhythm", s.wave2.breathRhythm.id)
            putInt("w_breath_min", s.wave2.breathMinutes)
            putBoolean("r_water", s.reminders.water)
            putBoolean("r_summary", s.reminders.dailySummary)
            putBoolean("r_weekly", s.reminders.weeklySummary)
            putInt("r_quiet_from", s.reminders.quietFromMin)
            putInt("r_quiet_to", s.reminders.quietToMin)
            putBoolean("notif_asked", s.notificationPermissionAsked)
            if (s.startEpochDay != null) putLong("start_day", s.startEpochDay) else remove("start_day")
            if (s.foodAiKey.isNotEmpty()) putString("food_ai_key", s.foodAiKey) else remove("food_ai_key")
            if (s.installId.isNotEmpty()) putString("install_id", s.installId) else remove("install_id")
            val pr = s.profile
            if (pr == null) {
                listOf("p_sex", "p_age", "p_height", "p_weight", "p_activity", "p_goal").forEach(::remove)
            } else {
                putString("p_sex", pr.sex.name)
                putInt("p_age", pr.ageYears)
                putInt("p_height", pr.heightCm)
                putFloat("p_weight", pr.weightKg.toFloat())
                putString("p_activity", pr.activity.name)
                if (pr.goal != null) putString("p_goal", pr.goal.name) else remove("p_goal")
            }
        }
    }
}

/** Для тестов и превью: в памяти. */
class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()
    override fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
