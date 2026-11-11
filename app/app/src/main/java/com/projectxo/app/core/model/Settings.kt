package com.projectxo.app.core.model

// Настройки и профиль пользователя. Чистый Kotlin без android.* (готово к Compose Multiplatform).

/** Тема приложения (Р49). Warm следует за светлой и тёмной темой телефона, остальные всегда тёмные. */
enum class ThemeChoice { Warm, Acid, Volt, Blood }

/** Размер текста поверх системного масштаба. */
enum class TextSize(val scale: Float) { Small(0.9f), Normal(1f), Large(1.15f), Huge(1.3f) }

enum class WeightUnit { Kg, Lb }
enum class HeightUnit { Cm, FtIn }
enum class VolumeUnit { Ml, Oz }

data class Units(
    val weight: WeightUnit = WeightUnit.Kg,
    val height: HeightUnit = HeightUnit.Cm,
    val volume: VolumeUnit = VolumeUnit.Ml,
)

enum class Sex { Male, Female, Unspecified }

/** Тренировок в неделю (docs/PLAN-CALC.md, раздел 3). */
enum class Activity { None, OneTwo, ThreeFour, FiveSix, Daily }

enum class WeightGoal { Lose, Keep, Gain }

/** То, что пользователь указал о себе. Хранится в кг и см; единицы — только для показа. */
data class Profile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Double,
    val activity: Activity,
    /** null — пользователь не выбирал, берётся подсказка по ИМТ. */
    val goal: WeightGoal?,
)

data class Reminders(
    val water: Boolean = true,
    val dailySummary: Boolean = false,
    /** Итог недели: по воскресеньям вечером, по желанию. */
    val weeklySummary: Boolean = false,
    /** Тихие часы, минуты от полуночи. */
    val quietFromMin: Int = 22 * 60,
    val quietToMin: Int = 8 * 60,
)

/** Вид экрана «Привычки» (Р43): по умолчанию «Кольцо дня». */
enum class HabitsView { Ring, List, Cards }

/** Как считать подходы в тренировке (Р47). */
enum class WorkoutCounting { Ladder, Tap }

/** Вид таймера отдыха и удержания (Р64): кольцо по умолчанию, полоса, деления. */
enum class WorkoutTimerStyle { Ring, Bar, Ticks }

/** Настройки модулей волны 2. */
/** Язык интерфейса: по телефону или выбранный в приложении. */
enum class AppLanguage { System, Ru, En }

data class Wave2Settings(
    val habitsView: HabitsView = HabitsView.Ring,
    val habitsSeeded: Boolean = false,
    /** Дни плана тренировок: маска пн = 1 … вс = 64 (Р73). */
    val workoutDays: Int = HabitMath.WORKOUT_DAYS,
    val workoutCounting: WorkoutCounting = WorkoutCounting.Ladder,
    val workoutTimer: WorkoutTimerStyle = WorkoutTimerStyle.Ring,
    /** Привычки вместе (Р79): вид (по умолчанию «Пересечение») и псевдоним, который видит друг. */
    val sharedView: SharedView = SharedView.Intersection,
    val sharedNick: String = "",
    /** Сон: плановый отбой и подъём, минуты от полуночи; напоминание за 30 минут до отбоя. */
    val sleepBedMin: Int = 23 * 60,
    val sleepWakeMin: Int = 7 * 60,
    val sleepRemind: Boolean = true,
    /** Голодание: часы цели и ответ на вопросы безопасности. */
    val fastingPlanHours: Int = FastingMath.DEFAULT_PLAN,
    val fastingSafetyAccepted: Boolean = false,
    /** Дыхание: последний ритм и длительность. */
    val breathRhythm: BreathRhythm = BreathRhythm.Even,
    val breathMinutes: Int = BreathingMath.DEFAULT_MIN,
)

data class AppSettings(
    val onboarded: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.Warm,
    val textSize: TextSize = TextSize.Normal,
    val highContrast: Boolean = false,
    /** Меньше анимаций по выбору пользователя (системная настройка учитывается отдельно). */
    val reduceMotion: Boolean = false,
    val language: AppLanguage = AppLanguage.System,
    val units: Units = Units(),
    val modules: Set<Module> = Module.BASE,
    /** Порядок модулей во вкладках и на «Сегодня» (Р38). */
    val moduleOrder: List<Module> = Module.entries,
    /** Закреплённые во вкладках модули (Р76); null — по умолчанию вода, еда, тренировки. */
    val tabPins: List<Module>? = null,
    val profile: Profile? = null,
    val reminders: Reminders = Reminders(),
    val wave2: Wave2Settings = Wave2Settings(),
    /** Системное окно разрешения на уведомления уже показывали (спрашиваем один раз сами). */
    val notificationPermissionAsked: Boolean = false,
    /** День первого запуска, epoch day. Дни раньше в календаре недоступны (Р41). */
    val startEpochDay: Long? = null,
    /** Свой бесплатный ключ Google AI Studio для «Еды по фото» (Р69, В7). Только на телефоне. Пусто — ключа нет. */
    val foodAiKey: String = "",
    /** Случайный номер установки для счётчика лимитов общего посредника «Еды по фото» (Р70). Личных данных в нём нет. */
    val installId: String = "",
    /** Пользователь с VPN (Р80): снимок для «Еды по фото» идёт сразу на посредника, без российского ретранслятора. */
    val usesVpn: Boolean = false,
)
