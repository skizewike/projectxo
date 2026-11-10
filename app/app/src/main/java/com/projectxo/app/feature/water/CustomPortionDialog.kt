package com.projectxo.app.feature.water

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
import com.projectxo.app.core.model.VolumeMath
import com.projectxo.app.core.ui.format.LocalVolumeUnit
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.volumeUnitLabel
import com.projectxo.app.core.ui.theme.XoTheme

/** Своя порция воды: числовое поле 1–3 000 мл. */
@Composable
fun CustomPortionDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var text by rememberSaveable { mutableStateOf("") }
    val unit = LocalVolumeUnit.current
    val value = text.toIntOrNull()
    val ml = value?.let { VolumeMath.customToMl(it, unit) }
    val valid = ml != null
    val showError = text.isNotEmpty() && !valid
    val confirm = { if (ml != null) onConfirm(ml) }
    val range = VolumeMath.customRange(unit)
    val unitLabel = volumeUnitLabel(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.custom_portion_title), style = type.title, color = colors.ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.custom_portion_label, unitLabel)) },
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.custom_portion_error, formatCount(range.first.toLong()), "${formatCount(range.last.toLong())} $unitLabel")) }
                } else {
                    null
                },
                singleLine = true,
                textStyle = type.body,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = valid) {
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

private const val MAX_DIGITS = 4
