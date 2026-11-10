package com.projectxo.app.core.model

import com.projectxo.app.core.data.FoodDraft
import kotlin.math.abs
import kotlin.math.roundToInt

// Еда по фото (Р69). Чистый Kotlin без android.*: разбор блюда на составляющие и пересчёт после правок.
// ИИ даёт составляющие с граммами и значениями на 100 г. Итоги всегда считает приложение,
// поэтому правка граммов или ответ на вопрос не требуют нового запроса.

/** Составляющая блюда: значения на 100 г готового продукта и граммы в порции. */
data class PhotoItem(
    val name: String,
    val grams: Int,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    /** Масло или другой жир, добавленный при готовке: его меняет ответ «Чем готовили?». */
    val isAddedFat: Boolean = false,
)

/** Насколько ИИ уверен в разборе. От этого зависит ширина диапазона калорий. */
enum class Confidence { Low, Medium, High }

data class PhotoDish(
    val name: String,
    val items: List<PhotoItem>,
    val confidence: Confidence,
    /** Короткий вопрос от ИИ, если он не уверен, иначе null. */
    val clarify: String?,
    /** Оценка пользы 1..10: ориентир, а не медицинский вывод. */
    val healthScore: Int,
    val healthReasons: List<String>,
)

/** Ответ на «Чем готовили?». */
enum class OilChoice(val fatG: Int?) { None(0), Some(5), Lots(15), AsPhoto(null) }

/** Ответ на «Сколько весит порция?». */
enum class PortionChoice(val grams: Int?) { Small(250), Mid(350), Large(450), Custom(null), AsPhoto(null) }

data class PhotoTotals(val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int, val grams: Int)

object FoodPhotoMath {
    const val MAX_ITEM_G = 1500
    const val MAX_KCAL_100 = 900.0
    const val MAX_ITEMS = 12
    const val MIN_CUSTOM_G = 50
    const val MAX_CUSTOM_G = 1500
    private const val MACRO_KCAL_TOLERANCE = 0.25
    private const val MIN_MACRO_KCAL = 5.0
    private const val MIN_NON_FAT_G = 20.0

    /**
     * Защита от арифметических ошибок ИИ: макро в сумме не больше 100 г на 100 г,
     * калории не выше 900 и не расходятся с макро больше чем на 25 %.
     */
    fun clean(item: PhotoItem): PhotoItem {
        var p = item.protein100.coerceIn(0.0, 100.0)
        var f = item.fat100.coerceIn(0.0, 100.0)
        var c = item.carbs100.coerceIn(0.0, 100.0)
        val sum = p + f + c
        if (sum > 100.0) {
            val k = 100.0 / sum
            p *= k; f *= k; c *= k
        }
        val macroKcal = FoodRules.KCAL_PER_G_PROTEIN * p + FoodRules.KCAL_PER_G_FAT * f + FoodRules.KCAL_PER_G_CARBS * c
        var kcal = item.kcal100.coerceIn(0.0, MAX_KCAL_100)
        if (macroKcal >= MIN_MACRO_KCAL && abs(kcal - macroKcal) > MACRO_KCAL_TOLERANCE * macroKcal) kcal = macroKcal
        return item.copy(grams = item.grams.coerceIn(0, MAX_ITEM_G), kcal100 = kcal, protein100 = p, fat100 = f, carbs100 = c)
    }

    fun totals(items: List<PhotoItem>): PhotoTotals {
        var k = 0.0; var p = 0.0; var f = 0.0; var c = 0.0; var g = 0
        items.forEach {
            val m = it.grams / 100.0
            k += it.kcal100 * m; p += it.protein100 * m; f += it.fat100 * m; c += it.carbs100 * m
            g += it.grams
        }
        return PhotoTotals(k.roundToInt(), p.roundToInt(), f.roundToInt(), c.roundToInt(), g)
    }

    /** Диапазон калорий, округлённый до 10: чем меньше уверенность, тем шире. */
    fun range(kcal: Int, confidence: Confidence): Pair<Int, Int> {
        val (lo, hi) = when (confidence) {
            Confidence.Low -> 0.72 to 1.35
            Confidence.Medium -> 0.85 to 1.18
            Confidence.High -> 0.92 to 1.10
        }
        return round10(kcal * lo) to round10(kcal * hi)
    }

    private fun round10(x: Double) = (x / 10.0).roundToInt() * 10

    /** Жир при готовке равен [fatG]: меняет, добавляет или убирает составляющую-жир. */
    fun withFat(items: List<PhotoItem>, fatG: Int, fatName: String): List<PhotoItem> {
        val i = items.indexOfFirst { it.isAddedFat }
        return when {
            i >= 0 && fatG <= 0 -> items.filterIndexed { idx, _ -> idx != i }
            i >= 0 -> items.mapIndexed { idx, it -> if (idx == i) it.copy(grams = fatG) else it }
            fatG > 0 -> items + OIL.copy(name = fatName, grams = fatG)
            else -> items
        }
    }

    /** Порция весит [targetG]: все составляющие, кроме жира при готовке, растягиваются поровну. */
    fun withPortion(items: List<PhotoItem>, targetG: Int): List<PhotoItem> {
        val fat = items.filter { it.isAddedFat }.sumOf { it.grams }
        val rest = items.filterNot { it.isAddedFat }.sumOf { it.grams }
        if (rest <= 0) return items
        val k = (targetG - fat).coerceAtLeast(MIN_NON_FAT_G.toInt()).toDouble() / rest
        return items.map { if (it.isAddedFat) it else it.copy(grams = (it.grams * k).roundToInt().coerceIn(1, MAX_ITEM_G)) }
    }

    fun step(items: List<PhotoItem>, index: Int, deltaG: Int): List<PhotoItem> =
        items.mapIndexed { i, it -> if (i == index) it.copy(grams = (it.grams + deltaG).coerceIn(0, MAX_ITEM_G)) else it }

    /** Шаг правки граммов: мелкий для масла, обычный для остального. */
    fun stepSize(item: PhotoItem) = if (item.isAddedFat) 5 else 10

    fun toDraft(name: String, items: List<PhotoItem>, unnamed: String): FoodDraft {
        val t = totals(items)
        return FoodDraft(
            name.trim().take(FoodRules.MAX_NAME_LENGTH).ifEmpty { unnamed },
            t.kcal.coerceIn(1, FoodRules.MAX_KCAL),
            t.proteinG.coerceAtMost(FoodRules.MAX_MACRO_G),
            t.fatG.coerceAtMost(FoodRules.MAX_MACRO_G),
            t.carbsG.coerceAtMost(FoodRules.MAX_MACRO_G),
        )
    }

    private val OIL = PhotoItem("", 0, 884.0, 0.0, 100.0, 0.0, isAddedFat = true)
}
