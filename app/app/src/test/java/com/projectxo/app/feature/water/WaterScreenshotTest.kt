package com.projectxo.app.feature.water

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.projectxo.app.core.data.WaterEntry
import com.projectxo.app.core.ui.theme.XoTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class WaterScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.of(2026, 11, 9)

    private fun entries(vararg ml: Int): List<WaterEntry> {
        val times = listOf(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(14, 0))
        return ml.mapIndexed { i, v ->
            WaterEntry(
                id = (i + 1).toLong(),
                timestampMs = date.atTime(times[i]).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                localDate = date,
                ml = v,
            )
        }.reversed() // как в приложении: новые сверху
    }

    private fun state(vararg ml: Int) = WaterUiState(
        date = date,
        totalMl = ml.sum(),
        goalMl = 2000,
        entries = entries(*ml),
        loaded = true,
    )

    private fun shoot(name: String, dark: Boolean, state: WaterUiState, fontScale: Float? = null) {
        if (fontScale != null) RuntimeEnvironment.setFontScale(fontScale)
        composeRule.setContent {
            XoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(XoTheme.colors.background)) {
                    WaterScreen(state, onAdd = { _, _ -> }, onDelete = {}, onUndoDelete = {})
                }
            }
        }
        composeRule.waitForIdle()
        val bmp = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screens").apply { mkdirs() }
        val file = File(dir, name)
        FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name missing or empty", file.exists() && file.length() > 0)
    }

    @Test fun light1200() = shoot("water_light_1200.png", false, state(600, 250, 350))
    @Test fun dark1200() = shoot("water_dark_1200.png", true, state(600, 250, 350))
    @Test fun light2350() = shoot("water_light_2350.png", false, state(1500, 500, 350))
    @Test fun lightEmpty() = shoot("water_light_empty.png", false, WaterUiState(date = date, goalMl = 2000, loaded = true))
    @Test fun light1200Large() = shoot("water_light_1200_large.png", false, state(600, 250, 350), fontScale = 1.3f)
}
