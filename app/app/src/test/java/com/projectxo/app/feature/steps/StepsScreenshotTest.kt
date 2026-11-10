package com.projectxo.app.feature.steps

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.projectxo.app.core.ui.theme.XoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** Снимки экрана «Ходьба» без эмулятора (build/outputs/screens). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class StepsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val date = LocalDate.of(2026, 11, 9)
    private val day = listOf(0, 0, 0, 0, 0, 0, 0, 120, 640, 880, 310, 260, 1420, 980, 1870)
    private fun hours(extra: Int = 0) = List(24) { h -> day.getOrElse(h) { 0 } + if (h == 14) extra else 0 }

    private fun shoot(name: String, dark: Boolean, state: StepsUiState, access: StepsAccess = StepsAccess.Granted) {
        rule.setContent {
            XoTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(XoTheme.colors.background)) {
                    StepsScreen(state, access, onAllow = {}, nowHour = 14)
                }
            }
        }
        rule.waitForIdle()
        val file = File(File("build/outputs/screens").apply { mkdirs() }, name)
        FileOutputStream(file).use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 0)
    }

    @Test fun light() = shoot("steps_light.png", false, StepsUiState(date, hours(), loaded = true))
    @Test fun dark() = shoot("steps_dark.png", true, StepsUiState(date, hours(), loaded = true))
    @Test fun done() = shoot("steps_done.png", false, StepsUiState(date, hours(extra = 1800), loaded = true))
    @Test fun permission() = shoot("steps_perm.png", false, StepsUiState(date, loaded = true), StepsAccess.NeedPermission)
}
