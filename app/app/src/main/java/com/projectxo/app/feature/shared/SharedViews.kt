package com.projectxo.app.feature.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.projectxo.app.R
import com.projectxo.app.core.model.SharedMath
import com.projectxo.app.core.ui.components.RollingNumber
import com.projectxo.app.core.ui.format.formatCount
import com.projectxo.app.core.ui.format.rememberFormatLocale
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoSpacing
import com.projectxo.app.core.ui.theme.XoTheme
import java.time.format.TextStyle
import kotlin.math.PI

/** Анимация одной отметки: кривая приложения, при «меньше анимаций» без движения. */
@Composable
private fun reveal(delayMs: Int = 0, durationMs: Int = ARC_MS): AnimationSpec<Float> {
    val reduced = LocalReducedMotion.current
    return if (reduced) snap() else tween(durationMs, delayMs, XoMotion.EmphasizedDecelerate)
}

/** Крестик в два штриха (Р37): первый штрих, потом второй. [color] у отметки друга приглушён. */
@Composable
internal fun XMarkC(on: Boolean, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val p by animateFloatAsState(if (on) 1f else 0f, tween(if (reduced) 0 else X_MS, easing = XoMotion.EmphasizedDecelerate), label = "sharedX")
    Canvas(modifier.size(size)) {
        val w = 1.8.dp.toPx()
        val pad = this.size.minDimension * 0.28f
        val a = Offset(pad, pad)
        val b = Offset(this.size.width - pad, this.size.height - pad)
        val c = Offset(this.size.width - pad, pad)
        val d = Offset(pad, this.size.height - pad)
        fun lerp(from: Offset, to: Offset, t: Float) = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        val t1 = (p * 2f).coerceIn(0f, 1f)
        val t2 = (p * 2f - 1f).coerceIn(0f, 1f)
        if (t1 > 0f) drawLine(color, a, lerp(a, b, t1), w, StrokeCap.Round)
        if (t2 > 0f) drawLine(color, c, lerp(c, d, t2), w, StrokeCap.Round)
    }
}

/**
 * «Пересечение» (Р79): два кольца недели как два круга. Отметки рисуются дугами по семи делениям,
 * общая часть кругов темнеет с каждым днём вместе, число «вместе» перекатывается, а когда отметили оба, по кругам идёт волна.
 */
@Composable
fun IntersectionView(week: SharedWeek, myName: String, friendName: String, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val together = week.together
    val progMe = List(SharedMath.WEEK) { i -> animateFloatAsState(if (shown && week.mine[i]) 1f else 0f, reveal(i * ARC_STEP_MS), label = "arcMe$i").value }
    val progFr = List(SharedMath.WEEK) { i -> animateFloatAsState(if (shown && week.friend[i]) 1f else 0f, reveal(i * ARC_STEP_MS + ARC_STEP_MS / 2), label = "arcFr$i").value }
    val lens by animateFloatAsState(LENS_MIN + LENS_RANGE * together / SharedMath.WEEK, if (reduced) snap() else tween(LENS_MS, easing = XoMotion.EmphasizedDecelerate), label = "lens")
    val wave = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(together) }
    LaunchedEffect(together) {
        if (together > last && !reduced) {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(WAVE_MS, easing = XoMotion.EmphasizedDecelerate))
        }
        last = together
    }
    val a11y = stringResource(R.string.shared_a11y_week, myName, week.mineCount.toString(), friendName, week.friendCount.toString(), together.toString())
    val ink = colors.ink
    val muted = colors.muted
    val track = colors.track
    Box(modifier.fillMaxWidth().aspectRatio(W / H).clearAndSetSemantics { contentDescription = a11y }) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / W
            val r = RADIUS * u
            val cm = Offset(CM_X * u, CY * u)
            val cf = Offset(CF_X * u, CY * u)
            val pm = Path().apply { addOval(Rect(cm, r)) }
            val pf = Path().apply { addOval(Rect(cf, r)) }
            drawPath(Path.combine(PathOperation.Intersect, pm, pf), ink.copy(alpha = lens))
            val stroke = Stroke(RING_W * u, cap = StrokeCap.Round)
            val seg = 360f / SharedMath.WEEK
            val gapDeg = (RING_GAP / (2.0 * PI * RADIUS) * 360.0).toFloat()
            fun ring(center: Offset, prog: List<Float>, color: Color) {
                val topLeft = Offset(center.x - r, center.y - r)
                val box = Size(r * 2, r * 2)
                for (i in 0 until SharedMath.WEEK) {
                    val start = -90f + i * seg + gapDeg / 2f
                    val sweep = seg - gapDeg
                    drawArc(track, start, sweep, false, topLeft, box, style = stroke)
                    if (prog[i] > 0.001f) drawArc(color, start, sweep * prog[i], false, topLeft, box, style = stroke)
                }
            }
            ring(cm, progMe, ink)
            ring(cf, progFr, muted)
            if (wave.value < 1f) {
                drawCircle(ink.copy(alpha = (1f - wave.value) * WAVE_ALPHA), (12f + 78f * wave.value) * u, Offset((W / 2f) * u, CY * u), style = Stroke(1.5f * u))
            }
        }
        Column(Modifier.align(BiasAlignment(bias(86f), 0f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(myName, style = type.scale, color = colors.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(week.mineCount.toLong(), week.mineCount.toString(), type.subtitle, colors.ink)
                Text("/7", style = type.caption, color = colors.muted)
            }
        }
        Column(Modifier.align(BiasAlignment(bias(226f), 0f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(friendName, style = type.scale, color = colors.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(week.friendCount.toLong(), week.friendCount.toString(), type.subtitle, colors.ink)
                Text("/7", style = type.caption, color = colors.muted)
            }
        }
        Column(Modifier.align(BiasAlignment(0f, 0f)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.shared_together), style = type.scale, color = colors.muted, maxLines = 1)
            RollingNumber(together.toLong(), together.toString(), type.title, colors.ink)
        }
    }
}

private fun bias(x: Float) = x / W * 2f - 1f

/**
 * «Нить» (Р79): последние семь дней, у каждого дня ваш и друга квадрат и нить между ними. Один отметил — нить тянется
 * навстречу точками, оба — натягивается целиком. Дни идут сверху вниз от сегодняшнего.
 */
@Composable
fun ThreadView(week: SharedWeek, myName: String, friendName: String, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val locale = rememberFormatLocale()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = XoSpacing.s1), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(DAY_COL))
            Text(myName, style = type.scale, color = colors.muted, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(CELL))
            Spacer(Modifier.weight(1f))
            Text(friendName, style = type.scale, color = colors.muted, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.width(CELL))
        }
        val n = week.days.size
        for (k in 0 until n) {
            val idx = n - 1 - k
            val day = week.days[idx]
            val me = week.mine[idx]
            val fr = week.friend[idx]
            val both = me && fr
            val label = when (k) {
                0 -> stringResource(R.string.shared_today)
                1 -> stringResource(R.string.shared_yesterday)
                else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " + day.dayOfMonth
            }
            val a11y = stringResource(R.string.shared_a11y_day, label, myName, stateWord(me), friendName, stateWord(fr))
            val appear by animateFloatAsState(if (shown) 1f else 0f, reveal(k * ROW_STEP_MS, ROW_MS), label = "row$k")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_H)
                    .alpha(appear)
                    .padding(top = ((1f - appear) * 10f).dp)
                    .clearAndSetSemantics { contentDescription = a11y },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = type.caption, color = if (k == 0) colors.ink else colors.muted, modifier = Modifier.width(DAY_COL), maxLines = 1)
                Cell(me, colors.ink, both)
                ThreadLine(me, fr, Modifier.weight(1f).fillMaxSize())
                Cell(fr, colors.muted, both)
            }
        }
    }
}

