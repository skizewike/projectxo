package com.projectxo.app.feature.food

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.projectxo.app.R
import com.projectxo.app.core.data.FoodDraft
import com.projectxo.app.core.model.FoodRules
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme

/**
 * Ввод своей записи: название (по желанию), калории и БЖУ.
 * Если калории не введены, а граммы есть, берётся оценка по ним (4 · Б + 9 · Ж + 4 · У).
 */
@Composable
fun FoodEntryDialog(onConfirm: (FoodDraft) -> Unit, onDismiss: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    var name by rememberSaveable { mutableStateOf("") }
    var kcalText by rememberSaveable { mutableStateOf("") }
    var proteinText by rememberSaveable { mutableStateOf("") }
    var fatText by rememberSaveable { mutableStateOf("") }
    var carbsText by rememberSaveable { mutableStateOf("") }

    val protein = proteinText.toIntOrNull() ?: 0
    val fat = fatText.toIntOrNull() ?: 0
    val carbs = carbsText.toIntOrNull() ?: 0
    val estimate = FoodRules.estimateKcal(protein, fat, carbs)
    val kcal = if (kcalText.isEmpty()) estimate else kcalText.toIntOrNull() ?: 0

    val macroOk = protein <= FoodRules.MAX_MACRO_G && fat <= FoodRules.MAX_MACRO_G && carbs <= FoodRules.MAX_MACRO_G
    val unnamed = stringResource(R.string.food_unnamed)
    val draft = FoodDraft(name.trim().ifEmpty { unnamed }, kcal, protein, fat, carbs)
    val valid = macroOk && FoodRules.isValid(draft)
    val kcalError = kcalText.isNotEmpty() && kcal !in 1..FoodRules.MAX_KCAL
    val confirm = { if (valid) onConfirm(draft) }

    fun digits(new: String) = new.filter(Char::isDigit).take(MAX_DIGITS)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(stringResource(R.string.food_dialog_title), style = type.title, color = colors.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(FoodRules.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.food_name_label)) },
                    singleLine = true,
                    textStyle = type.body,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(XoSpacing.s2))
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = digits(it) },
                    label = { Text(stringResource(R.string.food_kcal_label)) },
                    isError = kcalError,
                    supportingText = when {
                        kcalError -> {{ Text(stringResource(R.string.food_kcal_error)) }}
                        kcalText.isEmpty() && estimate > 0 -> {{ Text(stringResource(R.string.food_kcal_estimate, formatCount(estimate.toLong()))) }}
                        else -> null
                    },
                    singleLine = true,
                    textStyle = type.body,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(XoSpacing.s2))
                MacroField(proteinText, { proteinText = digits(it) }, R.string.food_protein_label, ImeAction.Next, confirm)
                Spacer(Modifier.height(XoSpacing.s2))
                MacroField(fatText, { fatText = digits(it) }, R.string.food_fat_label, ImeAction.Next, confirm)
                Spacer(Modifier.height(XoSpacing.s2))
                MacroField(carbsText, { carbsText = digits(it) }, R.string.food_carbs_label, ImeAction.Done, confirm)
            }
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

@Composable
private fun MacroField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    ime: ImeAction,
    onDone: () -> Unit,
) {
    val type = XoTheme.type
    val tooBig = (value.toIntOrNull() ?: 0) > FoodRules.MAX_MACRO_G
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = tooBig,
        supportingText = if (tooBig) {{ Text(stringResource(R.string.food_macro_error)) }} else null,
        singleLine = true,
        textStyle = type.body,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

private const val MAX_DIGITS = 4
