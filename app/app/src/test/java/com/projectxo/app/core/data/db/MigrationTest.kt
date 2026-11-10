package com.projectxo.app.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        XoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsDataAndAddsStepsTables() {
        // Полный путь: иначе Room на Windows под Robolectric не узнаёт файл базы.
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test").absolutePath
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO water_entry(id, ts, localDate, ml) VALUES(1, 1000, '2026-05-01', 250)")
            execSQL("INSERT INTO goal(type, target) VALUES('WaterMl', 2500)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, XoDatabase.MIGRATION_1_2)

        db.query("SELECT ml FROM water_entry WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(250, it.getInt(0))
        }
        db.query("SELECT target FROM goal WHERE type = 'WaterMl'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(2500L, it.getLong(0))
        }
        db.query("SELECT COUNT(*) FROM steps_hour").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM sensor_reading").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate2To3KeepsDataAndAddsFoodTable() {
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test-3").absolutePath
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO water_entry(id, ts, localDate, ml) VALUES(1, 1000, '2026-05-01', 250)")
            execSQL("INSERT INTO goal(type, target) VALUES('FoodKcal', 1900)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, XoDatabase.MIGRATION_2_3)

        db.query("SELECT ml FROM water_entry WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(250, it.getInt(0))
        }
        db.query("SELECT target FROM goal WHERE type = 'FoodKcal'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(1900L, it.getLong(0))
        }
        db.execSQL("INSERT INTO food_entry(ts, localDate, name, kcal, protein, fat, carbs) VALUES(1, '2026-05-01', 'x', 100, 1, 2, 3)")
        db.query("SELECT COUNT(*) FROM food_entry").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrate4To5KeepsDataAndAddsWave2Tables() {
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test-5").absolutePath
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO water_entry(id, ts, localDate, ml) VALUES(1, 1000, '2026-05-01', 250)")
            execSQL("INSERT INTO day_log(localDate, feel, tags, weightTenths, note) VALUES('2026-05-01', 3, '', 718, '')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 5, true, XoDatabase.MIGRATION_4_5)

        db.query("SELECT ml FROM water_entry WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(250, it.getInt(0))
        }
        db.query("SELECT weightTenths FROM day_log").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(718, it.getInt(0))
        }
        db.execSQL("INSERT INTO sleep_night(startMs, endMs, rate) VALUES(1, 28800001, 3)")
        db.execSQL("INSERT INTO fasting_session(startMs, endMs, planHours) VALUES(1, NULL, 16)")
        db.execSQL("INSERT INTO breathing_session(ts, localDate, seconds, rhythm, cycles) VALUES(1, '2026-05-01', 180, 'Even', 11)")
        db.execSQL("INSERT INTO habit(name, freqType, freqN, daysMask, reminderMin, target, sortOrder, kind) VALUES('x', 'Daily', 0, 127, NULL, 1, 0, 'Habit')")
        db.execSQL("INSERT INTO habit_check(habitId, localDate, value) VALUES(1, '2026-05-01', 1)")
        db.execSQL("INSERT INTO workout_program(exercise, level, daysMask) VALUES('pushups', 10, 21)")
        db.execSQL("INSERT INTO workout_session(localDate, exercise, level, plan, actual, rating, ts) VALUES('2026-05-01', 'pushups', 10, '6,5,5,4,5', '6,5,5,4,5', 1, 1)")
        listOf("sleep_night", "fasting_session", "breathing_session", "habit", "habit_check", "workout_program", "workout_session").forEach { t ->
            db.query("SELECT COUNT(*) FROM $t").use {
                it.moveToFirst()
                assertEquals(t, 1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate5To6KeepsFoodAndAddsPhotoColumns() {
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test-6").absolutePath
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO food_entry(ts, localDate, name, kcal, protein, fat, carbs) VALUES(1, '2026-05-01', 'Овсянка', 320, 10, 6, 58)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 6, true, XoDatabase.MIGRATION_5_6)

        db.query("SELECT name, kcal, photoFile, healthScore FROM food_entry").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Овсянка", it.getString(0))
            assertEquals(320, it.getInt(1))
            assertEquals(true, it.isNull(2))
            assertEquals(true, it.isNull(3))
        }
        db.execSQL("INSERT INTO food_entry(ts, localDate, name, kcal, protein, fat, carbs, photoFile, healthScore) VALUES(2, '2026-05-01', 'Печень', 621, 61, 36, 11, 'a.jpg', 7)")
        db.query("SELECT photoFile, healthScore FROM food_entry WHERE id = 2").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("a.jpg", it.getString(0))
            assertEquals(7, it.getInt(1))
        }
    }

    @Test
    fun migrate3To4KeepsDataAndAddsDayLog() {
        val name = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test-4").absolutePath
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO food_entry(ts, localDate, name, kcal, protein, fat, carbs) VALUES(1, '2026-05-01', 'x', 100, 1, 2, 3)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 4, true, XoDatabase.MIGRATION_3_4)

        db.query("SELECT kcal FROM food_entry").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(100, it.getInt(0))
        }
        db.execSQL("INSERT INTO day_log(localDate, feel, tags, weightTenths, note) VALUES('2026-05-01', 3, 'tired,head', 718, 'ok')")
        db.query("SELECT weightTenths FROM day_log").use {
            it.moveToFirst()
            assertEquals(718, it.getInt(0))
        }
    }
}
