package com.projectxo.app.core.model

/** Порции воды (docs/DESIGN.md, 5a). */
object WaterPortions {
    /** Плитки на экране «Вода»: маленький стакан, стакан, кружка, бутылка. */
    val TILES = listOf(150, 250, 350, 500)
    const val PRIMARY = 250

    /** Плитки в унциях: 4, 8, 12, 16 унц. Формы значков те же, что у плиток в мл. */
    val TILES_OZ = listOf(4, 8, 12, 16)

    /** Объём плитки номер [index] в мл для выбранных единиц. */
    fun tileMl(index: Int, unit: VolumeUnit): Int =
        if (unit == VolumeUnit.Ml) TILES[index] else VolumeMath.ozToMl(TILES_OZ[index])

    /** Главная порция («+» на «Сегодня», виджет, уведомление): 250 мл или 8 унц. */
    fun primaryMl(unit: VolumeUnit): Int = tileMl(PRIMARY_INDEX, unit)

    const val PRIMARY_INDEX = 1
    const val MIN_CUSTOM = 1
    const val MAX_CUSTOM = 3000

    fun isValid(ml: Int): Boolean = ml in MIN_CUSTOM..MAX_CUSTOM
}

/** Сколько держится полоса «Отменить / Вернуть». */
object UndoPolicy {
    const val WINDOW_MS = 4000L
}
