package com.projectxo.app.core.ui.theme

import androidx.compose.ui.unit.dp

/** Отступы, радиусы и размеры (docs/DESIGN.md, разделы 3, 4 и 6.2). */
object XoSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
    val s8 = 64.dp
    val screenGutter = 24.dp

    /** Между строкой и подписью под ней (название и пояснение): чтобы строки читались отдельно, не слитно. */
    val textGap = 6.dp

    /** Между заголовком и строкой под ним (заголовок экрана и дата, итог и пояснение). */
    val headGap = 8.dp
}

object XoRadius {
    val s = 8.dp
    val m = 16.dp
}

object XoSize {
    val hairline = 1.dp
    val touchMin = 48.dp
    val rowHeight = 64.dp

    val ringDiameter = 200.dp
    val ringStroke = 12.dp
    val ringDiameterLarge = 168.dp
    val ringStrokeLarge = 10.dp
    /** С какого масштаба текста кольцо уменьшается. */
    const val LARGE_TEXT_SCALE = 1.3f
    /** Предел роста подписей нижней панели. */
    const val NAV_MAX_FONT_SCALE = 1.3f

    /** Циферблат вокруг кольца. */
    const val DIAL_TICKS = 60
    val dialGap = 8.dp
    val dialTick = 5.dp
    val dialTickSeal = 9.dp
    val dialTickWidth = 1.dp

    val marker = 10.dp
    val markerStroke = 1.3.dp
    val bar = 2.dp

    val addButton = 40.dp
    val plus = 14.dp
    val plusStroke = 1.6.dp

    val navHeight = 56.dp
    val navDot = 4.dp
    val navIcon = 22.dp

    val pillHeight = 48.dp
    val buttonHeight = 52.dp

    /** Мерный сосуд и плитки (DESIGN.md, 5a). */
    val vesselWidth = 84.dp
    val vesselRadius = 21.dp
    val vesselBottomGap = 28.dp
    val columnGap = 20.dp
    val vesselTick = 12.dp
    val vesselLabelInset = 10.dp
    val vesselLabelHide = 12.dp
    val goalDash = 3.dp
    val goalSolid = 1.5.dp
    val handleWidth = 24.dp
    val handleHeight = 3.dp
    val handleLift = 7.dp
    val surfaceSagMax = 6.dp
    val dragPillHeight = 22.dp
    val dragPillLift = 30.dp
    val tileHeight = 88.dp
    val tileGap = 10.dp
    val tileRadius = 16.dp
    val icon = 28.dp

    /** «Еда»: квадрат дня (DESIGN.md, «Еда», вариант «Под подписью»). */
    val foodSquare = 164.dp
    val foodSquareRadius = 6.dp
    val foodLayerGap = 2.dp
    val foodOutline = 1.2.dp
    val foodOverHeight = 12.dp
    val foodOverGap = 4.dp
    val foodHatchStep = 5.dp
    val foodTickShort = 6.dp
    val foodTickLong = 10.dp
    val foodSwatch = 9.dp
    val chipHeight = 36.dp
    val chipGap = 8.dp
    val chipRadius = 18.dp
    const val FOOD_MID_TONE = 0.45f
    const val FOOD_OVER_ALPHA = 0.7f

    /** «Ходьба»: столбики по часам (DESIGN.md, 5d). */
    val barsHeight = 190.dp
    val barGap = 4.dp
    val barRadius = 3.dp
    val barMin = 2.dp
    val barLift = 6.dp
    val goalLine = 2.dp
    val traceLift = 6.dp
    val permIcon = 40.dp
    const val BARS_FROM_HOUR = 6
    const val BARS_LABEL_EVERY = 3
    const val BARS_MIN_SCALE = 2000
    const val PAST_BAR_ALPHA = 0.86f
    val iconStroke = 1.5.dp
    val entryRowHeight = 56.dp
    val deleteIcon = 14.dp
    val deleteIconStroke = 1.6.dp

    /** Сдвиг элементов при появлении. */
    val entranceRise = 14.dp
}

/** Геометрия заставки XO (docs/DESIGN.md, 6.2). */
object XoSplashSize {
    val opticalLift = 18.dp
    val xOffset = (-43).dp
    val oOffset = 41.dp
    val xHalf = 30.5.dp
    val xStroke = 10.dp
    val oRadius = 30.75.dp
    val oStroke = 10.5.dp
    val taglineTop = 66.dp
    /** Свежие чернила и курсор печатной машинки (DESIGN.md, 5b п. 5 и 6). */
    val nib = 14.dp
    val cursorWidth = 1.5.dp
}
