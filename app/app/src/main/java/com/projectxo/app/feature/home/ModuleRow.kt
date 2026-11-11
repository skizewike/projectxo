package com.projectxo.app.feature.home

import androidx.compose.foundation.clickable
import com.projectxo.app.core.ui.format.moduleGoalText
import com.projectxo.app.core.ui.format.moduleValueText
import com.projectxo.app.core.ui.icons.ModuleUi
import com.projectxo.app.core.ui.format.formatVolumeNumber
import com.projectxo.app.core.ui.format.formatVolume
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.projectxo.app.core.ui.motion.animatedValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import com.projectxo.app.core.ui.format.pluralText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.projectxo.app.R
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.model.ModuleProgress
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.splash.LocalSplash
import com.projectxo.app.core.ui.splash.entrance
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme

/** Строка модуля на «Сегодня»: метка-форма, название, значение «из цели», полоса прогресса. */
@Composable
fun ModuleRow(progress: ModuleProgress, entranceIndex: Int, isLast: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val splash = LocalSplash.current
    val hairline = with(LocalDensity.current) { XoSize.hairline.toPx() }

    val name = stringResource(ModuleUi.nameRes(progress.module))
    val value = moduleValueText(progress)
    val ofGoal = moduleGoalText(progress)
    val a11y = stringResource(R.string.module_row_a11y, name, value, ofGoal)
    val shownFraction by animatedValue(progress.fraction, snap = !splash.done)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = XoSize.rowHeight)
            .entrance(entranceIndex)
            .drawBehind {
                drawRect(colors.line, size = Size(size.width, hairline))
                if (isLast) drawRect(colors.line, Offset(0f, size.height - hairline), Size(size.width, hairline))
            }
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = a11y }
            .padding(top = XoSpacing.s4, bottom = XoSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Иконка «Предметы» вместо фигуры (Р40).
            androidx.compose.material3.Icon(
                ModuleUi.icon(progress.module),
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(XoSpacing.s3))
            Text(name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
                Text(value, style = type.bodyStrong, color = colors.ink, modifier = Modifier.alignByBaseline())
                Text(ofGoal, style = type.caption, color = colors.muted, modifier = Modifier.alignByBaseline())
            }
        }
        Spacer(Modifier.height(XoSpacing.s3))
        // Полоса прогресса: заполнение растёт вместе с появлением строки.
        Box(
            Modifier
                .fillMaxWidth()
                .height(XoSize.bar)
                .drawBehind {
                    drawRect(colors.track)
                    val fill = shownFraction * splash.entrance(entranceIndex)
                    if (fill > 0f) drawRect(colors.ink, size = Size(size.width * fill, size.height))
                },
        )
    }
}
