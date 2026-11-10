package com.projectxo.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodPhotoMathTest {
    private val buckwheat = PhotoItem("Гречка", 180, 110.0, 4.2, 1.1, 21.0)
    private val chicken = PhotoItem("Курица", 120, 165.0, 31.0, 3.6, 0.0)
    private val oil = PhotoItem("Масло", 10, 884.0, 0.0, 100.0, 0.0, isAddedFat = true)
    private val dish = listOf(buckwheat, chicken, oil)

    @Test
    fun totals_sumPerHundredGrams() {
        val t = FoodPhotoMath.totals(listOf(buckwheat, chicken))
        assertEquals(198 + 198, t.kcal)
        assertEquals(8 + 37, t.proteinG) // 7,56 + 37,2 = 44,76 округляется до 45
        assertEquals(300, t.grams)
    }

    @Test
    fun clean_fixesKcalThatContradictsMacros() {
        // ИИ написал 50 ккал, а макро дают 4·20 + 9·10 + 4·30 = 290.
        val c = FoodPhotoMath.clean(PhotoItem("x", 100, 50.0, 20.0, 10.0, 30.0))
        assertEquals(290.0, c.kcal100, 0.01)
    }

    @Test
    fun clean_keepsConsistentKcal() {
        val c = FoodPhotoMath.clean(buckwheat)
        assertEquals(110.0, c.kcal100, 0.01)
    }

    @Test
    fun clean_macrosCannotExceedHundredGrams() {
        val c = FoodPhotoMath.clean(PhotoItem("x", 100, 400.0, 60.0, 60.0, 60.0))
        assertTrue(c.protein100 + c.fat100 + c.carbs100 <= 100.0001)
    }

    @Test
    fun clean_zeroCalorieDrinkStaysZero() {
        val c = FoodPhotoMath.clean(PhotoItem("Чай", 250, 0.0, 0.0, 0.0, 0.0))
        assertEquals(0.0, c.kcal100, 0.0)
    }

    @Test
    fun clean_limitsGramsAndKcal() {
        val c = FoodPhotoMath.clean(PhotoItem("x", 99_999, 5000.0, 0.0, 0.0, 0.0))
        assertEquals(FoodPhotoMath.MAX_ITEM_G, c.grams)
        assertEquals(FoodPhotoMath.MAX_KCAL_100, c.kcal100, 0.0)
    }

    @Test
    fun range_widerWhenLessSure() {
        val low = FoodPhotoMath.range(500, Confidence.Low)
        val high = FoodPhotoMath.range(500, Confidence.High)
        assertTrue(low.first < high.first && low.second > high.second)
        assertEquals(460 to 550, high)
    }

    @Test
    fun withFat_zeroRemovesFatItem() {
        val r = FoodPhotoMath.withFat(dish, 0, "Масло")
        assertEquals(2, r.size)
        assertTrue(r.none { it.isAddedFat })
    }

    @Test
    fun withFat_setsGramsOfExistingFatItem() {
        val r = FoodPhotoMath.withFat(dish, 15, "Масло")
        assertEquals(15, r.single { it.isAddedFat }.grams)
    }

    @Test
    fun withFat_addsFatItemWhenAbsent() {
        val r = FoodPhotoMath.withFat(listOf(buckwheat), 5, "Масло при готовке")
        val fat = r.single { it.isAddedFat }
        assertEquals("Масло при готовке", fat.name)
        assertEquals(5, fat.grams)
        assertTrue(FoodPhotoMath.totals(r).fatG > FoodPhotoMath.totals(listOf(buckwheat)).fatG)
    }

    @Test
    fun withFat_noFatAndZeroChangesNothing() {
        assertEquals(listOf(buckwheat), FoodPhotoMath.withFat(listOf(buckwheat), 0, "Масло"))
    }

    @Test
    fun withPortion_scalesFoodButNotOil() {
        val r = FoodPhotoMath.withPortion(dish, 310) // 300 г еды + 10 г масла
        assertEquals(10, r.single { it.isAddedFat }.grams)
        assertEquals(300, r.filterNot { it.isAddedFat }.sumOf { it.grams })
        val bigger = FoodPhotoMath.withPortion(dish, 460)
        assertEquals(450, bigger.filterNot { it.isAddedFat }.sumOf { it.grams })
    }

    @Test
    fun withPortion_onlyOilItem_unchanged() {
        assertEquals(listOf(oil), FoodPhotoMath.withPortion(listOf(oil), 300))
    }

    @Test
    fun step_changesOneItemAndStaysInRange() {
        val r = FoodPhotoMath.step(dish, 0, 10)
        assertEquals(190, r[0].grams)
        assertEquals(120, r[1].grams)
        assertEquals(0, FoodPhotoMath.step(dish, 2, -100)[2].grams)
        assertEquals(5, FoodPhotoMath.stepSize(oil))
        assertEquals(10, FoodPhotoMath.stepSize(buckwheat))
    }

    @Test
    fun toDraft_validForFoodRules() {
        val d = FoodPhotoMath.toDraft("  Гречка с курицей  ", dish, "Без названия")
        assertEquals("Гречка с курицей", d.name)
        assertTrue(FoodRules.isValid(d))
    }

    @Test
    fun toDraft_blankNameAndHugeNumbersAreClamped() {
        val huge = listOf(PhotoItem("x", 1500, 900.0, 100.0, 0.0, 0.0))
        val d = FoodPhotoMath.toDraft(" ", huge, "Без названия")
        assertEquals("Без названия", d.name)
        assertEquals(FoodRules.MAX_KCAL, d.kcal)
        assertEquals(FoodRules.MAX_MACRO_G, d.proteinG)
        assertTrue(FoodRules.isValid(d))
    }

    @Test
    fun toDraft_longNameIsCut() {
        val d = FoodPhotoMath.toDraft("а".repeat(100), dish, "Без названия")
        assertEquals(FoodRules.MAX_NAME_LENGTH, d.name.length)
    }
}
