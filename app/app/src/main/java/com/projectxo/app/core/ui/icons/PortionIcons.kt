package com.projectxo.app.core.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import com.projectxo.app.core.ui.theme.XoSize
import com.projectxo.app.core.ui.theme.XoTheme

/**
 * Иконки порций воды (docs/DESIGN.md, 5a): одна линия, скругления, вода внутри — линия акцента.
 * Чем больше объём, тем выше силуэт. Холст 28 × 28; те же пути, что в design/water-vessel.html.
 */
private class PortionShape(val body: List<String>, val water: String)

private val SHAPES = mapOf(
    150 to PortionShape(listOf("M9.5 13h9l-1 10.5h-7z"), "M10 17.5h8"),
    250 to PortionShape(listOf("M8.5 6h11l-1.4 17.5h-8.2z"), "M9.3 13h9.4"),
    350 to PortionShape(
        listOf(
            "M6.5 8.5h12v12.5a2.5 2.5 0 0 1-2.5 2.5h-7a2.5 2.5 0 0 1-2.5-2.5z",
            "M18.5 11.5h1.8a2.7 2.7 0 0 1 0 5.4h-1.8",
        ),
        "M6.5 13.5h12",
    ),
    500 to PortionShape(
        listOf("M12 2.5h4v3.2l2.3 3.3v13a2.5 2.5 0 0 1-2.5 2.5h-3.6a2.5 2.5 0 0 1-2.5-2.5V9l2.3-3.3z"),
        "M9.7 14h8.6",
    ),
)

private const val VIEWPORT = 28f

@Composable
fun PortionIcon(ml: Int, modifier: Modifier = Modifier) {
    val colors = XoTheme.colors
    val shape = SHAPES[ml] ?: SHAPES.getValue(250)
    val paths = remember(ml) {
        Pair(shape.body.map { PathParser().parsePathString(it).toPath() }, PathParser().parsePathString(shape.water).toPath())
    }
    // Толщина линии задана в dp и не меняется от масштаба холста.
    val strokeDp = with(LocalDensity.current) { XoSize.iconStroke.toPx() }
    Canvas(modifier.size(XoSize.icon)) {
        val k = size.minDimension / VIEWPORT
        val stroke = Stroke(width = strokeDp / k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            paths.first.forEach { p: Path -> drawPath(p, colors.ink, style = stroke) }
            drawPath(paths.second, colors.accent, style = stroke)
        }
    }
}
