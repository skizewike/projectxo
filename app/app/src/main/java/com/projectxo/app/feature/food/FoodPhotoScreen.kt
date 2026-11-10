package com.projectxo.app.feature.food

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.projectxo.app.BuildConfig
import com.projectxo.app.R
import com.projectxo.app.core.data.foodai.PhotoError
import com.projectxo.app.core.data.foodai.PhotoPrep
import com.projectxo.app.core.model.FoodPhotoMath
import com.projectxo.app.core.model.OilChoice
import com.projectxo.app.core.model.PortionChoice
import com.projectxo.app.core.ui.components.XoHairline
import com.projectxo.app.core.ui.components.XoPrimaryButton
import com.projectxo.app.core.ui.components.XoTextButton
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.icons.XoIcons
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.delay
import java.io.File

/** Всё, что экран «Еда по фото» просит у модели. */
class FoodPhotoActions(
    val onHint: (String) -> Unit,
    val onSaveKey: (String) -> Unit,
    val onAnalyze: (language: String, jpeg: suspend () -> ByteArray?) -> Unit,
    val onRetake: () -> Unit,
    val onOil: (OilChoice) -> Unit,
    val onPortion: (PortionChoice) -> Unit,
    val onCustomGrams: (String) -> Unit,
    val onEditing: (Boolean) -> Unit,
    val onStep: (index: Int, sign: Int) -> Unit,
    val onSave: (unnamed: String) -> Boolean,
    val setFatName: (String) -> Unit,
    val onReset: () -> Unit = {},
    val onRetry: (language: String) -> Unit = {},
)

private const val STUDIO_URL = "https://aistudio.google.com/apikey"

/**
 * «Еда по фото», вариант 03 «Уточнение» (Р69): фото, два вопроса (масло, вес порции) и результат с диапазоном
 * калорий, БЖУ и оценкой пользы 1–10. Сам модуль «Еда» не меняется: сюда ведёт кнопка «По фото…».
 */
@Composable
fun FoodPhotoScreen(state: FoodPhotoState, actions: FoodPhotoActions, onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val language = stringResource(R.string.fp_ai_language)
    val fatName = stringResource(R.string.fp_oil_name)
    val unnamed = stringResource(R.string.food_unnamed)
    actions.setFatName(fatName)

    var pending by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun analyze(uri: Uri) {
        actions.onAnalyze(language) {
            PhotoPrep.jpegFrom(context, uri).also { runCatching { File(context.cacheDir, "photos/$PHOTO_NAME").delete() } }
        }
    }

    // Ушли с экрана (не поворот): забываем результат и снимок.
    val activity = remember(context) { context.findActivity() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (activity?.isChangingConfigurations != true) actions.onReset() }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending
        if (ok && uri != null) analyze(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) analyze(uri) }

    fun launchCamera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", File(dir, PHOTO_NAME))
        pending = uri
        runCatching { camera.launch(uri) }.onFailure { Toast.makeText(context, R.string.fp_no_camera, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = XoSpacing.screenGutter),
    ) {
        val backAction = if (state.step == PhotoStep.Result || state.step == PhotoStep.Failed) actions.onRetake else onBack
        BackRow(stringResource(if (state.step == PhotoStep.Pick || state.step == PhotoStep.Working) R.string.back else R.string.fp_retake), backAction)
        when (state.step) {
            PhotoStep.Pick -> PickStep(
                state, actions,
                onCamera = ::launchCamera,
                onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            PhotoStep.Working -> WorkingStep(state.photo)
            PhotoStep.Result -> ResultStep(state, actions, onSave = {
                if (actions.onSave(unnamed)) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSaved()
                }
            })
            PhotoStep.Failed -> FailedStep(state, actions, onRetry = { actions.onRetry(language) })
        }
        Spacer(Modifier.height(XoSpacing.s7))
    }
}

private const val PHOTO_NAME = "food.jpg"

/** Activity из контекста, даже если он обёрнут (язык приложения, темы). */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
private val RESULT_PHOTO_HEIGHT = 190.dp

// ——— выбор фото ———

