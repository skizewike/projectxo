package com.projectxo.app.core.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.projectxo.app.core.motion.InkDot
import com.projectxo.app.core.motion.InkFlowMath
import com.projectxo.app.core.motion.InkFlowParticles
import com.projectxo.app.core.motion.InkFlowSpec
import com.projectxo.app.core.ui.theme.XoTheme

/** Центр элемента на экране: источник потока (кнопка). Обновляется без перестройки экрана. */
class InkAnchor {
    var center: Offset = Offset.Unspecified
        internal set
}

fun Modifier.inkAnchor(anchor: InkAnchor): Modifier =
    this.onGloballyPositioned { anchor.center = it.boundsInRoot().center }

/** Куда летят точки. Ключи целей. */
object InkTargets {
    const val TODAY_RING = "today-ring"
    const val WATER_LEVEL = "water-level"
}

/**
 * Чернильный поток (docs/DESIGN.md, 5b п. 1). Один слой на всё приложение.
 * Цели регистрируют «голову» своего прогресса; кнопки запускают поток к ней.
 */
@Stable
class InkFlowController(private val reducedMotion: Boolean) {
    internal class Flow(val particles: InkFlowParticles, val from: Offset, val to: Offset) {
        var startNs = -1L
    }

    internal val flows = mutableStateListOf<Flow>()
    internal var nowNs by mutableLongStateOf(0L)
    private val targets = HashMap<String, () -> Offset>()
    private var seed = 1

    fun registerTarget(key: String, head: () -> Offset) {
        targets[key] = head
    }

    fun unregisterTarget(key: String, head: () -> Offset) {
        if (targets[key] === head) targets.remove(key)
    }

    /**
     * Запустить поток от [from] к цели [targetKey].
     * Возвращает false, если поток не показан (меньше анимаций, нет цели): тогда значение меняют сразу.
     */
    fun launch(from: Offset, targetKey: String): Boolean {
        if (reducedMotion || !from.isSpecified) return false
        val to = targets[targetKey]?.invoke()?.takeIf { it.isSpecified } ?: return false
        if (flows.size >= MAX_FLOWS) flows.removeAt(0)
        flows.add(Flow(InkFlowParticles(seed++), from, to))
        return true
    }

    private companion object {
        const val MAX_FLOWS = 4
    }
}

val LocalInkFlow = staticCompositionLocalOf { InkFlowController(reducedMotion = true) }

/** Цель потока: регистрируется, пока элемент на экране. */
@Composable
fun InkTarget(key: String, head: () -> Offset) {
    val ink = LocalInkFlow.current
    val current by rememberUpdatedState(head)
    val provider = remember { { current() } }
    DisposableEffect(ink, key) {
        ink.registerTarget(key, provider)
        onDispose { ink.unregisterTarget(key, provider) }
    }
}

@Composable
fun InkFlowLayer(controller: InkFlowController, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val dot = remember { InkDot() }

    LaunchedEffect(controller) {
        snapshotFlow { controller.flows.size }.collect { size ->
            if (size == 0) return@collect
            while (controller.flows.isNotEmpty()) {
                withFrameNanos { now ->
                    controller.flows.forEach { if (it.startNs < 0) it.startNs = now }
                    controller.nowNs = now
                    controller.flows.removeAll { (now - it.startNs) / 1_000_000f > InkFlowSpec.TOTAL_MS }
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val now = controller.nowNs
        val dp = density
        controller.flows.forEach { flow ->
            if (flow.startNs < 0) return@forEach
            val t = (now - flow.startNs) / 1_000_000f
            for (i in 0 until InkFlowSpec.COUNT) {
                if (InkFlowMath.dot(flow.particles, i, t, flow.from.x, flow.from.y, flow.to.x, flow.to.y, dp, dot)) {
                    drawCircle(colors.ink, radius = dot.radiusDp * dp, center = Offset(dot.x, dot.y), alpha = dot.alpha)
                }
            }
        }
    }
}
