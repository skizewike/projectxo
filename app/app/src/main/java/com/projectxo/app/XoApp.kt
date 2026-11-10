package com.projectxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import com.projectxo.app.core.model.DayRollover
import com.projectxo.app.core.model.WaterPortions
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.projectxo.app.core.motion.SplashMode
import com.projectxo.app.core.motion.SplashPolicy
import com.projectxo.app.core.ui.motion.InkFlowController
import com.projectxo.app.core.ui.motion.InkFlowLayer
import com.projectxo.app.core.ui.motion.LocalInkFlow
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.splash.LocalSplash
import com.projectxo.app.core.ui.splash.SplashController
import com.projectxo.app.core.ui.splash.SplashOverlay
import com.projectxo.app.core.ui.theme.XoTheme
import com.projectxo.app.feature.home.TodayViewModel
import com.projectxo.app.feature.onboarding.OnboardingScreen
import com.projectxo.app.feature.onboarding.OnboardingViewModel
import com.projectxo.app.navigation.XoNavHost
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * Корень приложения: вкладки и поверх них заставка.
 * [forcedSplash] — только для проверки в отладочной сборке (adb ... -e xo.splash full|short).
 */
@Composable
fun XoApp(
    container: AppContainer,
    reducedMotion: Boolean,
    showSplash: Boolean,
    forcedSplash: SplashMode?,
) {
    // Текущий день: при смене (полночь при открытом приложении, возврат из фона) экраны получают новые модели.
    val currentDate by produceState(container.clock.today()) {
        while (true) {
            val now = java.time.LocalTime.now()
            delay(DayRollover.nextCheckDelayMs(now.toNanoOfDay() / 1_000_000L))
            value = container.clock.today()
        }
    }
    val todayVm: TodayViewModel = viewModel(
        key = "today-$currentDate",
        factory = viewModelFactory {
            initializer {
                TodayViewModel(
                    container.history, container.water, container.settings, container.clock, container.appScope,
                    primaryMl = { WaterPortions.primaryMl(container.settings.settings.value.units.volume) },
                )
            }
        },
    )
    val today by todayVm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val splash = remember { SplashController(reducedMotion) }
    val ink = remember { InkFlowController(reducedMotion) }

    LaunchedEffect(splash) {
        if (!showSplash) {
            splash.skip()
            return@LaunchedEffect
        }
        // Шаг загрузки 1: выбор заставки (всегда полная, DECISIONS.md Р19).
        splash.start(forcedSplash ?: SplashPolicy.decide())
        splash.loadProgress = STEP_LAUNCH
        // Шаг 2: открыть базу и посчитать «Баланс дня». Дуга O приезжает в это значение.
        val state = todayVm.state.filterNotNull().first()
        splash.ratio = state.balance
        splash.loadProgress = 1f
    }

    // Шаги считаются сами (Р67): один раз после первого запуска просим «Физическую активность», если «Ходьба» включена.
    // Сразу после «да» запускается фоновый счёт. Уведомления спрашиваем после этого ответа, не одновременно.
    val context = androidx.compose.ui.platform.LocalContext.current
    val motion = container.motionSteps
    var activityAsked by remember { mutableStateOf(motion.permissionAsked || container.stepSensor.hasPermission()) }
    val activityLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok ->
        container.stepsLive.refreshPermission()
        if (ok) motion.ensureRunning()
        activityAsked = true
    }
    LaunchedEffect(settings.onboarded, settings.modules, splash.done) {
        if (activityAsked || !settings.onboarded || !splash.done) return@LaunchedEffect
        if (com.projectxo.app.core.model.Module.Steps !in settings.modules || !motion.canTrack) {
            activityAsked = true
            return@LaunchedEffect
        }
        motion.permissionAsked = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            activityLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            motion.ensureRunning()
            activityAsked = true
        }
    }

    // Один раз после первого запуска просим разрешение на уведомления, если напоминания включены (Android 13+).
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(settings.onboarded, settings.reminders, settings.notificationPermissionAsked, activityAsked) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && activityAsked && settings.onboarded && !settings.notificationPermissionAsked &&
            com.projectxo.app.core.model.ReminderPolicy.anyEnabled(settings.reminders, settings.modules) &&
            !com.projectxo.app.core.reminders.ReminderNotifier.canPost(context)
        ) {
            container.settings.update { it.copy(notificationPermissionAsked = true) }
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalSplash provides splash,
        LocalInkFlow provides ink,
    ) {
        Box(Modifier.fillMaxSize().background(XoTheme.colors.background)) {
            if (settings.onboarded) {
                XoNavHost(container = container, today = today, todayVm = todayVm, modules = settings.modules, order = settings.moduleOrder, date = currentDate)
            } else {
                // Первый запуск: онбординг под заставкой; после «Принять план» — «Сегодня» с нулевым днём (Р41).
                val onb: OnboardingViewModel = viewModel(
                    factory = viewModelFactory { initializer { OnboardingViewModel(container.settings, container.goals, container.clock) } },
                )
                OnboardingScreen(onb)
            }
            InkFlowLayer(ink)
            if (!splash.done && splash.mode != null) SplashOverlay(splash)
        }
    }
}

private const val STEP_LAUNCH = 0.4f
