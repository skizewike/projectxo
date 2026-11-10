package com.projectxo.app.core.model

import kotlin.math.roundToInt

/** Объём воды: в базе всегда мл, единицы — только для показа и ввода (Р50, Р60). */
object VolumeMath {
    const val ML_PER_OZ = 29.5735

    fun mlToOz(ml: Double): Double = ml / ML_PER_OZ

    /** Унции (американские, жидкие) в целые мл. */
    fun ozToMl(oz: Int): Int = (oz * ML_PER_OZ).roundToInt()

    /** Значение для показа в выбранных единицах. */
    fun display(ml: Long, unit: VolumeUnit): Double = if (unit == VolumeUnit.Ml) ml.toDouble() else mlToOz(ml.toDouble())

    /** Своя порция: границы ввода в выбранных единицах (целые числа). */
    fun customRange(unit: VolumeUnit): IntRange = when (unit) {
        VolumeUnit.Ml -> WaterPortions.MIN_CUSTOM..WaterPortions.MAX_CUSTOM
        VolumeUnit.Oz -> 1..(WaterPortions.MAX_CUSTOM / ML_PER_OZ).toInt()
    }

    /** Введённое число в мл, или null, если вне границ. */
    fun customToMl(input: Int, unit: VolumeUnit): Int? {
        if (input !in customRange(unit)) return null
        val ml = if (unit == VolumeUnit.Ml) input else ozToMl(input)
        return ml.takeIf { WaterPortions.isValid(it) }
    }
}
