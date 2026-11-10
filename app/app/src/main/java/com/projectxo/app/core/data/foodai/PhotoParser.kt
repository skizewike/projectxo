package com.projectxo.app.core.data.foodai

import com.projectxo.app.core.model.Confidence
import com.projectxo.app.core.model.FoodPhotoMath
import com.projectxo.app.core.model.PhotoDish
import com.projectxo.app.core.model.PhotoItem
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Разбор JSON-ответа ИИ в [PhotoDish]. Ответ не доверяем: поля проверяются и ограничиваются,
 * числа проходят через [FoodPhotoMath.clean]. Битый ответ даёт [PhotoResult.Fail] с [PhotoError.BadAnswer].
 */
object PhotoParser {
    private const val MAX_NAME = 60
    private const val MAX_REASON = 90
    private const val MAX_REASONS = 3
    private const val MAX_CLARIFY = 140

    fun parse(raw: String): PhotoResult {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return bad()
        val root = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return bad()

        if (!root.optBoolean("is_food", true)) return PhotoResult.NotFood
        val arr: JSONArray = root.optJSONArray("items") ?: return bad()

        val items = buildList {
            for (i in 0 until minOf(arr.length(), FoodPhotoMath.MAX_ITEMS)) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim().take(MAX_NAME)
                val grams = o.optDouble("grams", Double.NaN)
                val kcal = o.optDouble("kcal_100", Double.NaN)
                val p = o.optDouble("protein_100", Double.NaN)
                val f = o.optDouble("fat_100", Double.NaN)
                val c = o.optDouble("carbs_100", Double.NaN)
                if (name.isEmpty() || listOf(grams, kcal, p, f, c).any { it.isNaN() }) continue
                add(FoodPhotoMath.clean(PhotoItem(name, grams.roundToInt(), kcal, p, f, c, o.optBoolean("added_fat", false))))
            }
        }.filter { it.grams > 0 }
        if (items.isEmpty()) return bad()

        val confidence = when (root.optString("confidence").lowercase()) {
            "high" -> Confidence.High
            "low" -> Confidence.Low
            else -> Confidence.Medium
        }
        val reasons = root.optJSONArray("health_reasons")?.let { a ->
            (0 until a.length()).map { a.optString(it).trim().take(MAX_REASON) }.filter { it.isNotEmpty() }.take(MAX_REASONS)
        }.orEmpty()
        val clarify = root.optString("clarify").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }?.take(MAX_CLARIFY)
        return PhotoResult.Ok(
            PhotoDish(
                name = root.optString("dish").trim().take(MAX_NAME),
                items = items,
                confidence = confidence,
                clarify = clarify,
                healthScore = root.optInt("health_score", 5).coerceIn(1, 10),
                healthReasons = reasons,
            ),
        )
    }

    private fun bad() = PhotoResult.Fail(PhotoError.BadAnswer)
}
