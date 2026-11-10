package com.projectxo.app.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WaterEntryEntity::class,
        GoalEntity::class,
        StepsHourEntity::class,
        SensorReadingEntity::class,
        FoodEntryEntity::class,
        DayLogEntity::class,
        SleepNightEntity::class,
        FastingEntity::class,
        BreathingEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        WorkoutProgramEntity::class,
        WorkoutSessionEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class XoDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao
    abstract fun goalDao(): GoalDao
    abstract fun stepsDao(): StepsDao
    abstract fun foodDao(): FoodDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun sleepDao(): SleepDao
    abstract fun fastingDao(): FastingDao
    abstract fun breathingDao(): BreathingDao
    abstract fun habitDao(): HabitDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        /** Еда по фото (Р71): имя файла снимка и оценка пользы у записи еды. Старые записи остаются без фото. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `photoFile` TEXT")
                db.execSQL("ALTER TABLE `food_entry` ADD COLUMN `healthScore` INTEGER")
            }
        }

        /** Добавляет модули волны 2: сон, голодание, дыхание, привычки, тренировки. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_night` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER, `rate` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `fasting_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER, `planHours` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `breathing_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ts` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `seconds` INTEGER NOT NULL, `rhythm` TEXT NOT NULL, `cycles` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_breathing_session_localDate` ON `breathing_session` (`localDate`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `freqType` TEXT NOT NULL, `freqN` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `reminderMin` INTEGER, `target` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `kind` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `habit_check` (`habitId` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `localDate`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_program` (`exercise` TEXT NOT NULL, `level` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, PRIMARY KEY(`exercise`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `localDate` TEXT NOT NULL, `exercise` TEXT NOT NULL, `level` INTEGER NOT NULL, `plan` TEXT NOT NULL, `actual` TEXT NOT NULL, `rating` INTEGER, `ts` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_localDate` ON `workout_session` (`localDate`)")
            }
        }

        /** Добавляет заметки дня: самочувствие, вес, текст. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_log` (`localDate` TEXT NOT NULL, `feel` INTEGER, " +
                        "`tags` TEXT NOT NULL, `weightTenths` INTEGER, `note` TEXT NOT NULL, " +
                        "PRIMARY KEY(`localDate`))",
                )
            }
        }

        /** Добавляет записи еды. Цели БЖУ лежат в уже существующей таблице goal. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ts` INTEGER NOT NULL, `localDate` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kcal` INTEGER NOT NULL, `protein` INTEGER NOT NULL, `fat` INTEGER NOT NULL, " +
                        "`carbs` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_entry_localDate` ON `food_entry` (`localDate`)")
            }
        }

        /** Добавляет шаги по часам и последнее показание датчика. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `steps_hour` (`localDate` TEXT NOT NULL, " +
                        "`hour` INTEGER NOT NULL, `steps` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`localDate`, `hour`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sensor_reading` (`id` INTEGER NOT NULL, " +
                        "`counter` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        fun build(context: Context): XoDatabase =
            Room.databaseBuilder(context, XoDatabase::class.java, "xo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
