package com.projectxo.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.projectxo.app.core.model.WellbeingTag

/**
 * Иконки самочувствия: нарисованы под XO, сетка 24, линия 1,5, без эмодзи (Р48, design/calendar.html).
 * Цвет задаёт Icon(tint).
 */
object WellbeingIcons {
    private const val W = 1.5f

    /** [fill] — закрашенные части (заряд батареи). */
    private fun icon(name: String, vararg paths: Pair<String, Float>, fill: String? = null): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { (d, w) ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = w,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            if (fill != null) addPath(pathData = addPathNodes(fill), fill = SolidColor(Color.Black))
        }.build()

    private val battery = "M3.5 9.5a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2zM20.5 10.5v3"

    private val byTag: Map<WellbeingTag, ImageVector> = mapOf(
        WellbeingTag.Energetic to icon("energy", battery to W, fill = "M6.5 10.5h3v3h-3zM10.5 10.5h3v3h-3zM14.5 10.5h1.5v3h-1.5z"),
        WellbeingTag.Tired to icon("tired", battery to W, fill = "M6.5 10.5h2.5v3h-2.5z"),
        WellbeingTag.Sleepy to icon("sleepy", "M18 14.5A7 7 0 1 1 9.5 6a5.5 5.5 0 0 0 8.5 8.5z" to W, "M15 3.5h3l-3 3h3" to W),
        WellbeingTag.Head to icon(
            "head", "M5 12a7 7 0 1 0 14 0a7 7 0 1 0 -14 0" to W, "M4 6l2.5 2M20 6l-2.5 2M12 1.8v2.5" to W, "M9.5 13.5c1.4 1 3.6 1 5 0" to W,
        ),
        WellbeingTag.Back to icon("back", "M12 3v18" to W, "M9.5 5.5h5M9 9.5h6M9 13.5h6M9.5 17.5h5" to W),
        WellbeingTag.Muscle to icon(
            "muscle", "M4.5 17.5c2-4 4-7 7.5-9 1.2-.7 2.5-1.6 3-3l2.5 1.5c-.4 1.7-1.6 2.6-2.8 3.3 2.3.3 4.3 2.1 4.3 4.6 0 2.4-2.1 4.1-4.7 4.1H4.5z" to W,
        ),
        WellbeingTag.Joints to icon(
            "joint", "M9 3.5v6.5a3 3 0 0 0 6 0V3.5" to W, "M9 20.5V16a3 3 0 0 1 6 0v4.5" to W, "M10.8 13a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0" to W,
        ),
        WellbeingTag.Belly to icon("belly", "M7 4v4a5 5 0 0 0 0 8v4M17 4v4a5 5 0 0 1 0 8v4" to W, "M10.7 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0" to W),
        WellbeingTag.Calm to icon(
            "calm", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W,
            "M8.5 10.3c.6-.6 1.4-.6 2 0M13.5 10.3c.6-.6 1.4-.6 2 0M9 14.8c1.7 1.1 4.3 1.1 6 0" to W,
        ),
        WellbeingTag.Joy to icon(
            "joy", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W, "M9 9.5v.5M15 9.5v.5" to 2f, "M8 13.2c1.8 3 6.2 3 8 0z" to W,
        ),
        WellbeingTag.Anxiety to icon(
            "anx", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W, "M9 10v.3M15 10v.3" to 2f,
            "M8.5 15.5l1.2-1 1.2 1 1.1-1 1.2 1 1.2-1 1.1 1" to W,
        ),
        WellbeingTag.Irritation to icon(
            "angry", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W, "M8 8.5l2.5 1.5M16 8.5l-2.5 1.5M9 16c1.7-1 4.3-1 6 0" to W,
        ),
        WellbeingTag.Sadness to icon(
            "sad", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W, "M9 10v.3M15 10v.3" to 2f, "M9 16.2c1.7-1.2 4.3-1.2 6 0M16.5 12.5v1.5" to W,
        ),
        WellbeingTag.Stress to icon("stress", "M3.5 13h3l2-5 3.5 10 2.5-7 1.5 2h4.5" to W),
        WellbeingTag.Bloating to icon(
            "bloat", "M6 9.5a6 6 0 1 0 12 0a6 6 0 1 0 -12 0" to W, "M12 15.5l-1 1.5h2zM12 17v3.5" to W, "M9.5 7.5a3 3 0 0 1 2.5-1.5" to W,
        ),
        WellbeingTag.Heartburn to icon(
            "heart", "M12 21c4-3 6-6 6-9.5 0-2-1-3.5-2.5-4.5-.5 2-1.8 3-3 3 .5-3-1-6-3.5-7 .3 3-4 5.5-4 9.5C5 15.5 8 18.5 12 21z" to W,
        ),
        WellbeingTag.Nausea to icon(
            "nausea", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0" to W, "M8.5 10h2M13.5 10h2M8 15c1-1 2-1 3 0s2 1 3 0 2-1 2 0" to W,
        ),
        WellbeingTag.Cold to icon("cold", "M10 4a2 2 0 0 1 4 0v9.5a4 4 0 1 1-4 0z" to W, "M12 9v6.5" to W),
        WellbeingTag.Allergy to icon(
            "allergy", "M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0" to W,
            "M12 9.8V5M12 14.2V19M9.8 12H5M14.2 12H19M10.4 10.4L7 7M13.6 13.6L17 17M13.6 10.4L17 7M10.4 13.6L7 17" to W,
        ),
    )

    fun of(tag: WellbeingTag): ImageVector = byTag.getValue(tag)

    /** Лица оценки дня: 0 (плохо) .. 4 (отлично). Рот — разные линии на одном круге с глазами. */
    private val mouths = listOf(
        "M8.3 16.6c1.9-2 5.5-2 7.4 0",
        "M8.8 16c1.7-.9 4.7-.9 6.4 0",
        "M9 15.4h6",
        "M8.8 14.4c1.7 1.1 4.7 1.1 6.4 0",
        "M8.2 13.8c1.9 2.6 5.7 2.6 7.6 0",
    )

    private val faces: List<ImageVector> = mouths.mapIndexed { i, mouth ->
        icon("face$i", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0" to W, "M9.3 9.8v.2M14.7 9.8v.2" to 2f, mouth to W)
    }

    fun face(feel: Int): ImageVector = faces[feel.coerceIn(0, faces.lastIndex)]
}
