package com.projectxo.app.feature.food

import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.data.foodai.FoodPhotoAnalyzer
import com.projectxo.app.core.data.foodai.PhotoResult
import com.projectxo.app.core.data.photo.FoodPhotoStore
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Confidence
import com.projectxo.app.core.model.PhotoDish
import com.projectxo.app.core.model.PhotoItem
import com.projectxo.app.core.model.UndoPolicy
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeFoodRepository
import com.projectxo.app.testing.FakeGoalsRepository
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class FoodPhotoStorageTest {
    private lateinit var dir: File
    private lateinit var store: FoodPhotoStore
    private val food = FakeFoodRepository()
    private val clock = FakeClock(today = LocalDate.of(2026, 11, 10))
    private val dish = PhotoDish(
        "Куриная печень с луком",
        listOf(PhotoItem("Печень", 200, 160.0, 20.0, 5.0, 3.0)),
        Confidence.Medium, null, 7, listOf("Много белка"),
    )
    private val analyzer = object : FoodPhotoAnalyzer {
        override suspend fun analyze(jpeg: ByteArray, hint: String, language: String) = PhotoResult.Ok(dish)
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("xo-photos").toFile()
        store = FoodPhotoStore(File(dir, "food_photos"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun entries() = runBlocking { food.entriesFor(clock.today()).first() }

    @Test
    fun store_saveReadDelete() {
        val name = store.save(byteArrayOf(1, 2, 3))
        assertTrue(name.endsWith(".jpg"))
        assertEquals(3, store.file(name)!!.length())
        store.delete(name)
        assertNull(store.file(name))
    }

    @Test
    fun store_rejectsNamesOutsideItsFormat() {
        File(dir, "secret.txt").writeText("x")
        assertNull(store.file("../secret.txt"))
        assertNull(store.file("secret.txt"))
        assertNull(store.file(null))
        store.delete("../secret.txt")
        assertTrue(File(dir, "secret.txt").exists())
    }

    @Test
    fun store_deleteAllClearsEverything() {
        val a = store.save(byteArrayOf(1))
        val b = store.save(byteArrayOf(2))
        store.deleteAll()
        assertNull(store.file(a))
        assertNull(store.file(b))
    }

    @Test
    fun save_writesPhotoAndScoreWithTheEntry() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()), photos = store,
        )
        vm.analyze("Russian") { byteArrayOf(9, 9, 9, 9) }
        assertTrue(vm.save("Без названия"))
        val entry = entries().single()
        assertEquals(7, entry.healthScore)
        val file = store.file(entry.photoFile)
        assertNotNull(file)
        assertEquals(4, file!!.length())
    }

    @Test
    fun reset_forgetsResultAndPhoto_soNextOpeningStartsFresh() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()), photos = store,
        )
        vm.analyze("Russian") { byteArrayOf(1, 2, 3) }
        assertEquals(PhotoStep.Result, vm.state.value.step)
        assertNotNull(vm.state.value.photo)

        vm.reset()

        assertEquals(PhotoStep.Pick, vm.state.value.step)
        assertNull(vm.state.value.photo)
        assertNull(vm.state.value.dish)
        assertFalse("после сброса записывать нечего", vm.save("Без названия"))
        assertTrue(entries().isEmpty())
    }

    @Test
    fun analyze_exposesPhotoWhileResultIsShown() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        vm.analyze("Russian") { byteArrayOf(7, 7) }
        assertEquals(2, vm.state.value.photo!!.size)
    }

    @Test
    fun retry_afterNetworkError_usesSamePhotoFromMemory() {
        var calls = 0
        val seen = mutableListOf<Int>()
        val flaky = object : FoodPhotoAnalyzer {
            override suspend fun analyze(jpeg: ByteArray, hint: String, language: String): PhotoResult {
                seen += jpeg.size
                return if (++calls == 1) PhotoResult.Fail(com.projectxo.app.core.data.foodai.PhotoError.Network) else PhotoResult.Ok(dish)
            }
        }
        val vm = FoodPhotoViewModel(
            flaky, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        var reads = 0
        vm.analyze("Russian") { reads++; byteArrayOf(1, 2, 3) }
        assertEquals(PhotoStep.Failed, vm.state.value.step)

        vm.retry("Russian")

        assertEquals(PhotoStep.Result, vm.state.value.step)
        assertEquals("снимок читается из файла один раз", 1, reads)
        assertEquals(listOf(3, 3), seen)
    }

    @Test
    fun unreadablePhoto_isReportedAsPhotoProblem_notAsBadAnswer() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        vm.analyze("Russian") { null }
        assertEquals(PhotoStep.Failed, vm.state.value.step)
        assertEquals(com.projectxo.app.core.data.foodai.PhotoError.BadPhoto, vm.state.value.error)
    }

    @Test
    fun retake_dropsPhotoSoRetryHasNothingStale() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        vm.analyze("Russian") { byteArrayOf(1) }
        vm.retake()
        assertNull(vm.state.value.photo)
        assertEquals(PhotoStep.Pick, vm.state.value.step)
        vm.retry("Russian") // нечего повторять: остаёмся на выборе фото
        assertEquals(PhotoStep.Pick, vm.state.value.step)
    }

    @Test
    fun save_withoutPhotoStore_stillSavesEntryWithoutPhoto() {
        val vm = FoodPhotoViewModel(
            analyzer, InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39))), food, clock,
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        vm.analyze("Russian") { byteArrayOf(1) }
        assertTrue(vm.save("Без названия"))
        assertNull(entries().single().photoFile)
    }

    @Test
    fun deletingEntry_removesPhotoFileAfterUndoWindow() {
        val name = store.save(byteArrayOf(1, 2))
        runBlocking { food.add(FoodDraft("Печень", 300, 20, 10, 5, name, 7), 1L, clock.today()) }
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val vm = FoodViewModel(food, FakeGoalsRepository(), clock, CoroutineScope(UnconfinedTestDispatcher(scheduler)), store)
        val entry = entries().single()

        vm.delete(entry)
        scheduler.runCurrent()
        assertNotNull("файл живёт, пока можно нажать «Вернуть»", store.file(name))

        scheduler.advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        scheduler.runCurrent()
        assertTrue(entries().isEmpty())
        assertNull(store.file(name))
    }

    @Test
    fun undoingDelete_keepsPhotoFile() {
        val name = store.save(byteArrayOf(1, 2))
        runBlocking { food.add(FoodDraft("Печень", 300, 20, 10, 5, name, 7), 1L, clock.today()) }
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        val vm = FoodViewModel(food, FakeGoalsRepository(), clock, CoroutineScope(UnconfinedTestDispatcher(scheduler)), store)

        vm.delete(entries().single())
        scheduler.runCurrent()
        vm.undoDelete()
        scheduler.advanceTimeBy(UndoPolicy.WINDOW_MS + 100)
        scheduler.runCurrent()
        assertEquals(1, entries().size)
        assertNotNull(store.file(name))
    }
}
