package com.projectxo.app

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.projectxo.app.core.motion.SplashMode
import com.projectxo.app.core.ui.theme.XoTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.projectxo.app.core.data.prefs.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системная заставка: только фон того же цвета. Логотип рисует Compose (docs/DESIGN.md, 6.6).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val reducedMotion = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
        val forced = if (BuildConfig.DEBUG) {
            when (intent.getStringExtra(EXTRA_SPLASH)) {
                "full" -> SplashMode.Full
                "short" -> SplashMode.Short
                else -> null
            }
        } else {
            null
        }

        // Шаги вживую, пока приложение на экране (Р25).
        val container = (application as XoApplication).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.stepsLive.refreshPermission()
                // Телефон без датчика шагов: счёт по движению, если включён (Р65). Запуск только с экрана — так требует Android.
                container.motionSteps.ensureRunning()
                container.stepsLive.run()
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            // Язык сменили в настройках: экран пересоздаётся на новом языке.
            val startLanguage = remember { settings.language }
            androidx.compose.runtime.LaunchedEffect(settings.language) { if (settings.language != startLanguage) recreate() }
            XoTheme(
                theme = settings.theme,
                highContrast = settings.highContrast,
                textScale = settings.textSize.scale,
            ) {
                // Значки в строке состояния и панели жестов: светлые на тёмной теме и тёмные на светлой.
                val dark = XoTheme.colors.isDark
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.projectxo.app.core.ui.format.LocalVolumeUnit provides settings.units.volume,
                ) {
                    XoApp(
                        container = container,
                        // Системное «убрать анимации» или выбор в приложении.
                        reducedMotion = reducedMotion || settings.reduceMotion,
                        // После поворота экрана или пересоздания заставку не повторяем.
                        showSplash = savedInstanceState == null,
                        forcedSplash = forced,
                    )
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SPLASH = "xo.splash"
    }
}
