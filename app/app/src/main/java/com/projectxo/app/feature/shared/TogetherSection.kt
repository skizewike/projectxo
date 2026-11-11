package com.projectxo.app.feature.shared

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.Habit
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme

class TogetherActions(
    val onOpen: (String) -> Unit,
    val onCreate: (Habit, String) -> Unit,
    val onJoin: (String, String) -> Unit,
    val onShowInvite: (SharedBrief) -> Unit,
    val onDismissInvite: () -> Unit,
    val onClearError: () -> Unit,
    val onOpened: () -> Unit,
)

/** Раздел «Вместе» внизу экрана привычек (Р79): общие привычки, «Позвать друга», «Ввести код». */
@Composable
fun TogetherSection(state: TogetherState, actions: TogetherActions, modifier: Modifier = Modifier) {
    if (!state.available) return
    val colors = XoTheme.colors
    val type = XoTheme.type
    var creating by rememberSaveable { mutableStateOf(false) }
    var joining by rememberSaveable { mutableStateOf(false) }

    // Вошли по коду: открываем общую привычку и закрываем окно ввода.
    LaunchedEffect(state.openRoom) {
        state.openRoom?.let {
            joining = false
            actions.onOpened()
            actions.onOpen(it)
        }
    }
    // Комната создана: окно создания закрывается, показывается код.
    LaunchedEffect(state.invite) { if (state.invite != null) creating = false }

    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.shared_section), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.s2))
        state.rooms.forEach { room ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = XoSize.rowHeight).clickable(role = Role.Button) { actions.onOpen(room.roomId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(room.title, style = type.bodyStrong, color = colors.ink)
                    Text(
                        if (room.friendNick != null) stringResource(R.string.shared_with, room.friendNick) else stringResource(R.string.shared_waiting),
                        style = type.caption, color = colors.muted,
                    )
                }
                Icon(XoIcons.Chevron, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            }
        }
        if (state.rooms.isEmpty()) {
            Text(stringResource(R.string.shared_section_empty), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s2))
        }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { actions.onClearError(); creating = true }, enabled = state.candidates.isNotEmpty()) {
                Text(stringResource(R.string.shared_invite_action), style = type.bodyStrong)
            }
            TextButton(onClick = { actions.onClearError(); joining = true }) { Text(stringResource(R.string.shared_join_action), style = type.bodyStrong) }
        }
    }

    if (creating) CreateDialog(state, actions) { creating = false }
    if (joining) JoinDialog(state, actions) { joining = false }
    state.invite?.let { InviteDialog(it, actions) }
}

@Composable
private fun CreateDialog(state: TogetherState, actions: TogetherActions, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var picked by rememberSaveable { mutableStateOf(state.candidates.firstOrNull()?.id ?: -1L) }
    var nick by rememberSaveable { mutableStateOf(state.nick) }
    val habit = state.candidates.firstOrNull { it.id == picked }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.shared_create_title), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.shared_create_lead), style = type.caption, color = colors.muted)
                Spacer(Modifier.height(XoSpacing.s3))
                state.candidates.forEach { h ->
                    val on = h.id == picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = XoSize.touchMin).clickable(role = Role.RadioButton) { picked = h.id }.semantics { selected = on },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.name, style = if (on) type.bodyStrong else type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        if (on) Icon(XoIcons.Check, null, tint = colors.ink, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(XoSpacing.s3))
                NickField(nick) { nick = it }
                state.error?.let {
                    Spacer(Modifier.height(XoSpacing.s2))
                    Text(sharedErrorText(it), style = type.caption, color = colors.muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { habit?.let { actions.onCreate(it, nick) } }, enabled = habit != null && SharedMath.cleanNick(nick).isNotEmpty() && !state.busy) {
                Text(stringResource(if (state.busy) R.string.shared_wait else R.string.shared_create_confirm), style = type.bodyStrong)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = type.bodyStrong) } },
    )
}

@Composable
private fun JoinDialog(state: TogetherState, actions: TogetherActions, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var code by rememberSaveable { mutableStateOf("") }
    var nick by rememberSaveable { mutableStateOf(state.nick) }
    val ok = SharedMath.normalizeCode(code).length == SharedMath.CODE_LENGTH && SharedMath.cleanNick(nick).isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.shared_join_title), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.shared_join_lead), style = type.caption, color = colors.muted)
                Spacer(Modifier.height(XoSpacing.s3))
                OutlinedTextField(
                    value = code, onValueChange = { code = it.take(CODE_INPUT_MAX).uppercase() },
                    label = { Text(stringResource(R.string.shared_code_label)) }, singleLine = true, textStyle = type.bodyStrong,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(XoSpacing.s3))
                NickField(nick) { nick = it }
                state.error?.let {
                    Spacer(Modifier.height(XoSpacing.s2))
                    Text(sharedErrorText(it), style = type.caption, color = colors.muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onJoin(code, nick) }, enabled = ok && !state.busy) {
                Text(stringResource(if (state.busy) R.string.shared_wait else R.string.shared_join_confirm), style = type.bodyStrong)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = type.bodyStrong) } },
    )
}

@Composable
private fun NickField(value: String, onChange: (String) -> Unit) {
    val type = XoTheme.type
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(SharedMath.MAX_NICK)) },
        label = { Text(stringResource(R.string.shared_nick_label)) },
        supportingText = { Text(stringResource(R.string.shared_nick_hint)) },
        singleLine = true, textStyle = type.body,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

@Composable
private fun InviteDialog(invite: InviteInfo, actions: TogetherActions) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = actions.onDismissInvite,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.shared_invite_title), style = type.title, color = colors.ink) },
        text = {
            Column {
                Text(invite.title, style = type.bodyStrong, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.s3))
                Text(
                    SharedMath.prettyCode(invite.code), style = type.display, color = colors.ink,
                    modifier = Modifier.fillMaxWidth().border(XoSize.hairline, colors.line, RoundedCornerShape(16.dp)).padding(XoSpacing.s4),
                )
                Spacer(Modifier.height(XoSpacing.s3))
                Text(stringResource(R.string.shared_invite_note), style = type.caption, color = colors.muted)
            }
        },
        confirmButton = { TextButton(onClick = { shareCode(context, invite.title, invite.code) }) { Text(stringResource(R.string.shared_share), style = type.bodyStrong) } },
        dismissButton = {
            TextButton(onClick = { actions.onDismissInvite(); actions.onOpen(invite.roomId) }) { Text(stringResource(R.string.shared_open), style = type.bodyStrong) }
        },
    )
}

private const val CODE_INPUT_MAX = 8