@Composable
private fun PickStep(state: FoodPhotoState, actions: FoodPhotoActions, onCamera: () -> Unit, onGallery: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    ScreenTitle(R.string.fp_title)
    Spacer(Modifier.height(XoSpacing.headGap))
    Text(stringResource(R.string.fp_lead), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.s5))
    if (!state.hasKey) {
        FoodAiKeyPanel(state.keyError, onSave = actions.onSaveKey, onRemove = null)
        return
    }
    OutlinedTextField(
        value = state.hint,
        onValueChange = actions.onHint,
        label = { Text(stringResource(R.string.fp_hint_label)) },
        supportingText = { Text(stringResource(R.string.fp_hint_support)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = type.body,
        minLines = 1,
        maxLines = 3,
    )
    Spacer(Modifier.height(XoSpacing.s5))
    XoPrimaryButton(stringResource(R.string.fp_camera), onCamera)
    Spacer(Modifier.height(XoSpacing.s2))
    OutlineButton(stringResource(R.string.fp_gallery), onGallery)
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.fp_privacy), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.textGap))
    Text(stringResource(R.string.fp_disclaimer), style = type.caption, color = colors.muted)
}

/**
 * Ключ Google AI Studio: как получить, поле вставки, сохранение. Общий для экрана фото и настроек
 * (там [onRemove] не null и показывается кнопка «Удалить ключ»).
 */
@Composable
fun FoodAiKeyPanel(keyError: Boolean, onSave: (String) -> Unit, onRemove: (() -> Unit)?) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.fp_key_body), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.s4))
    OutlineButton(stringResource(R.string.fp_key_open)) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, STUDIO_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, R.string.fp_key_open_failed, Toast.LENGTH_LONG).show() }
    }
    Spacer(Modifier.height(XoSpacing.s4))
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(stringResource(R.string.fp_key_label)) },
        isError = keyError,
        supportingText = if (keyError) {{ Text(stringResource(R.string.fp_key_bad)) }} else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = type.body,
    )
    Spacer(Modifier.height(XoSpacing.s4))
    XoPrimaryButton(stringResource(R.string.fp_key_save), { onSave(key) }, enabled = key.isNotBlank())
    if (onRemove != null) {
        Spacer(Modifier.height(XoSpacing.s2))
        XoTextButton(stringResource(R.string.fp_key_remove), onRemove)
    }
}

// ——— ожидание ———

/**
 * «Считаем»: вариант 03 «Скан» (design/food-photo-loading.html, Р72). Снимок остаётся на экране, по нему ходит
 * тонкая линия, на тарелке по очереди появляются точки. Подписи сменяются каждые [MSG_MS] мс.
 * При «меньше анимаций» стоит один кадр: линия посередине, точки видны, первая подпись.
 */
