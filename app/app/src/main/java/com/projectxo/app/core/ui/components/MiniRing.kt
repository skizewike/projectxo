package com.projectxo.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.projectxo.app.core.ui.theme.XoTheme

/**
 * Малое кольцо «Баланса дня» для календаря и полосы недели: дуга от верха по часовой.
 * При 100 % дуга акцентного цвета. [onInk] — на тёмной подложке выбранного дня.
 */
@Composable
fun MiniRing(progress: Float, diameter: Dp, stroke: Dp, modifier: Modifier = Modifier, onInk: Boolean = false) {
    val colors = XoTheme.colors
    val track = if (onInk) Color.Gray.copy(alpha = 0.3f) else colors.track
    val arc = when {
        progress >= 1f -> colors.accent
        onInk -> colors.background
        else -> colors.ink
    }
    Canvas(modifier.size(diameter)) {
        val w = stroke.toPx()
        val inset = w / 2f + 0.5f
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
        if (progress > 0f) {
            drawArc(arc, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}
