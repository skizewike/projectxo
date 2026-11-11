package com.projectxo.app.core.ui.figure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.projectxo.app.core.model.FigureDeco
import com.projectxo.app.core.model.FigureMath
import com.projectxo.app.core.model.FigurePose
import com.projectxo.app.core.model.FigureSpec
import com.projectxo.app.core.model.Pt
import com.projectxo.app.core.ui.motion.LocalReducedMotion
import com.projectxo.app.core.ui.theme.XoTheme

/** Как показывать фигуру: движением, начальной позой или обеими позами сразу. */
enum class FigureMode { Animated, Start, Both }

/**
 * Фигура упражнения, стиль «Линия» (Р75): тонкая линия, дальние конечности светлее, опоры цветом линий.
 * При «меньше анимаций» вместо движения показываются обе позы: начало сплошной линией, конец пунктиром.
 * [description] читает TalkBack; пустое значение прячет фигуру от TalkBack (когда рядом есть название).
 */
@Composable
fun ExerciseFigure(
    spec: FigureSpec,
    modifier: Modifier = Modifier,
    mode: FigureMode = FigureMode.Animated,
    description: String = "",
) {
    val colors = XoTheme.colors
    val reduced = LocalReducedMotion.current
    val shown = if (mode == FigureMode.Animated && reduced) FigureMode.Both else mode
    val timeMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(shown, spec) {
        if (shown != FigureMode.Animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            timeMs.longValue = (now - start) / 1_000_000L
        }
    }
    val sem = if (description.isEmpty()) Modifier.clearAndSetSemantics { } else Modifier.semantics { contentDescription = description }
    Canvas(modifier.aspectRatio(FigureMath.W / FigureMath.H).then(sem)) {
        val unit = size.width / FigureMath.W
        val sc = spec.scale
        withTransform({
            scale(unit, unit, Offset.Zero)
            if (sc != 1f) {
                translate(FigureMath.W / 2f * (1f - sc), FigureMath.FLOOR * (1f - sc))
                scale(sc, sc, Offset.Zero)
            }
        }) {
            drawDeco(spec.deco, colors.line, sc)
            when (shown) {
                FigureMode.Start -> drawPose(spec.a, colors.ink, colors.ink.copy(alpha = FAR_ALPHA), sc, dashed = false)
                FigureMode.Both -> {
                    spec.b?.let { drawPose(it, colors.muted.copy(alpha = GHOST_ALPHA), colors.line, sc, dashed = true) }
                    drawPose(spec.a, colors.ink, colors.ink.copy(alpha = FAR_ALPHA), sc, dashed = false)
                }
                FigureMode.Animated -> {
                    val k = FigureMath.phase(spec, timeMs.longValue / 1000f)
                    drawPose(FigureMath.pose(spec, k), colors.ink, colors.ink.copy(alpha = FAR_ALPHA), sc, dashed = false)
                }
            }
        }
    }
}

private const val FAR_ALPHA = 0.42f
private const val GHOST_ALPHA = 0.55f
private const val LINE_W = 2.4f

private fun path(points: List<Pt>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
}

private fun DrawScope.drawPose(p: FigurePose, near: Color, far: Color, scale: Float, dashed: Boolean) {
    val w = LINE_W / scale
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(2f, 3f)) else null
    val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
    p.leg2?.let { drawPath(path(listOf(p.p) + it), far, style = st) }
    p.arm2?.let { drawPath(path(listOf(p.s) + it), far, style = st) }
    drawPath(path(listOf(p.p) + p.leg), near, style = st)
    drawPath(path(listOf(p.s, p.p)), near, style = st)
    drawPath(path(listOf(p.s) + p.arm), near, style = st)
    drawCircle(near, FigureMath.HEAD_R, Offset(p.head.x, p.head.y), style = st)
}

private fun DrawScope.drawDeco(deco: FigureDeco, color: Color, scale: Float) {
    val thin = 1f
    val st = Stroke(width = 3f / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(8f, FigureMath.FLOOR + 0.5f), Offset(152f, FigureMath.FLOOR + 0.5f), thin / scale, StrokeCap.Round)
    fun poly(vararg v: Float) = drawPath(Path().apply {
        moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { lineTo(v[i], v[i + 1]); i += 2 }
    }, color, style = st)
    when (deco) {
        FigureDeco.None -> Unit
        FigureDeco.Wall -> poly(127f, 10f, 127f, 88f)
        FigureDeco.WallLeft -> poly(50f, 10f, 50f, 88f)
        FigureDeco.Bench -> { poly(24f, 64f, 60f, 64f); poly(28f, 64f, 28f, 88f); poly(56f, 64f, 56f, 88f) }
        FigureDeco.Bar -> poly(58f, 12f, 102f, 12f)
        FigureDeco.Step -> poly(84f, 88f, 84f, 68f, 128f, 68f, 128f, 88f)
    }
}
