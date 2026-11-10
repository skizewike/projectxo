package com.projectxo.app.feature.steps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.projectxo.app.AppContainer

/**
 * Связка экрана «Ходьба» с Android: разрешение «Физическая активность», настройки и счёт по движению.
 * Разрешение спрашивается только по кнопке; после отказа кнопка ведёт в настройки (5d).
 * Без датчика шагов, но с акселерометром — счёт по движению телефона (Р65).
 */
@Composable
fun StepsEntry(container: AppContainer, date: java.time.LocalDate) {
    val context = LocalContext.current
    val vm: StepsViewModel = viewModel(
        key = "steps-$date",
        factory = viewModelFactory {
            initializer { StepsViewModel(container.steps, container.goals, container.settings, container.clock, container.appScope) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val granted by container.stepsLive.permission.collectAsStateWithLifecycle()
    val motion = container.motionSteps
    val motionOn by motion.enabled.collectAsStateWithLifecycle()
    var refused by rememberSaveable { mutableStateOf(false) }
    // Разрешение спросили ради счёта по движению: после «да» сразу включаем.
    var wantMotion by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        container.stepsLive.refreshPermission()
        if (ok) {
            if (wantMotion) motion.enable()
        } else {
            val activity = context as? Activity
            // Система больше не покажет запрос: дальше только через настройки.
            refused = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)
        }
        wantMotion = false
    }

    val access = when {
        container.stepsLive.hasSensor -> when {
            granted -> StepsAccess.Granted
            refused -> StepsAccess.OpenSettings
            else -> StepsAccess.NeedPermission
        }
        !motion.available -> StepsAccess.NoSensor
        motionOn && granted -> StepsAccess.MotionOn
        else -> StepsAccess.MotionOff
    }

    fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    StepsScreen(
        state = state,
        access = access,
        onAllow = {
            when {
                access == StepsAccess.MotionOff && granted -> motion.enable()
                refused -> openSettings()
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> if (access == StepsAccess.MotionOff) motion.enable() else openSettings()
                else -> {
                    wantMotion = access == StepsAccess.MotionOff
                    launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        },
        onStopMotion = motion::disable,
        onAddManual = vm::addManual,
        onUndoManual = vm::undoManual,
    )
}
