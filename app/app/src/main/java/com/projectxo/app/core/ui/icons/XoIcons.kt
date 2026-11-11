package com.projectxo.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки набора «Предметы» (Р40, design/icons-variants.html, вариант 02).
 * Нарисованы под XO: сетка 24 × 24, линия 1,5, круглые концы. Цвет задаёт Icon(tint).
 */
object XoIcons {
    internal fun icon(name: String, vararg paths: Pair<String, Float>): ImageVector =
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
        }.build()

    private const val W = 1.5f

    val Today = icon("today", "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0" to W, "M12 4a8 8 0 0 1 8 8" to 3f)
    val Water = icon(
        "water",
        "M12 3.5c3.4 4.2 5.8 7.4 5.8 10.2a5.8 5.8 0 0 1-11.6 0C6.2 10.9 8.6 7.7 12 3.5z" to W,
        "M9.2 14.2a2.8 2.8 0 0 0 2.8 2.8" to W,
    )
    val Food = icon(
        "food",
        "M7 3.5v5.5a2 2 0 0 0 2 2M11 3.5v5.5a2 2 0 0 1-2 2v9.5M9 3.5v5" to W,
        "M17 20.5V3.5c-2 1.5-3 4-3 7.5h3" to W,
    )
    val Steps = icon(
        "steps",
        "M8.5 3.5c1.8 0 2.7 1.6 2.7 3.8 0 2.4-.9 4.2-2.7 4.2S5.8 9.7 5.8 7.3c0-2.2.9-3.8 2.7-3.8zM7 13.8l3.4.3-.3 2.1a1.7 1.7 0 0 1-3.3-.5z" to W,
        "M15.5 8c1.8 0 2.7 1.6 2.7 3.8 0 2.4-.9 4.2-2.7 4.2s-2.7-1.8-2.7-4.2c0-2.2.9-3.8 2.7-3.8zM14 18.3l3.4.3-.3 2.1a1.7 1.7 0 0 1-3.3-.5z" to W,
    )
    val Settings = icon("settings", "M4 7h10M18 7h2M4 17h2M10 17h10" to W, "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M6 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0" to W)
    val Back = icon("back", "M15 5l-7 7 7 7" to W)
    val Check = icon("check", "M6 12.5l4 4 8-9" to 2f)
    val Phone = icon("phone", "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM10 18h4" to W)
    val Shield = icon("shield", "M12 3l7.5 3v5.5c0 4.5-3.2 8.2-7.5 9.5-4.3-1.3-7.5-5-7.5-9.5V6z" to W, "M9 12l2 2 4-4.5" to W)
    val Trash = icon("trash", "M4.5 6.5h15M9.5 6.5V4.5h5v2M6.5 6.5l1 13h9l1-13" to W)

    // Календарь и настройки (design/calendar.html, design/settings.html).
    val Close = icon("close", "M6 6l12 12M18 6L6 18" to W)
    val Plus = icon("plus", "M12 5v14M5 12h14" to W)
    // Еда по фото (design/food-photo-button.html, вариант 01 «Камера»).
    val Camera = icon(
        "camera",
        "M4 8.5A1.5 1.5 0 0 1 5.5 7h2.2l1.1-1.6A1 1 0 0 1 9.6 5h4.8a1 1 0 0 1 .8.4L16.3 7h2.2A1.5 1.5 0 0 1 20 8.5v8a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 16.5z" to W,
        "M8.8 12.5a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0" to W,
    )
    val Search = icon("search", "M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0M16 16l4 4" to W)
    val Chevron = icon("chevron", "M9 5l7 7-7 7" to W)
    val Calendar = icon("calendar", "M4.5 6.5h15v13h-15zM4.5 10.5h15M8.5 4v4M15.5 4v4" to W)
    val User = icon("user", "M8.5 8.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5" to W)
    val Target = icon("target", "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" to W)
    val Grid = icon("grid", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z" to W)
    val Ruler = icon("ruler", "M4 16L16 4l4 4L8 20z" to W, "M8 12l1.5 1.5M10.5 9.5L12 11M13 7l1.5 1.5" to W)
    val Palette = icon(
        "palette",
        "M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.6-.9 1.2-1.8-.5-1.1.2-2.2 1.4-2.2H17a3.5 3.5 0 0 0 3.5-3.5c0-5.3-3.8-9.5-8.5-9.5z" to W,
    )
    val Globe = icon("globe", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0M3.5 12h17M12 3.5c2.5 2.3 3.7 5.1 3.7 8.5s-1.2 6.2-3.7 8.5c-2.5-2.3-3.7-5.1-3.7-8.5S9.5 5.8 12 3.5z" to W)
    val Text = icon("text", "M5 18L10 6l5 12M6.8 14h6.4M16 18l2.5-6 2.5 6" to W)
    val Bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" to W, "M10 20.5a2 2 0 0 0 4 0" to W)
    val Doc = icon("doc", "M7 3.5h7l4 4v13H7z" to W, "M14 3.5v4h4M9.5 12h6M9.5 15.5h6" to W)
    val Info = icon("info", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0M12 11v5.5M12 7.8v.2" to W)
    val Note = icon("note", "M6 3.5h9l3 3v14H6z" to W, "M9 10h6M9 13.5h6M9 17h4" to W)
    val Scale = icon("scale", "M4 8a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z" to W, "M8.5 10a5 5 0 0 1 7 0L13 12.2" to W)
    val Fasting = icon("fasting", "M4.5 13a7.5 7.5 0 1 0 15 0a7.5 7.5 0 1 0 -15 0" to W, "M12 9v4l2.5 2M10 2.5h4" to W)
    val Sleep = icon("sleep", "M19 14.8A7.8 7.8 0 1 1 9.2 5a6.2 6.2 0 0 0 9.8 9.8z" to W, "M16 3.5h3l-3 3h3" to W)
    val Habits = icon("habits", "M5 5h14v14H5z" to W, "M9 9l6 6M15 9l-6 6" to W)
    val Mood = icon("mood", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z" to W, "M9 9.8v.4M15 9.8v.4" to W, "M8.5 14.5c1 1.2 2.2 1.8 3.5 1.8s2.5-.6 3.5-1.8" to W)
    val Breathing = icon("breathing", "M3 12c2.5-5 4.5-5 6 0s3.5 5 6 0 3.5-5 6 0" to W)
    val More = icon("more", "M5.1 12a.9.9 0 1 0 1.8 0a.9.9 0 1 0 -1.8 0M11.1 12a.9.9 0 1 0 1.8 0a.9.9 0 1 0 -1.8 0M17.1 12a.9.9 0 1 0 1.8 0a.9.9 0 1 0 -1.8 0" to 2f)
    val Sun = icon("sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0" to W, "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4" to W)
    val Pen = icon("pen", "M4 20h4L19 9l-4-4L4 16z" to W)
    val Up = icon("up", "M6 14l6-6 6 6" to W)
    val Down = icon("down", "M6 10l6 6 6-6" to W)
    val Dumbbell = icon("dumbbell", "M6.5 7v10M17.5 7v10M4 9.5v5M20 9.5v5M6.5 12h11" to W)
}