@Composable
private fun stateWord(done: Boolean) = stringResource(if (done) R.string.shared_a11y_done else R.string.shared_a11y_not_yet)

@Composable
private fun Cell(on: Boolean, markColor: Color, both: Boolean) {
    val colors = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val line by animateFloatAsState(if (both) 1f else 0f, if (reduced) snap() else tween(THREAD_MS, easing = XoMotion.EmphasizedDecelerate), label = "cellLine")
    val borderColor = lerpColor(colors.line, colors.ink, line)
    Box(
        Modifier.size(CELL).border(XoSize.hairline, borderColor, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) { XMarkC(on, CELL, markColor) }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)

/** Нить между квадратами: целая, если отметили оба, пунктир навстречу, если один. */
@Composable
private fun ThreadLine(me: Boolean, friend: Boolean, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(THREAD_MS, easing = XoMotion.EmphasizedDecelerate)
    val full by animateFloatAsState(if (me && friend) 1f else 0f, spec, label = "full")
    val left by animateFloatAsState(if (me && !friend) 1f else 0f, spec, label = "left")
    val right by animateFloatAsState(if (friend && !me) 1f else 0f, spec, label = "right")
    val ink = colors.ink
    val muted = colors.muted
    Canvas(modifier) {
        val y = size.height / 2f
        val pad = 6.dp.toPx()
        val w = size.width
        if (full > 0f) drawLine(ink, Offset(0f, y), Offset(w * full, y), 2.dp.toPx(), StrokeCap.Round)
        val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))
        if (left > 0f) drawLine(muted.copy(alpha = 0.7f), Offset(pad, y), Offset(pad + (w * HALF_REACH - pad) * left, y), 2.dp.toPx(), StrokeCap.Round, pathEffect = dots)
        if (right > 0f) drawLine(muted.copy(alpha = 0.7f), Offset(w - pad, y), Offset(w - pad - (w * HALF_REACH - pad) * right, y), 2.dp.toPx(), StrokeCap.Round, pathEffect = dots)
    }
}

// Холст «Пересечения» в условных единицах (как в макете design/shared-habits-motion.html).
private const val W = 312f
private const val H = 196f
private const val RADIUS = 70f
private const val CM_X = 114f
private const val CF_X = 198f
private const val CY = 98f
private const val RING_W = 7f
private const val RING_GAP = 11f
private const val LENS_MIN = 0.05f
private const val LENS_RANGE = 0.26f
private const val WAVE_ALPHA = 0.55f
private const val WAVE_MS = 900
private const val LENS_MS = 700
private const val ARC_MS = 520
private const val ARC_STEP_MS = 50
private const val X_MS = 320
private const val ROW_MS = 420
private const val ROW_STEP_MS = 50
private const val THREAD_MS = 600
private const val HALF_REACH = 0.36f
private val ROW_H = 44.dp
private val CELL = 32.dp
private val DAY_COL = 96.dp
