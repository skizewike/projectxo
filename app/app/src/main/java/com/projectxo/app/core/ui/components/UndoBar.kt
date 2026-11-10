package com.projectxo.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoRadius
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme

/** Полоса отмены внизу экрана (docs/DESIGN.md, 5a). Тёмная плашка цвета ink, текст цвета фона. */
@Composable
fun UndoBar(
    visible: Boolean,
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val enterFade = tween<Float>(XoMotion.STATE_MS, easing = XoMotion.EmphasizedDecelerate)
    val exitFade = tween<Float>(XoMotion.MICRO_MS, easing = XoMotion.EmphasizedAccelerate)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) fadeIn(enterFade) else {
            fadeIn(enterFade) + slideInVertically(tween(XoMotion.STATE_MS, easing = XoMotion.EmphasizedDecelerate)) { it }
        },
        exit = if (reduced) fadeOut(exitFade) else {
            fadeOut(exitFade) + slideOutVertically(tween(XoMotion.MICRO_MS, easing = XoMotion.EmphasizedAccelerate)) { it / 2 }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = XoSpacing.s4, vertical = XoSpacing.s3)
                .background(colors.ink, RoundedCornerShape(XoRadius.m))
                .heightIn(min = XoSize.touchMin + XoSpacing.s2)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message, style = type.body, color = colors.background,
                modifier = Modifier.weight(1f).padding(start = XoSpacing.s4),
            )
            Box(
                Modifier
                    .heightIn(min = XoSize.touchMin)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = XoSpacing.s4),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = type.bodyStrong, color = colors.background)
            }
        }
    }
}