@Composable
private fun WorkingStep(photo: ByteArray?) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    val infinite = rememberInfiniteTransition(label = "fpScan")
    val sweep by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCAN_HALF_MS, easing = XoMotion.Scan), RepeatMode.Reverse), label = "fpScanLine",
    )
    val cycle by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCAN_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "fpScanPoints",
    )
    val line = if (reduced) 0.5f else sweep
    val t = if (reduced) 0.5f else cycle
    val messages = listOf(R.string.fp_working_m1, R.string.fp_working_m2, R.string.fp_working_m3).map { stringResource(it) }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduced) {
        if (!reduced) while (true) {
            delay(MSG_MS)
            index = (index + 1) % messages.size
        }
    }
    val veil = colors.background.copy(alpha = VEIL_ALPHA)

    Column(Modifier.fillMaxWidth().padding(top = XoSpacing.s3), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.track)
                .clearAndSetSemantics { },
        ) {
            JpegPhoto(photo, maxSidePx = 1000, modifier = Modifier.matchParentSize().alpha(PHOTO_DIM))
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val y = line * h
                drawRect(veil, Offset.Zero, Size(w, y))
                drawLine(colors.ink, Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
                // Уголки видоискателя.
                val arm = 22.dp.toPx()
                val gap = 12.dp.toPx()
                val sw = 1.5.dp.toPx()
                for ((cx, dx) in listOf(gap to 1f, w - gap to -1f)) for ((cy, dy) in listOf(gap to 1f, h - gap to -1f)) {
                    drawLine(colors.ink, Offset(cx, cy), Offset(cx + dx * arm, cy), sw, StrokeCap.Round)
                    drawLine(colors.ink, Offset(cx, cy), Offset(cx, cy + dy * arm), sw, StrokeCap.Round)
                }
                // Найденные составляющие: точки появляются по очереди и гаснут к концу круга.
                SCAN_POINTS.forEach { (fx, fy, start) ->
                    val p = pointProgress(t, start)
                    if (p > 0f) {
                        val c = Offset(w * fx, h * fy)
                        val r = POINT_RADIUS.toPx() * (POINT_MIN_SCALE + (1f - POINT_MIN_SCALE) * p)
                        drawCircle(colors.background.copy(alpha = p), r, c)
                        drawCircle(colors.ink.copy(alpha = p), r, c, style = Stroke(sw))
                    }
                }
            }
        }
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.fp_working), style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.headGap))
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(MSG_FADE_IN_MS)) togetherWith fadeOut(tween(MSG_FADE_OUT_MS)) },
            label = "fpScanMessage",
        ) { i ->
            Text(
                messages[i], style = type.caption, color = colors.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Прогресс точки 0..1: появляется за 8 % круга после [start], держится до 88 %, потом гаснет. */
private fun pointProgress(t: Float, start: Float): Float = when {
    t < start -> 0f
    t < start + POINT_IN -> (t - start) / POINT_IN
    t < POINT_OUT_FROM -> 1f
    else -> (1f - (t - POINT_OUT_FROM) / (1f - POINT_OUT_FROM)).coerceAtLeast(0f)
}

private data class ScanPoint(val x: Float, val y: Float, val start: Float)
private val SCAN_POINTS = listOf(ScanPoint(0.36f, 0.36f, 0.14f), ScanPoint(0.64f, 0.46f, 0.26f), ScanPoint(0.46f, 0.68f, 0.38f))
private val POINT_RADIUS = 6.dp
private const val POINT_MIN_SCALE = 0.3f
private const val POINT_IN = 0.08f
private const val POINT_OUT_FROM = 0.88f
private const val SCAN_HALF_MS = 1400
private const val SCAN_CYCLE_MS = 2800
private const val MSG_MS = 2400L
private const val MSG_FADE_IN_MS = 300
private const val MSG_FADE_OUT_MS = 200
private const val VEIL_ALPHA = 0.35f
private const val PHOTO_DIM = 0.55f

// ——— результат ———

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultStep(state: FoodPhotoState, actions: FoodPhotoActions, onSave: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val dish = state.dish ?: return
    Text(stringResource(R.string.fp_title), style = type.caption, color = colors.muted)
    Spacer(Modifier.height(XoSpacing.headGap))
    Text(
        dish.name.ifEmpty { stringResource(R.string.food_unnamed) }, style = type.title, color = colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    if (state.photo != null) {
        Spacer(Modifier.height(XoSpacing.s4))
        JpegPhoto(
            state.photo, maxSidePx = 1200,
            modifier = Modifier.fillMaxWidth().height(RESULT_PHOTO_HEIGHT).clip(RoundedCornerShape(16.dp)),
            contentDescription = stringResource(R.string.food_sheet_photo_a11y, dish.name.ifEmpty { stringResource(R.string.food_unnamed) }),
        )
    }
    if (dish.clarify != null) {
        // Вопрос от ИИ показываем как есть: он уже сформулирован как вопрос на языке пользователя.
        Spacer(Modifier.height(XoSpacing.s3))
        Text(dish.clarify, style = type.bodyStrong, color = colors.ink)
    }

    Spacer(Modifier.height(XoSpacing.s5))
    Question(R.string.fp_q_oil)
    ChoiceRow {
        OilChoice.entries.forEach { c ->
            val label = when (c) {
                OilChoice.None -> R.string.fp_oil_none
                OilChoice.Some -> R.string.fp_oil_some
                OilChoice.Lots -> R.string.fp_oil_lots
                OilChoice.AsPhoto -> R.string.fp_oil_asphoto
            }
            ChoiceChip(stringResource(label), state.oil == c) { actions.onOil(c) }
        }
    }

    Spacer(Modifier.height(XoSpacing.s4))
    Question(R.string.fp_q_weight)
    ChoiceRow {
        PortionChoice.entries.forEach { c ->
            val label = when (c) {
                PortionChoice.Small -> R.string.fp_w_small
                PortionChoice.Mid -> R.string.fp_w_mid
                PortionChoice.Large -> R.string.fp_w_big
                PortionChoice.Custom -> R.string.fp_w_custom
                PortionChoice.AsPhoto -> R.string.fp_w_asphoto
            }
            ChoiceChip(stringResource(label), state.portion == c) { actions.onPortion(c) }
        }
    }
    if (state.portion == PortionChoice.Custom) {
        Spacer(Modifier.height(XoSpacing.s2))
        OutlinedTextField(
            value = state.customGrams,
            onValueChange = actions.onCustomGrams,
            label = { Text(stringResource(R.string.fp_custom_label)) },
            supportingText = {
                Text(stringResource(R.string.fp_custom_support, formatCount(FoodPhotoMath.MIN_CUSTOM_G.toLong()), formatCount(FoodPhotoMath.MAX_CUSTOM_G.toLong())))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = type.body,
        )
    }

    Spacer(Modifier.height(XoSpacing.s5))
    XoHairline()
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.fp_result), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(XoSpacing.s2))
    val t = state.totals
    val (lo, hi) = state.range
    val a11y = stringResource(
        R.string.fp_result_a11y, formatCount(t.kcal.toLong()), formatCount(lo.toLong()), formatCount(hi.toLong()),
        formatCount(t.proteinG.toLong()), formatCount(t.fatG.toLong()), formatCount(t.carbsG.toLong()),
    )
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
        Row(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
            Text(formatCount(t.kcal.toLong()), style = type.display, color = colors.ink, modifier = Modifier.alignByBaseline())
            Text(stringResource(R.string.food_unit), style = type.displayUnit, color = colors.muted, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(stringResource(R.string.fp_range, formatCount(lo.toLong()), formatCount(hi.toLong())), style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s4))
        MacroLine(t.proteinG, t.fatG, t.carbsG)
    }

    Spacer(Modifier.height(XoSpacing.s5))
    ScoreBlock(dish.healthScore, dish.healthReasons)

    if (state.editing) {
        Spacer(Modifier.height(XoSpacing.s5))
        Text(stringResource(R.string.fp_edit), style = type.caption, color = colors.muted, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.s2))
        state.items.forEachIndexed { i, item -> ItemRow(item.name, item.grams, (item.kcal100 * item.grams / 100.0).toInt(), { actions.onStep(i, -1) }, { actions.onStep(i, 1) }) }
        XoHairline()
    }

    Spacer(Modifier.height(XoSpacing.s5))
    XoPrimaryButton(stringResource(R.string.fp_save), onSave, enabled = state.canSave)
    Spacer(Modifier.height(XoSpacing.s2))
    OutlineButton(stringResource(if (state.editing) R.string.fp_edit_done else R.string.fp_edit)) { actions.onEditing(!state.editing) }
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(R.string.fp_disclaimer), style = type.caption, color = colors.muted)
}

