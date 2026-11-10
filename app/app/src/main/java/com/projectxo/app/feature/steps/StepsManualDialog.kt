package com.projectxo.app.feature.steps

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.projectxo.app.R
import com.projectxo.app.core.steps.StepsMath
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.theme.XoTheme

/** Шаги вручную: числовое поле 1–50 000, запись в текущий час. Тот же вид, что у своей порции воды. */
@Composable
fun StepsManualDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()?.takeIf { it in 1..StepsMath.MAX_MANUAL }
    val showError = text.isNotEmpty() && value == null
    val confirm = { if (value != null) onConfirm(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.steps_manual_title), style = type.title, color = colors.ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.steps_manual_label)) },
                isError = showError,
                supportingText = {
                    Text(
                        if (showError) {
                            stringResource(R.string.custom_portion_error, formatCount(1), formatCount(StepsMath.MAX_MANUAL.toLong()))
                        } else {
                            stringResource(R.string.steps_manual_hint)
                        },
                    )
                },
                singleLine = true,
                textStyle = type.body,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = value != null) {
                Text(stringResource(R.string.custom_portion_add), style = type.bodyStrong)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = type.body)
            }
        },
    )
}

private const val MAX_DIGITS = 5
