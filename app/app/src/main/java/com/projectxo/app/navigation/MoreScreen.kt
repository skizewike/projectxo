package com.projectxo.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.Module
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.icons.ModuleUi
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme

/** «Ещё» (Р38): модули, которые не поместились во вкладки, и настройки. */
@Composable
fun MoreScreen(modules: List<Module>, onOpen: (Module) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.more_title), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.s4))
        if (modules.isEmpty()) {
            Text(stringResource(R.string.more_none), style = type.body, color = colors.muted)
        } else {
            Text(stringResource(R.string.more_modules), style = type.caption, color = colors.muted)
            modules.forEach { m -> MoreRow(ModuleUi.icon(m), stringResource(ModuleUi.nameRes(m)), stringResource(ModuleUi.subRes(m))) { onOpen(m) } }
            XoHairline()
        }
        Spacer(Modifier.height(XoSpacing.s5))
        MoreRow(XoIcons.Settings, stringResource(R.string.settings_title), null, onSettings)
        XoHairline()
    }
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    XoHairline()
    Row(
        Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight - 8.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.bodyStrong, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(XoSpacing.s1 / 2))
                Text(subtitle, style = type.caption, color = colors.muted)
            }
        }
        Icon(XoIcons.Chevron, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
}