@Composable
private fun MacroLine(proteinG: Int, fatG: Int, carbsG: Int) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val hairline = XoSize.hairline
    Row(Modifier.fillMaxWidth().border(hairline, colors.line)) {
        listOf(R.string.macro_protein to proteinG, R.string.macro_fat to fatG, R.string.macro_carbs to carbsG).forEachIndexed { i, (label, grams) ->
            Column(Modifier.weight(1f).padding(horizontal = XoSpacing.s3, vertical = XoSpacing.s3)) {
                Text(stringResource(R.string.fp_grams, formatCount(grams.toLong())), style = type.bodyStrong, color = colors.ink)
                Spacer(Modifier.height(XoSpacing.s1))
                Text(stringResource(label), style = type.caption, color = colors.muted, maxLines = 1)
            }
            if (i < 2) Box(Modifier.width(hairline).heightIn(min = 40.dp).background(colors.line))
        }
    }
}

@Composable
private fun ScoreBlock(score: Int, reasons: List<String>) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val title = stringResource(R.string.fp_score, score.toString())
    val a11y = stringResource(R.string.fp_score_a11y, score.toString(), reasons.joinToString(". "))
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.fp_score_note), style = type.caption, color = colors.muted)
        }
        Spacer(Modifier.height(XoSpacing.s2))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 1..10) {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= score) colors.ink else colors.track))
            }
        }
        reasons.forEach {
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(it, style = type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun ItemRow(name: String, grams: Int, kcal: Int, onLess: () -> Unit, onMore: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    XoHairline()
    Row(
        Modifier.fillMaxWidth().heightIn(min = XoSize.entryRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = type.body, color = colors.ink, maxLines = 2)
            Spacer(Modifier.height(XoSpacing.textGap))
            Text(stringResource(R.string.fp_item_kcal, formatCount(kcal.toLong())), style = type.caption, color = colors.muted)
        }
        RoundStep("−", stringResource(R.string.fp_less_a11y, name), onLess)
        Text(
            stringResource(R.string.fp_grams, formatCount(grams.toLong())), style = type.body, color = colors.ink,
            modifier = Modifier.width(60.dp).clearAndSetSemantics { contentDescription = "" },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundStep("+", stringResource(R.string.fp_more_a11y, name), onMore)
    }
}

@Composable
private fun RoundStep(symbol: String, label: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Box(Modifier.size(XoSize.touchMin), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(XoSize.hairline, colors.line, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Text(symbol, style = XoTheme.type.title, color = colors.ink) }
    }
}

// ——— ошибка ———

@Composable
private fun FailedStep(state: FoodPhotoState, actions: FoodPhotoActions, onRetry: () -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val message = when {
        state.notFood -> R.string.fp_not_food
        state.error == PhotoError.Network -> R.string.fp_err_net
        state.error == PhotoError.Quota -> R.string.fp_err_quota
        state.error == PhotoError.BadKey || state.error == PhotoError.NoKey -> R.string.fp_err_key
        state.error == PhotoError.Busy -> R.string.fp_err_busy
        state.error == PhotoError.BadPhoto -> R.string.fp_err_photo
        else -> R.string.fp_err_bad
    }
    ScreenTitle(R.string.fp_title)
    Spacer(Modifier.height(XoSpacing.s4))
    Text(stringResource(message), style = type.body, color = colors.ink)
    Spacer(Modifier.height(XoSpacing.s5))
    if (!state.hasKey) {
        FoodAiKeyPanel(state.keyError, onSave = actions.onSaveKey, onRemove = null)
        return
    }
    if (!state.notFood) {
        XoPrimaryButton(stringResource(R.string.fp_retry), onRetry)
        Spacer(Modifier.height(XoSpacing.s2))
    }
    OutlineButton(stringResource(R.string.fp_retake), actions.onRetake)
}

// ——— общие части ———

@Composable
private fun ScreenTitle(title: Int) {
    Text(stringResource(title), style = XoTheme.type.title, color = XoTheme.colors.ink, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Question(text: Int) {
    Text(stringResource(text), style = XoTheme.type.bodyStrong, color = XoTheme.colors.ink)
    Spacer(Modifier.height(XoSpacing.s2))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(XoSize.chipGap),
    ) { content() }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Box(
        Modifier
            .heightIn(min = XoSize.touchMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = XoSize.chipHeight)
                .clip(RoundedCornerShape(XoSize.chipRadius))
                .background(if (selected) colors.ink else colors.background)
                .border(XoSize.hairline, if (selected) colors.ink else colors.line, RoundedCornerShape(XoSize.chipRadius))
                .padding(horizontal = XoSpacing.s3),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = XoTheme.type.caption, color = if (selected) colors.background else colors.ink, maxLines = 1) }
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(XoSize.buttonHeight)
            .clip(CircleShape)
            .border(XoSize.hairline, colors.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = XoTheme.type.bodyStrong, color = colors.ink) }
}

@Composable
private fun BackRow(label: String, onClick: () -> Unit) {
    val colors = XoTheme.colors
    Row(
        Modifier.heightIn(min = XoSize.touchMin).clickable(role = Role.Button, onClick = onClick).padding(top = XoSpacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XoIcons.Back, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(XoSpacing.s1))
        Text(label, style = XoTheme.type.caption, color = colors.muted)
    }
}
