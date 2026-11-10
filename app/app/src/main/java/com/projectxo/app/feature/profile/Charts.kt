package com.projectxo.app.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.projectxo.app.core.ui.theme.XoTheme

enum class ChartKind { Line, Bars }

/**
 * График по дням (design/settings.html): линия со скользящим средним или столбики, пунктир цели.
 * Касание или проведение пальцем выбирает день: [selected] подсвечивается, подпись показывает родитель.
 */
@Composable
fun DayChart(
    values: List<Double?>,
    kind: ChartKind,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    startLabel: String,
    endLabel: String,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    trend: List<Double?>? = null,
    goal: Double? = null,
) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val n = values.size
    val currentSelect = rememberUpdatedState(onSelect)
    val hasData = values.any { it != null && (kind == ChartKind.Line || it > 0.0) }
    val labelStyle = remember(type) { type.scale }

    fun indexAt(x: Float, width: Float): Int = when {
        n <= 1 -> 0
        kind == ChartKind.Bars -> (x / width * n).toInt()
        else -> Math.round(x / width * (n - 1))
    }.coerceIn(0, (n - 1).coerceAtLeast(0))

    Canvas(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(n, hasData) {
                if (!hasData) return@pointerInput
                detectTapGestures(onPress = { p ->
                    currentSelect.value(indexAt(p.x, size.width.toFloat()))
                    tryAwaitRelease()
                    currentSelect.value(null)
                })
            }
            .pointerInput(n, hasData) {
                if (!hasData) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { p -> currentSelect.value(indexAt(p.x, size.width.toFloat())) },
                    onDragEnd = { currentSelect.value(null) },
                    onDragCancel = { currentSelect.value(null) },
                ) { change, _ -> currentSelect.value(indexAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        val w = size.width
        val pad = 6.dp.toPx()
        val labelH = 14.dp.toPx()
        val plotH = size.height - 2 * pad - labelH
        val hair = 1.dp.toPx()

        // Сетка: нижняя, средняя и верхняя линии.
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = pad + plotH * f
            drawLine(colors.line, Offset(0f, y), Offset(w, y), hair)
        }

        // Подписи под графиком.
        val startLayout = measurer.measure(startLabel, labelStyle)
        val endLayout = measurer.measure(endLabel, labelStyle)
        val labelY = size.height - startLayout.size.height
        drawText(startLayout, color = colors.muted, topLeft = Offset(0f, labelY))
        drawText(endLayout, color = colors.muted, topLeft = Offset(w - endLayout.size.width, labelY))

        if (!hasData) {
            val l = measurer.measure(emptyLabel, labelStyle.copy(fontSize = type.caption.fontSize))
            drawText(l, color = colors.muted, topLeft = Offset((w - l.size.width) / 2f, pad + (plotH - l.size.height) / 2f))
            return@Canvas
        }

        val present = values.filterNotNull() + listOfNotNull(goal) + (if (kind == ChartKind.Line) (trend ?: emptyList()).filterNotNull() else emptyList())
        var mn = present.min()
        var mx = present.max()
        if (kind == ChartKind.Bars) mn = 0.0
        val span = if (mx - mn == 0.0) 1.0 else mx - mn
        if (kind == ChartKind.Line) {
            mn -= span * 0.15
            mx += span * 0.15
        } else {
            mx += span * 0.08
        }
        fun yOf(v: Double): Float = (pad + plotH * (1 - (v - mn) / (mx - mn))).toFloat()
        fun xLine(i: Int): Float = if (n <= 1) w / 2f else i.toFloat() / (n - 1) * w

        if (goal != null) {
            val gy = yOf(goal)
            drawLine(colors.muted, Offset(0f, gy), Offset(w, gy), hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
        }

        when (kind) {
            ChartKind.Bars -> {
                val slot = w / n
                val gap = (if (n > 40) 1.dp else 3.dp).toPx().coerceAtMost(slot / 2f)
                val bw = (slot - gap).coerceAtLeast(2.dp.toPx())
                values.forEachIndexed { i, v ->
                    val value = v ?: 0.0
                    val top = yOf(value)
                    val bottom = yOf(0.0)
                    val isLast = i == n - 1
                    val isSel = i == selected
                    drawRect(
                        if (isLast || isSel) colors.accent else colors.ink,
                        Offset(i * slot, top), androidx.compose.ui.geometry.Size(bw, (bottom - top).coerceAtLeast(1f)),
                        alpha = if (isLast || isSel) 1f else 0.75f,
                    )
                }
            }
            ChartKind.Line -> {
                val strokeFaint = 1.5.dp.toPx()
                drawSeries(values, ::xLine, ::yOf, if (trend != null) colors.ink.copy(alpha = 0.35f) else colors.ink, strokeFaint)
                if (trend != null) drawSeries(trend, ::xLine, ::yOf, colors.accent, 2.5.dp.toPx())
                else values.indexOfLast { it != null }.takeIf { it >= 0 }?.let { i ->
                    drawCircle(colors.accent, 3.5.dp.toPx(), Offset(xLine(i), yOf(values[i]!!)))
                }
                // Отмеченные дни точками, если их немного.
                if (values.count { it != null } <= 40) values.forEachIndexed { i, v ->
                    if (v != null) drawCircle(colors.ink.copy(alpha = if (trend != null) 0.5f else 0f), 2.dp.toPx(), Offset(xLine(i), yOf(v)))
                }
            }
        }

        // Выбранный день.
        if (selected != null && selected in 0 until n) {
            val x = if (kind == ChartKind.Bars) selected * (w / n) + (w / n) / 2f else xLine(selected)
            drawLine(colors.ink, Offset(x, pad), Offset(x, pad + plotH), hair, alpha = 0.5f)
            if (kind == ChartKind.Line) values[selected]?.let { v -> drawCircle(colors.accent, 4.dp.toPx(), Offset(x, yOf(v))) }
        }
    }
}

private fun DrawScope.drawSeries(
    values: List<Double?>,
    x: (Int) -> Float,
    y: (Double) -> Float,
    color: androidx.compose.ui.graphics.Color,
    width: Float,
) {
    var prev: Offset? = null
    values.forEachIndexed { i, v ->
        if (v == null) return@forEachIndexed
        val p = Offset(x(i), y(v))
        drawCircle(color, width / 2f, p)
        prev?.let { drawLine(color, it, p, width, StrokeCap.Round) }
        prev = p
    }
}

private val CHART_HEIGHT = 120.dp
