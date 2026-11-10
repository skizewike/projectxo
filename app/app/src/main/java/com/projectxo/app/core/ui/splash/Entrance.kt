package com.projectxo.app.core.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.projectxo.app.core.ui.theme.XoSize

/** Номера элементов «Сегодня» в очереди появления после заставки. */
object EntranceSlot {
    const val HEADER = 0
    const val RING_LABEL = 1
    const val ROW_FIRST = 2

    /** Строки после третьей (когда включено больше трёх модулей) появляются вместе с третьей. */
    const val ROW_LAST = 4
    const val NAV = 5
}

/** Появление элемента после заставки: прозрачность 0 → 1 и подъём на 14 dp (docs/DESIGN.md, 6.3). */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val splash = LocalSplash.current
    val rise = with(LocalDensity.current) { XoSize.entranceRise.toPx() }
    return this.graphicsLayer {
        val p = splash.entrance(index)
        alpha = p
        translationY = (1f - p) * rise
    }
}
