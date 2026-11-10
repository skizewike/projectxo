package com.projectxo.app.core.ui.splash

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.projectxo.app.core.motion.RingTarget
import com.projectxo.app.core.motion.SplashFrame
import com.projectxo.app.core.motion.SplashGeometry
import com.projectxo.app.core.motion.SplashMode
import com.projectxo.app.core.motion.SplashTimeline
import kotlin.math.min

/**
 * Общее состояние заставки. Его читают холст заставки и экран «Сегодня»:
 * кольцо сообщает сюда свои координаты, элементы берут отсюда прогресс появления.
 * Чтения идут в лямбдах рисования и graphicsLayer, поэтому каждый кадр перерисовывает, но не перестраивает экран.
 */
@Stable
class SplashController(val reducedMotion: Boolean) {
    var mode by mutableStateOf<SplashMode?>(null)
        private set
    var frame by mutableStateOf<SplashFrame?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    /** Координаты кольца «Баланс дня» в пикселях окна. */
    var target by mutableStateOf<RingTarget?>(null)

    /** Реальная доля загрузки 0..1. */
    var loadProgress by mutableFloatStateOf(0f)

    /** Фактическое значение «Баланса дня», куда приезжает дуга. */
    var ratio by mutableFloatStateOf(0f)

    fun start(mode: SplashMode) {
        this.mode = mode
    }

    /** Без заставки: пересоздание экрана, поворот. */
    fun skip() {
        done = true
    }

    /** Прогресс появления элемента «Сегодня» с номером [index] (0 — шапка, 5 — панель вкладок). */
    fun entrance(index: Int): Float {
        if (done) return 1f
        val f = frame ?: return 0f
        return f.home[index.coerceIn(0, f.home.lastIndex)] * f.stageAlpha
    }

    internal suspend fun run(geometry: SplashGeometry, loadShown: () -> Float) {
        val mode = mode ?: return
        val duration = SplashTimeline.duration(mode, reducedMotion)
        // Короткая заставка и режим «меньше анимаций» ждут готовности данных до старта.
        val waitsBeforeStart = mode == SplashMode.Short || reducedMotion
        var startNs = -1L
        var readyAt: Float? = null
        while (true) {
            val now = withFrameNanos { it }
            val ready = loadProgress >= 1f
            if (startNs < 0) {
                if (waitsBeforeStart && !ready) {
                    frame = SplashTimeline.frame(0f, mode, reducedMotion, geometry)
                    continue
                }
                startNs = now
            }
            val t = (now - startNs) / 1_000_000f
            if (ready && readyAt == null) readyAt = t
            val te = if (waitsBeforeStart) t else SplashTimeline.effectiveTime(t, readyAt)
            // Значение дня и место кольца могут измениться после старта (экран «Сегодня» дорисовался,
            // строки модулей подгрузились и сдвинули кольцо): берём актуальные каждый кадр, иначе O садится мимо кольца.
            val liveTarget = target ?: geometry.target
            val g = if (geometry.ratio == ratio && geometry.target == liveTarget) geometry else geometry.copy(ratio = ratio, target = liveTarget)
            frame = SplashTimeline.frame(min(te, duration), mode, reducedMotion, g, loadShown())
            if (te >= duration) {
                done = true
                return
            }
        }
    }
}

val LocalSplash = staticCompositionLocalOf { SplashController(reducedMotion = false).apply { skip() } }
