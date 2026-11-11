package com.projectxo.app.feature.shared

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.SharedError
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.model.SharedView
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoSegmented
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.format.pluralText
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay

class SharedActions(
    val onBack: () -> Unit,
    val onToggle: () -> Unit,
    val onView: (SharedView) -> Unit,
    val onRefresh: () -> Unit,
    val onNewCode: () -> Unit,
    val onLeave: () -> Unit,
)

/** Понятный текст ошибки общей привычки. */
@Composable
internal fun sharedErrorText(e: SharedError): String = stringResource(
    when (e) {
        SharedError.NotConfigured -> R.string.shared_err_not_configured
        SharedError.Network -> R.string.shared_err_network
        SharedError.BadCode -> R.string.shared_err_bad_code
        SharedError.RoomFull -> R.string.shared_err_room_full
        SharedError.TooMany -> R.string.shared_err_too_many
        SharedError.Gone -> R.string.shared_err_gone
        SharedError.BadInput -> R.string.shared_err_bad_input
        SharedError.Server -> R.string.shared_err_server
    },
)

/** Отправить код друзьям через системное меню «Поделиться». */
internal fun shareCode(context: android.content.Context, title: String, code: String) {
    val text = context.getString(R.string.shared_share_text, title, SharedMath.prettyCode(code))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Общая привычка (Р79): вы и друг. По умолчанию вид «Пересечение», второй вид — «Нить». Без давления:
 * пустая отметка друга нейтральная, красного и сравнения «кто лучше» нет.
 */
@Composable
fun SharedHabitScreen(state: SharedUiState, actions: SharedActions, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val context = LocalContext.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    // Пока экран открыт, раз в минуту подтягиваем отметки друга.
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MS)
            actions.onRefresh()
        }
    }
    LaunchedEffect(state.gone) { if (state.gone && state.loaded) actions.onBack() }

    val room = state.room
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        Row(
            Modifier.padding(top = XoSpacing.s2).heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = actions.onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(XoIcons.Back, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.module_habits), style = type.caption, color = colors.muted)
        }
        if (room == null) return@Column

        val myName = room.myNick
        val friendName = state.friendNick ?: stringResource(R.string.shared_friend_default)
        Text(room.title, style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.headGap))
        Text(
            if (state.friendNick != null) stringResource(R.string.shared_with, friendName) else stringResource(R.string.shared_waiting),
            style = type.caption, color = colors.muted,
        )

        // Друг ещё не вошёл: код приглашения.
        if (state.friendNick == null) {
            Spacer(Modifier.height(XoSpacing.s4))
            InviteCard(room.title, state, actions) { shareCode(context, room.title, it) }
        }

        Spacer(Modifier.height(XoSpacing.s4))
        XoSegmented(
            listOf(SharedView.Intersection to stringResource(R.string.shared_view_intersection), SharedView.Thread to stringResource(R.string.shared_view_thread)),
            state.view, actions.onView,
        )
        Spacer(Modifier.height(XoSpacing.s4))
        state.week?.let { week ->
            when (state.view) {
                SharedView.Intersection -> IntersectionView(week, myName, friendName)
                SharedView.Thread -> ThreadView(week, myName, friendName)
            }
        }

        Spacer(Modifier.height(XoSpacing.s4))
        Text(stringResource(R.string.shared_streak_title), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.textGap))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(state.streak.toLong(), state.streak.toString(), type.display, colors.ink)
            Text(
                pluralText(R.plurals.shared_days_word, state.streak) + " · " + stringResource(R.string.shared_best, state.best.toString()),
                style = type.caption, color = colors.muted, modifier = Modifier.padding(start = XoSpacing.s1, bottom = XoSpacing.s2),
            )
        }

        Spacer(Modifier.height(XoSpacing.s4))
        XoHairline()
        PersonRow(myName, true, state.doneToday)
        XoHairline()
        PersonRow(friendName, false, state.friendDoneToday, enabledName = state.friendNick != null)
        XoHairline()

        Spacer(Modifier.height(XoSpacing.s2))
        val status = when {
            state.syncing -> stringResource(R.string.shared_syncing)
            state.syncError == SharedError.Network -> stringResource(R.string.shared_offline)
            state.syncError != null -> sharedErrorText(state.syncError)
            else -> ""
        }
        if (status.isNotEmpty()) Text(status, style = type.caption, color = colors.muted)

        Spacer(Modifier.height(XoSpacing.s4))
        XoPrimaryButton(stringResource(if (state.doneToday) R.string.shared_done_today else R.string.shared_mark_today), actions.onToggle)
        XoTextButton(stringResource(R.string.shared_refresh), actions.onRefresh)
        XoTextButton(stringResource(R.string.shared_leave), { confirmLeave = true })
        Spacer(Modifier.height(XoSpacing.s7))
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.shared_leave_title), style = type.title, color = colors.ink) },
            text = { Text(stringResource(R.string.shared_leave_body), style = type.body, color = colors.muted) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; actions.onLeave() }) { Text(stringResource(R.string.shared_leave_confirm), style = type.bodyStrong) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel), style = type.bodyStrong) } },
        )
    }
}

@Composable
private fun InviteCard(title: String, state: SharedUiState, actions: SharedActions, onShare: (String) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Column(Modifier.fillMaxWidth().border(XoSize.hairline, colors.line, RoundedCornerShape(16.dp)).padding(XoSpacing.s4)) {
        Text(stringResource(R.string.shared_invite_title), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s2))
        val code = state.code
        if (code != null) {
            Text(SharedMath.prettyCode(code), style = type.display, color = colors.ink)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.shared_invite_note), style = type.caption, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s3))
            XoPrimaryButton(stringResource(R.string.shared_share), { onShare(code) })
        } else {
            Text(stringResource(R.string.shared_invite_expired), style = type.body, color = colors.muted)
            Spacer(Modifier.height(XoSpacing.s3))
            XoPrimaryButton(stringResource(R.string.shared_new_code), actions.onNewCode)
        }
        state.codeError?.let {
            Spacer(Modifier.height(XoSpacing.s2))
            Text(sharedErrorText(it), style = type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun PersonRow(name: String, isMe: Boolean, doneToday: Boolean, enabledName: Boolean = true) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    Row(Modifier.fillMaxWidth().heightIn(min = XoSize.touchMin).padding(vertical = XoSpacing.s1), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).border(XoSize.hairline, if (isMe) colors.ink else colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(name.take(1).uppercase(), style = type.scale, color = colors.ink) }
        Spacer(Modifier.width(XoSpacing.s3))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) stringResource(R.string.shared_you) else name, style = type.bodyStrong, color = colors.ink)
            Text(
                stringResource(if (!enabledName) R.string.shared_not_joined else if (doneToday) R.string.shared_today_done else R.string.shared_today_not_yet),
                style = type.caption, color = colors.muted,
            )
        }
    }
}

private const val REFRESH_MS = 60_000L
