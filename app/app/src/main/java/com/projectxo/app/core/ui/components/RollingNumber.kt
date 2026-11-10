package com.projectxo.app.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion

/**
 * Число как механический счётчик (docs/DESIGN.md, 5b п. 2): меняются только изменившиеся разряды,
 * справа налево с шагом 45 мс. При росте новая цифра въезжает снизу, при уменьшении сверху.
 * [text] — уже отформатированное число (с пробелами разрядов), [value] — для направления.
 */
@Composable
fun RollingNumber(value: Long, text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val last = remember { mutableLongStateOf(value) }
    val up = value >= last.longValue
    SideEffect { last.longValue = value }

    // Базовая линия задана явно: после перекатки цифр AnimatedContent её теряет, и подпись («ккал», «мл»)
    // уезжала бы наверх при выравнивании по базовой линии.
    val measurer = rememberTextMeasurer()
    val baseline = remember(style) { measurer.measure("0", style).firstBaseline.toInt() }

    Row(
        modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints)
            layout(p.width, p.height, mapOf(FirstBaseline to baseline)) { p.place(0, 0) }
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        val n = text.length
        text.forEachIndexed { index, ch ->
            val fromRight = n - 1 - index
            // Ячейка привязана к разряду справа, поэтому единицы остаются единицами при 999 → 1 000.
            key(fromRight) {
                AnimatedContent(
                    targetState = ch,
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        if (reduced) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val delay = fromRight * XoMotion.ROLL_STAGGER_MS
                            val slide = tween<IntOffset>(XoMotion.ROLL_MS, delayMillis = delay, easing = XoMotion.Roll)
                            val fade = tween<Float>(XoMotion.ROLL_FADE_MS, delayMillis = delay)
                            (slideInVertically(slide) { h -> if (up) h else -h } + fadeIn(fade)) togetherWith
                                (slideOutVertically(slide) { h -> if (up) -h else h } + fadeOut(fade)) using
                                SizeTransform(clip = true)
                        }
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), style = style, color = color)
                }
            }
        }
    }
}
