package com.projectxo.app.core.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlin.math.ceil
import com.projectxo.app.R
import com.projectxo.app.core.motion.SplashGeometry
import com.projectxo.app.core.ui.theme.XoMotion
import com.projectxo.app.core.ui.theme.XoSplashSize
import com.projectxo.app.core.ui.theme.XoTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Заставка 01 «Загрузка в букве» (docs/DESIGN.md, раздел 6).
 * Один холст поверх приложения: X, подложка и дуга O. O прилетает в кольцо «Баланс дня» на «Сегодня».
 */
@Composable
fun SplashOverlay(controller: SplashController, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val type = XoTheme.type
    val density = LocalDensity.current

    val loadShown = remember { Animatable(0f) }
    LaunchedEffect(controller) {
        snapshotFlow { controller.loadProgress }.collect {
            loadShown.animateTo(it, tween(XoMotion.LOAD_FOLLOW_MS, easing = XoMotion.Standard))
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics { }) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val px = with(density) {
            object {
                val cx = w / 2f
                val cy = h / 2f - XoSplashSize.opticalLift.toPx()
                val xOffset = XoSplashSize.xOffset.toPx()
                val oOffset = XoSplashSize.oOffset.toPx()
                val xHalf = XoSplashSize.xHalf.toPx()
                val xStroke = XoSplashSize.xStroke.toPx()
                val oRadius = XoSplashSize.oRadius.toPx()
                val oStroke = XoSplashSize.oStroke.toPx()
                val taglineTop = XoSplashSize.taglineTop.toPx()
                val nib = XoSplashSize.nib.toPx()
                val cursorWidth = XoSplashSize.cursorWidth.toPx()
            }
        }

        LaunchedEffect(controller, w, h) {
            val target = snapshotFlow { controller.target }.filterNotNull().first()
            val geometry = SplashGeometry(
                xCx = px.cx + px.xOffset, xCy = px.cy, xHalf = px.xHalf, xStroke = px.xStroke,
                oCx = px.cx + px.oOffset, oCy = px.cy, oRadius = px.oRadius, oStroke = px.oStroke,
                target = target, ratio = controller.ratio,
            )
            controller.run(geometry) { loadShown.value }
        }

        val ringPaint = remember {
            Paint().apply { isAntiAlias = true; style = PaintingStyle.Stroke; strokeCap = StrokeCap.Round }
        }
        val trackPaint = remember {
            Paint().apply { isAntiAlias = true; style = PaintingStyle.Stroke; strokeCap = StrokeCap.Butt }
        }
        val xPaint = remember {
            Paint().apply { isAntiAlias = true; style = PaintingStyle.Stroke; strokeCap = StrokeCap.Butt }
        }

        Canvas(Modifier.fillMaxSize()) {
            val f = controller.frame ?: return@Canvas
            val alpha = f.stageAlpha * f.logoAlpha
            if (alpha <= 0f) return@Canvas
            drawIntoCanvas { canvas ->
                // Подложка O
                if (f.trackAlpha > 0f) {
                    trackPaint.color = colors.track.copy(alpha = colors.track.alpha * f.trackAlpha * alpha)
                    trackPaint.strokeWidth = f.ringStroke
                    canvas.drawCircle(Offset(f.ringCx, f.ringCy), f.ringRadius, trackPaint)
                }
                // Дуга O / кольца «Баланс дня»
                val sweep = (f.arcEnd - f.arcStart).coerceAtLeast(0f)
                if (sweep * 2f * PI.toFloat() * f.ringRadius >= 0.5f) {
                    ringPaint.color = colors.accent.copy(alpha = alpha)
                    ringPaint.strokeWidth = f.ringStroke
                    canvas.drawArc(
                        f.ringCx - f.ringRadius, f.ringCy - f.ringRadius,
                        f.ringCx + f.ringRadius, f.ringCy + f.ringRadius,
                        -90f + 360f * f.arcStart, 360f * sweep, false, ringPaint,
                    )
                }
                // X: два штриха
                xPaint.color = colors.ink.copy(alpha = alpha)
                xPaint.strokeWidth = f.xStroke
                val l = f.xCx - f.xHalf
                val r = f.xCx + f.xHalf
                val t = f.xCy - f.xHalf
                val b = f.xCy + f.xHalf
                if (f.stroke1 > 0f) {
                    canvas.drawLine(Offset(l, t), Offset(l + (r - l) * f.stroke1, t + (b - t) * f.stroke1), xPaint)
                }
                if (f.stroke2 > 0f) {
                    canvas.drawLine(Offset(r, t), Offset(r + (l - r) * f.stroke2, t + (b - t) * f.stroke2), xPaint)
                }
                // Свежие чернила: последние 14 dp штриха окрашены акцентом и остывают (DESIGN.md, 5b п. 5).
                val nibFrac = px.nib / (SQRT2 * (r - l))
                if (f.nib1 > 0f && f.stroke1 > 0f) {
                    val from = (f.stroke1 - nibFrac).coerceAtLeast(0f)
                    xPaint.color = colors.accent.copy(alpha = alpha * f.nib1)
                    canvas.drawLine(
                        Offset(l + (r - l) * from, t + (b - t) * from),
                        Offset(l + (r - l) * f.stroke1, t + (b - t) * f.stroke1), xPaint,
                    )
                }
                if (f.nib2 > 0f && f.stroke2 > 0f) {
                    val from = (f.stroke2 - nibFrac).coerceAtLeast(0f)
                    xPaint.color = colors.accent.copy(alpha = alpha * f.nib2)
                    canvas.drawLine(
                        Offset(r + (l - r) * from, t + (b - t) * from),
                        Offset(r + (l - r) * f.stroke2, t + (b - t) * f.stroke2), xPaint,
                    )
                }
            }
        }

        // Печатная машинка (DESIGN.md, 5b п. 6): строка свёрстана целиком, ненапечатанное прозрачно.
        val tagline = stringResource(R.string.splash_tagline)
        val typed by remember(tagline) {
            derivedStateOf { ceil((controller.frame?.tagTyped ?: 0f) * tagline.length).toInt().coerceIn(0, tagline.length) }
        }
        val shown = remember(tagline, typed) {
            buildAnnotatedString {
                append(tagline.substring(0, typed))
                withStyle(SpanStyle(color = Color.Transparent)) { append(tagline.substring(typed)) }
            }
        }
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = shown,
            style = type.tagline,
            color = colors.muted,
            textAlign = TextAlign.Center,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (px.cy + px.taglineTop).roundToInt()) }
                .graphicsLayer {
                    val f = controller.frame
                    alpha = if (f == null) 0f else f.tagAlpha * f.stageAlpha
                }
                .drawWithContent {
                    drawContent()
                    val f = controller.frame ?: return@drawWithContent
                    val l = layout ?: return@drawWithContent
                    if (f.cursorAlpha <= 0f) return@drawWithContent
                    val x = l.getHorizontalPosition(typed, usePrimaryDirection = true)
                    val top = l.getLineTop(0)
                    val bottom = l.getLineBottom(0)
                    drawRect(
                        colors.ink, topLeft = Offset(x, top),
                        size = Size(px.cursorWidth, bottom - top), alpha = f.cursorAlpha,
                    )
                },
        )
    }
}

private const val SQRT2 = 1.4142135f
