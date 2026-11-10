package com.projectxo.app.core.model

import com.projectxo.app.core.ui.format.volumeNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VolumeMathTest {
    private val ru = Locale.forLanguageTag("ru")

    @Test
    fun ounceTiles_areRoundNumbersOfOunces() {
        assertEquals(listOf(118, 237, 355, 473), WaterPortions.TILES_OZ.map { VolumeMath.ozToMl(it) })
        assertEquals(250, WaterPortions.primaryMl(VolumeUnit.Ml))
        assertEquals(237, WaterPortions.primaryMl(VolumeUnit.Oz))
    }

    @Test
    fun mlTiles_unchanged() {
        assertEquals(listOf(150, 250, 350, 500), (0..3).map { WaterPortions.tileMl(it, VolumeUnit.Ml) })
    }

    @Test
    fun customInput_ounces_bounds() {
        assertEquals(1..101, VolumeMath.customRange(VolumeUnit.Oz))
        assertNull(VolumeMath.customToMl(0, VolumeUnit.Oz))
        assertNull(VolumeMath.customToMl(102, VolumeUnit.Oz))
        assertEquals(30, VolumeMath.customToMl(1, VolumeUnit.Oz))
        assertEquals(2987, VolumeMath.customToMl(101, VolumeUnit.Oz))
    }

    @Test
    fun customInput_ml_bounds() {
        assertNull(VolumeMath.customToMl(0, VolumeUnit.Ml))
        assertEquals(3000, VolumeMath.customToMl(3000, VolumeUnit.Ml))
        assertNull(VolumeMath.customToMl(3001, VolumeUnit.Ml))
    }

    @Test
    fun formatting_ml_wholeNumbers_oz_oneDecimalOrWhole() {
        assertEquals("250", volumeNumber(ru, 250, VolumeUnit.Ml))
        assertEquals("8,5", volumeNumber(ru, 250, VolumeUnit.Oz))
        assertEquals("71", volumeNumber(ru, 2100, VolumeUnit.Oz))
        assertEquals("17", volumeNumber(ru, 500, VolumeUnit.Oz, wholeOunces = true))
    }

    @Test
    fun conversion_roundTrip_isStableForPortions() {
        // Запись хранится в мл: 8 унц. → 237 мл → снова 8,0 унц.
        val ml = VolumeMath.ozToMl(8)
        assertEquals(8.0, VolumeMath.mlToOz(ml.toDouble()), 0.05)
    }
}
