package com.projectxo.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoRadius
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Общие элементы управления на токенах XO. Без Material-компонентов: свои формы и состояния.

/** Главная кнопка: заливка ink, текст цветом фона. */
@Composable
fun XoPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = XoTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(XoSize.buttonHeight)
            .clip(CircleShape)
            .background(c.ink)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = XoTheme.type.bodyStrong, color = c.background) }
}

/** Второстепенная кнопка-текст. */
@Composable
fun XoTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = XoSize.touchMin).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = XoTheme.type.caption, color = XoTheme.colors.muted) }
}

/** Переключатель: подложка track, включён — акцент. Пружинка бегунка, при «меньше анимаций» — сразу. */
@Composable
fun XoSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, if (reduced) tween(0) else XoMotion.springTouch(), label = "switch")
    val bg by animateColorAsState(if (checked) c.accent else c.track, tween(if (reduced) 0 else XoMotion.MICRO_MS), label = "switchBg")
    Box(modifier.size(46.dp, 28.dp).clip(CircleShape).background(bg).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(22.dp).clip(CircleShape).background(if (checked) c.onAccent else c.background))
    }
}

/** Строка с заголовком, подписью и переключателем. Вся строка нажимается. */
@Composable
fun XoSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = XoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = XoSize.rowHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = XoTheme.type.bodyStrong, color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(subtitle, style = XoTheme.type.caption, color = c.muted)
            }
        }
        Spacer(Modifier.width(XoSpacing.s3))
        XoSwitch(checked)
    }
}

/** Сегменты: выбранный — ink с текстом цвета фона. */
@Composable
fun <T> XoSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = XoTheme.colors
    Row(
        modifier.fillMaxWidth().border(XoSize.hairline, c.line, CircleShape).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.ink else c.background)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = XoTheme.type.caption, color = if (on) c.background else c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

/** Вариант выбора с кружком: рамка ink у выбранного. */
@Composable
fun XoOption(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
    tag: String? = null,
) {
    val c = XoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = XoSpacing.s2)
            .clip(RoundedCornerShape(XoRadius.m))
            .border(if (selected) 2.dp else XoSize.hairline, if (selected) c.ink else c.line, RoundedCornerShape(XoRadius.m))
            .alpha(if (enabled) 1f else 0.4f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = XoSpacing.s4, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, style = XoTheme.type.title, color = c.ink, modifier = Modifier.width(52.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = XoTheme.type.bodyStrong, color = c.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(XoSpacing.textGap))
                Text(subtitle, style = XoTheme.type.caption, color = c.muted)
            }
        }
        if (tag != null) {
            Text(tag, style = XoTheme.type.label, color = c.muted, modifier = Modifier.border(XoSize.hairline, c.line, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
            Spacer(Modifier.width(XoSpacing.s2))
        }
        Box(Modifier.size(20.dp).border(1.4.dp, if (selected) c.ink else c.line, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.ink))
        }
    }
}

/**
 * Поле «− значение +». Удержание кнопки листает быстрее (через 400 мс — каждые 60 мс).
 * [valueText] уже отформатирован, [a11y] — для TalkBack.
 */
@Composable
fun XoStepper(
    valueText: String,
    unit: String,
    a11y: String,
    minusLabel: String,
    plusLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = XoTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepeatButton("−", minusLabel, onMinus)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(valueText, style = XoTheme.type.displayM, color = c.ink)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = XoTheme.type.displayUnitM, color = c.muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        RepeatButton("+", plusLabel, onPlus)
    }
}

@Composable
private fun RepeatButton(symbol: String, label: String, onStep: () -> Unit) {
    val c = XoTheme.colors
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(XoSize.hairline, c.line, CircleShape)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                coroutineScope {
                    val outer = this
                    awaitEachGesture {
                        awaitFirstDown()
                        step()
                        val repeat = outer.launch {
                            delay(400)
                            while (true) { step(); delay(60) }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = XoTheme.type.title, color = c.ink) }
}

/** Тонкая разделительная линия. */
@Composable
fun XoHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(XoSize.hairline).background(XoTheme.colors.line))
}

