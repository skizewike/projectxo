package com.projectxo.app.feature.food

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectxo.app.core.data.foodai.FoodPhotoAnalyzer
import com.projectxo.app.core.data.foodai.PhotoResult
import com.projectxo.app.core.data.prefs.InMemorySettingsRepository
import com.projectxo.app.core.model.AppSettings
import com.projectxo.app.core.model.Confidence
import com.projectxo.app.core.model.PhotoDish
import com.projectxo.app.core.model.PhotoItem
import com.projectxo.app.core.ui.theme.XoTheme
import com.projectxo.app.testing.FakeClock
import com.projectxo.app.testing.FakeFoodRepository
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Весь путь «Еды по фото» на экране: ключ, фото, вопросы, пересчёт, запись. Снимки в build/outputs/screens. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w360dp-h1250dp-xxhdpi")
class FoodPhotoScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dish = PhotoDish(
        name = "Гречка с курицей",
        items = listOf(
            PhotoItem("Гречка", 180, 110.0, 4.2, 1.1, 21.0),
            PhotoItem("Курица", 120, 165.0, 31.0, 3.6, 0.0),
            PhotoItem("Масло", 10, 884.0, 0.0, 100.0, 0.0, isAddedFat = true),
        ),
        confidence = Confidence.Low,
        clarify = "Сколько масла ушло на жарку?",
        healthScore = 7,
        healthReasons = listOf("Много белка, мало добавленного сахара", "Овощей мало, добавьте горсть"),
    )
    private var result: PhotoResult = PhotoResult.Ok(dish)
    private val analyzer = object : FoodPhotoAnalyzer {
        override suspend fun analyze(jpeg: ByteArray, hint: String, language: String) = result
    }
    private val settings = InMemorySettingsRepository(AppSettings(foodAiKey = "K".repeat(39)))
    private val food = FakeFoodRepository()
    private val clock = FakeClock(today = LocalDate.of(2026, 11, 10))
    private lateinit var vm: FoodPhotoViewModel
    private var backed = 0
    private var saved = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = FoodPhotoViewModel(analyzer, settings, food, clock, CoroutineScope(UnconfinedTestDispatcher()))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun show(dark: Boolean = false) = compose.setContent {
        val st by vm.state.collectAsStateWithLifecycle()
        XoTheme(darkTheme = dark) {
            FoodPhotoScreen(
                st,
                FoodPhotoActions(
                    onHint = vm::setHint, onSaveKey = vm::saveKey, onAnalyze = vm::analyze, onRetake = vm::retake,
                    onOil = vm::chooseOil, onPortion = vm::choosePortion, onCustomGrams = vm::setCustomGrams,
                    onEditing = vm::setEditing, onStep = vm::step, onSave = vm::save, setFatName = vm::setFatName,
                ),
                onBack = { backed++ }, onSaved = { saved++ },
            )
        }
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(File("build/outputs/screens").apply { mkdirs() }, name)
        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    private fun analyzeNow() = vm.analyze("Russian") { byteArrayOf(1, 2, 3) }

    @Test
    fun withoutKey_showsKeyPanelAndRejectsShortKey() {
        settings.update { it.copy(foodAiKey = "") }
        vm = FoodPhotoViewModel(analyzer, settings, food, clock, CoroutineScope(UnconfinedTestDispatcher()))
        show()
        compose.onNodeWithText("Сохранить ключ").assertExists()
        shoot("foodphoto_0_key.png")
        vm.saveKey("короткий")
        assertTrue(vm.state.value.keyError)
        assertFalse(vm.state.value.hasKey)
        vm.saveKey("  AIza" + "x".repeat(34) + "\n")
        assertTrue(vm.state.value.hasKey)
        assertEquals("AIza" + "x".repeat(34), settings.settings.value.foodAiKey)
    }

    /** «Отправляем» (Р82) до сигнала «снимок ушёл», потом «Считаем». Часы теста ведём вручную: анимации бесконечные. */
    @Test
    fun working_showsUploadFirstThenCounting() {
        val sent = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : FoodPhotoAnalyzer {
            override suspend fun analyze(jpeg: ByteArray, hint: String, language: String) = result
            override suspend fun analyze(jpeg: ByteArray, hint: String, language: String, onUploaded: () -> Unit): PhotoResult {
                sent.await()
                onUploaded()
                release.await()
                return result
            }
        }
        vm = FoodPhotoViewModel(slow, settings, food, clock, CoroutineScope(UnconfinedTestDispatcher()))
        compose.mainClock.autoAdvance = false
        show()
        analyzeNow()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Отправляем снимок").assertExists()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(File("build/outputs/screens").apply { mkdirs() }, "foodphoto_6_upload.png")
        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        sent.complete(Unit)
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Считаем").assertExists()
        release.complete(Unit)
    }

    @Test
    fun pickStep_showsPhotoButtons() {
        show()
        compose.onNodeWithText("Снять").assertExists()
        compose.onNodeWithText("Выбрать из галереи").assertExists()
        shoot("foodphoto_1_pick.png")
    }

    @Test
    fun fullFlow_answerQuestions_recalculates_andSaves() {
        show()
        analyzeNow()
        compose.waitForIdle()
        assertEquals(PhotoStep.Result, vm.state.value.step)
        compose.onNodeWithText("Сколько масла ушло на жарку?").assertExists()
        compose.onNodeWithText("Польза 7 из 10", useUnmergedTree = true).assertExists()
        assertEquals(484, vm.state.value.totals.kcal) // 198 + 198 + 88
        shoot("foodphoto_2_result.png")

        compose.onNodeWithText("Без масла").performClick()
        assertEquals(396, vm.state.value.totals.kcal)
        compose.onNodeWithText("Много масла").performClick()
        assertEquals(396 + 133, vm.state.value.totals.kcal) // 15 г масла = 133 ккал
        compose.onNodeWithText("Больше 400 г").performClick()
        assertEquals(450, vm.state.value.totals.grams) // «Больше 400 г» = вся порция, масло внутри

        compose.onNodeWithText("Править состав").performClick()
        compose.waitForIdle()
        shoot("foodphoto_3_edit.png")

        compose.onNodeWithText("Записать в «Еду»").performClick()
        compose.waitForIdle()
        assertEquals(1, saved)
        val entry = runBlocking { food.entriesFor(clock.today()).first() }.single()
        assertEquals("Гречка с курицей", entry.name)
        assertEquals(vm.state.value.totals.kcal, entry.kcal)
    }

    @Test
    fun customWeight_scalesPortion() {
        show()
        analyzeNow()
        compose.onNodeWithText("Свой вес").performClick()
        vm.setCustomGrams("500")
        assertEquals(500, vm.state.value.totals.grams)
        // Вне границ вес игнорируется: порция остаётся прежней.
        vm.setCustomGrams("10")
        assertEquals(500, vm.state.value.totals.grams)
    }

    @Test
    fun asPhoto_restoresOriginalOil() {
        analyzeNow()
        vm.chooseOil(com.projectxo.app.core.model.OilChoice.None)
        assertTrue(vm.state.value.items.none { it.isAddedFat })
        vm.chooseOil(com.projectxo.app.core.model.OilChoice.AsPhoto)
        assertEquals(10, vm.state.value.items.single { it.isAddedFat }.grams)
    }

    @Test
    fun manualStep_changesGramsAndClearsPortionChoice() {
        analyzeNow()
        vm.choosePortion(com.projectxo.app.core.model.PortionChoice.Small)
        vm.step(0, 1)
        assertEquals(com.projectxo.app.core.model.PortionChoice.AsPhoto, vm.state.value.portion)
    }

    @Test
    fun notFood_showsMessage() {
        result = PhotoResult.NotFood
        show()
        analyzeNow()
        compose.waitForIdle()
        compose.onNodeWithText("На фото не видно еды. Снимите блюдо ближе и сверху.").assertExists()
        shoot("foodphoto_4_notfood.png")
    }

    @Test
    fun badKey_clearsStoredKeyAndAsksAgain() {
        result = PhotoResult.Fail(com.projectxo.app.core.data.foodai.PhotoError.BadKey)
        show()
        analyzeNow()
        compose.waitForIdle()
        assertEquals("", settings.settings.value.foodAiKey)
        compose.onNodeWithText("Сохранить ключ").assertExists()
    }

    @Test
    fun saveWithoutResult_doesNothing() {
        assertFalse(vm.save("Без названия"))
        assertTrue(runBlocking { food.entriesFor(clock.today()).first() }.isEmpty())
    }

    @Test
    fun darkTheme_result() {
        show(dark = true)
        analyzeNow()
        shoot("foodphoto_5_result_dark.png")
    }
}
