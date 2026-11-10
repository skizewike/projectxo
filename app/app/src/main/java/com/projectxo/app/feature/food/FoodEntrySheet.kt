package com.projectxo.app.feature.food

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.data.FoodEntry
import com.projectxo.app.core.data.photo.FoodPhotoStore
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.formatToday
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/** Снимок записи из хранилища. Читается и уменьшается в фоне; пока грузится или файла нет, виден фон track. */
@Composable
internal fun StoredPhoto(
    photos: FoodPhotoStore?,
    name: String?,
    maxSidePx: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = XoTheme.colors
    val bitmap by produceState<ImageBitmap?>(null, name, maxSidePx) {
        value = withContext(Dispatchers.IO) { decode(photos, name, maxSidePx) }
    }
    Box(modifier.background(colors.track)) {
        bitmap?.let {
            Image(
                it, contentDescription, Modifier.fillMaxSize().then(
                    if (contentDescription == null) Modifier.clearAndSetSemantics { } else Modifier,
                ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Снимок прямо из байтов (экран результата: запись ещё не сохранена). */
@Composable
internal fun JpegPhoto(bytes: ByteArray?, maxSidePx: Int, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap by produceState<ImageBitmap?>(null, bytes, maxSidePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (bytes == null) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(XoTheme.colors.track)) {
        bitmap?.let { Image(it, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private fun decode(photos: FoodPhotoStore?, name: String?, maxSidePx: Int): ImageBitmap? = runCatching {
    val file = photos?.file(name) ?: return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

private val SHEET_PHOTO_HEIGHT = 190.dp
private const val DISMISS_DRAG_FRACTION = 0.25f
private const val DRAG_BACK_MS = 200

/**
 * Лист записи с фото (Р71, design/food-entry-photo.html, вариант 01 «Лист»): снимок, название, калории,
 * БЖУ, оценка пользы и «Удалить запись». Закрывается касанием фона, жестом вниз и кнопкой «назад».
 */
@Composable
fun FoodEntrySheet(
    entry: FoodEntry?,
    photos: FoodPhotoStore?,
    onDismiss: () -> Unit,
    onDelete: (FoodEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    // Пока лист уезжает вниз, запись уже сброшена: показываем последнюю, чтобы он не пустел на ходу.
    var last by remember { mutableStateOf(entry) }
    if (entry != null) last = entry
    val shown = last
    BackHandler(enabled = entry != null, onBack = onDismiss)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            entry != null,
            enter = fadeIn(tween(if (reduced) 1 else 200)),
            exit = fadeOut(tween(if (reduced) 1 else 200)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onDismiss))
        }
        AnimatedVisibility(
            entry != null,
            enter = slideInVertically(tween(if (reduced) 1 else 360, easing = XoMotion.Standard)) { it },
            exit = slideOutVertically(tween(if (reduced) 1 else 280)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (shown != null) SheetBody(shown, photos, onDismiss, onDelete)
        }
    }
}

@Composable
private fun SheetBody(entry: FoodEntry, photos: FoodPhotoStore?, onDismiss: () -> Unit, onDelete: (FoodEntry) -> Unit) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    val pattern = stringResource(R.string.time_pattern)
    val time = remember(entry.timestampMs, pattern, locale) {
        DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(entry.timestampMs).atZone(ZoneId.systemDefault()))
    }
    val whenText = "${formatToday(entry.localDate)}, $time"
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var height by remember { mutableFloatStateOf(1f) }
    val photoA11y = stringResource(R.string.food_sheet_photo_a11y, entry.name)
    val closeLabel = stringResource(R.string.food_sheet_close)

    Column(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(0, offset.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Зона «ручки»: тянется вниз пальцем, касание тоже закрывает. Жест только здесь, чтобы не спорить с прокруткой.
        Box(
            Modifier
                .fillMaxWidth()
                .height(XoSize.touchMin)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offset.value > height * DISMISS_DRAG_FRACTION) onDismiss()
                            else scope.launch { offset.animateTo(0f, tween(DRAG_BACK_MS, easing = XoMotion.Standard)) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(DRAG_BACK_MS, easing = XoMotion.Standard)) } },
                    ) { _, dy -> scope.launch { offset.snapTo((offset.value + dy).coerceAtLeast(0f)) } }
                }
                .clickable(onClickLabel = closeLabel, role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.muted))
        }
        Column(
            Modifier
                .padding(horizontal = XoSpacing.screenGutter)
                .padding(bottom = XoSpacing.s4)
                .verticalScroll(rememberScrollState()),
        ) {
        StoredPhoto(
            photos, entry.photoFile, maxSidePx = 1200,
            modifier = Modifier.fillMaxWidth().height(SHEET_PHOTO_HEIGHT).clip(RoundedCornerShape(16.dp)),
            contentDescription = photoA11y,
        )
        Spacer(Modifier.height(XoSpacing.s4))
        Text(entry.name, style = type.title, color = colors.ink, maxLines = 2, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(XoSpacing.textGap))
        Text(whenText, style = type.caption, color = colors.muted)
        Spacer(Modifier.height(XoSpacing.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(XoSpacing.s1)) {
            Text(formatCount(entry.kcal.toLong()), style = type.display, color = colors.ink, modifier = Modifier.alignByBaseline())
            Text(stringResource(R.string.food_unit), style = type.displayUnit, color = colors.muted, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(XoSpacing.s3))
        Row(Modifier.fillMaxWidth().border(XoSize.hairline, colors.line)) {
            listOf(R.string.macro_protein to entry.proteinG, R.string.macro_fat to entry.fatG, R.string.macro_carbs to entry.carbsG)
                .forEachIndexed { i, (label, grams) ->
                    Column(Modifier.weight(1f).padding(XoSpacing.s3)) {
                        Text(stringResource(R.string.fp_grams, formatCount(grams.toLong())), style = type.bodyStrong, color = colors.ink)
                        Spacer(Modifier.height(XoSpacing.s1))
                        Text(stringResource(label), style = type.caption, color = colors.muted, maxLines = 1)
                    }
                    if (i < 2) Box(Modifier.width(XoSize.hairline).heightIn(min = 40.dp).background(colors.line))
                }
        }
        entry.healthScore?.let { score ->
            Spacer(Modifier.height(XoSpacing.s4))
            val a11y = stringResource(R.string.fp_score_a11y, score.toString(), "")
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = a11y }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.fp_score, score.toString()), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.fp_score_note), style = type.caption, color = colors.muted)
                }
                Spacer(Modifier.height(XoSpacing.s2))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..10) {
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= score) colors.ink else colors.track))
                    }
                }
            }
        }
        Spacer(Modifier.height(XoSpacing.s2))
        Box(
            Modifier.fillMaxWidth().heightIn(min = XoSize.touchMin)
                .clickable(role = Role.Button) { onDelete(entry); onDismiss() },
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.food_sheet_delete), style = type.caption, color = colors.muted) }
        }
    }
}
