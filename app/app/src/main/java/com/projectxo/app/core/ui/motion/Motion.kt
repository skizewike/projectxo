package com.projectxo.app.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.projectxo.app.core.ui.theme.XoMotion

/** «Меньше анимаций»: системная настройка или своя в приложении. При true движение заменяется мгновенной сменой. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Значение, которое доезжает до [target] по springSettle (docs/DESIGN.md, 5a).
 * Первое значение показывается сразу, без анимации. [snap] = true — сразу к цели (например, пока идёт заставка).
 */
@Composable
fun animatedValue(target: Float, snap: Boolean = false): State<Float> {
    val reduced = LocalReducedMotion.current
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, reduced, snap) {
        if (reduced || snap) anim.snapTo(target) else anim.animateTo(target, XoMotion.springSettle())
    }
    return anim.asState()
}

/** Лёгкое сжатие при нажатии: 1 → 0,96 → 1 на springTouch. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    val reduced = LocalReducedMotion.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) XoMotion.PRESS_SCALE else 1f,
        animationSpec = XoMotion.springTouch(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
