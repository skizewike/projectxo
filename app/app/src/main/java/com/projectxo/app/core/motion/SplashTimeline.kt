package com.projectxo.app.core.motion

import kotlin.math.max
import kotlin.math.min

/** Какую заставку показываем (docs/DESIGN.md, 6.3 и 6.4; правило выбора: DECISIONS.md, В5). */
enum class SplashMode { Full, Short }

/**
 * Все тайминги заставки 01 в одном месте, в миллисекундах. Меняет только оркестратор,
 * синхронно с docs/DESIGN.md, раздел 6.
 */
object SplashTimings {
    const val FULL_MS = 2800f
    const val SHORT_MS = 800f
    const val REDUCED_MS = 700f

    object Full {
        val stroke1 = 80f..460f
        val stroke2 = 200f..580f
        val trackIn = 100f..400f
        const val LOAD_START = 300f
        const val PAUSE_START = 900f
        const val PAUSE_END = 1050f
        const val LOAD_END = 1700f
        const val PAUSE_FROM = 0.55f
        const val PAUSE_TO = 0.60f
        /** Печатная машинка: буквы появляются равномерно (DESIGN.md, 5b п. 6). */
        val tagType = 500f..1040f
        const val CURSOR_START = 480f
        const val CURSOR_END = 1440f
        const val CURSOR_BLINK = 200f
        val tagOut = 1700f..1850f
        /** Свежие чернила: акцентный кончик штриха остывает после конца штриха (5b п. 5). */
        const val NIB_COOL = 160f
        val erase2 = 1750f..1950f
        val erase1 = 1820f..2020f
        const val EXIT_START = 1750f
        const val READY_GRACE = 50f
        const val FLY_START = 1800f
        val flyX = SpringParams(0.82f, 300f)
        val flyY = SpringParams(0.9f, 170f)
        const val ARC_START = 1950f
        val arc = SpringParams(1f, 120f)
        const val HOME_START = 2050f
        const val HOME_STEP = 60f
        const val HOME_DURATION = 420f
        /** Стоп-кадр для режима «меньше анимаций»: X прописан, O полный. */
        const val HOLD = 1720f
    }

    object Short {
        val logoIn = 0f..100f
        val erase2 = 0f..180f
        val erase1 = 40f..220f
        val flyX = SpringParams(0.85f, 520f)
        val flyY = SpringParams(0.92f, 360f)
        const val ARC_START = 120f
        val arc = SpringParams(1f, 300f)
        const val HOME_START = 200f
        const val HOME_STEP = 40f
        const val HOME_DURATION = 360f
    }

    object Reduced {
        const val HOLD_END = 300f
        const val FADE_OUT_END = 450f
    }
}

/** Куда прилетает O: кольцо «Баланс дня» на экране «Сегодня», в пикселях окна. */
data class RingTarget(val cx: Float, val cy: Float, val radius: Float, val stroke: Float)

/** Геометрия заставки в пикселях окна (docs/DESIGN.md, 6.2). */
data class SplashGeometry(
    val xCx: Float,
    val xCy: Float,
    val xHalf: Float,
    val xStroke: Float,
    val oCx: Float,
    val oCy: Float,
    val oRadius: Float,
    val oStroke: Float,
    val target: RingTarget,
    /** Фактическое значение «Баланса дня», 0..1. */
    val ratio: Float,
)

/** Один кадр заставки. Всё, что нужно холсту и экрану «Сегодня», без знаний об Android. */
data class SplashFrame(
    val stageAlpha: Float,
    val logoAlpha: Float,
    val xCx: Float,
    val xCy: Float,
    val xHalf: Float,
    val xStroke: Float,
    /** Сколько прописано у штриха 1 и 2, 0..1. */
    val stroke1: Float,
    val stroke2: Float,
    val ringCx: Float,
    val ringCy: Float,
    val ringRadius: Float,
    val ringStroke: Float,
    val arcStart: Float,
    val arcEnd: Float,
    val trackAlpha: Float,
    val tagAlpha: Float,
    /** Доля напечатанных букв подписи, 0..1. */
    val tagTyped: Float,
    /** Видимость курсора печатной машинки, 0 или 1. */
    val cursorAlpha: Float,
    /** Видимость акцентного кончика штриха 1 и 2, 0..1. */
    val nib1: Float,
    val nib2: Float,
    /** Прогресс появления элементов «Сегодня»: шапка, подпись кольца, три строки, панель вкладок. */
    val home: List<Float>,
)

object SplashTimeline {
    const val HOME_ITEMS = 6

    fun duration(mode: SplashMode, reduced: Boolean): Float = when {
        reduced -> SplashTimings.REDUCED_MS
        mode == SplashMode.Full -> SplashTimings.FULL_MS
        else -> SplashTimings.SHORT_MS
    }

    /**
     * Время сценария полной заставки с учётом реальной загрузки (docs/DESIGN.md, 6.5).
     * Пока данные не готовы, сценарий стоит на конце загрузки. Выход начинается не раньше,
     * чем через 50 мс после готовности.
     */
    fun effectiveTime(t: Float, readyAt: Float?): Float {
        val f = SplashTimings.Full
        if (t < f.LOAD_END) return t
        if (readyAt == null) return f.LOAD_END
        val shift = max(0f, readyAt + f.READY_GRACE - f.EXIT_START)
        return if (t < f.LOAD_END + shift) f.LOAD_END else t - shift
    }

    /**
     * Кадр в момент [t] (мс).
     * [loadShown] — реальная доля загрузки 0..1; дуга O не обгоняет её (только полная заставка).
     */
    fun frame(
        t: Float,
        mode: SplashMode,
        reduced: Boolean,
        g: SplashGeometry,
        loadShown: Float = 1f,
    ): SplashFrame {
        if (!reduced) return render(t, mode, g, loadShown)
        val r = SplashTimings.Reduced
        return if (t < r.FADE_OUT_END) {
            val from = if (mode == SplashMode.Full) SplashTimings.Full.HOLD else 0f
            render(from, mode, g, 1f).copy(
                logoAlpha = 1f,
                trackAlpha = 1f,
                stageAlpha = 1f - seg(t, r.HOLD_END..r.FADE_OUT_END),
            )
        } else {
            render(duration(mode, false), mode, g, 1f).copy(
                stageAlpha = seg(t, r.FADE_OUT_END..SplashTimings.REDUCED_MS),
            )
        }
    }

    private fun render(t: Float, mode: SplashMode, g: SplashGeometry, loadShown: Float): SplashFrame =
        if (mode == SplashMode.Full) renderFull(t, g, loadShown) else renderShort(t, g)

    private fun renderFull(t: Float, g: SplashGeometry, loadShown: Float): SplashFrame {
        val f = SplashTimings.Full
        val dec = Curves.EmphasizedDecelerate
        val acc = Curves.EmphasizedAccelerate
        val std = Curves.Standard

        var d1 = dec.transform(seg(t, f.stroke1))
        var d2 = dec.transform(seg(t, f.stroke2))

        val scripted = when {
            t < f.LOAD_START -> 0f
            t < f.PAUSE_START -> f.PAUSE_FROM * std.transform(seg(t, f.LOAD_START..f.PAUSE_START))
            t < f.PAUSE_END -> lerp(f.PAUSE_FROM, f.PAUSE_TO, seg(t, f.PAUSE_START..f.PAUSE_END))
            else -> lerp(f.PAUSE_TO, 1f, dec.transform(seg(t, f.PAUSE_END..f.LOAD_END)))
        }
        var end = min(scripted, clamp01(loadShown))

        val track = std.transform(seg(t, f.trackIn))
        val typed = seg(t, f.tagType)
        val tagAlpha = (if (t >= f.tagType.start) 1f else 0f) * (1f - std.transform(seg(t, f.tagOut)))
        val cursor = if (t >= f.CURSOR_START && t < f.CURSOR_END) {
            // Пока печатает, курсор горит; потом два мигания: 200 мс есть, 200 мс нет.
            if (t < f.tagType.endInclusive) 1f
            else if (((t - f.tagType.endInclusive) / f.CURSOR_BLINK).toInt() % 2 == 0) 0f else 1f
        } else {
            0f
        }
        val nib1 = nib(t, f.stroke1)
        val nib2 = nib(t, f.stroke2)

        if (t >= f.EXIT_START) {
            d2 = 1f - acc.transform(seg(t, f.erase2))
            d1 = 1f - acc.transform(seg(t, f.erase1))
        }
        val px = springProgress(t - f.FLY_START, f.flyX)
        val py = springProgress(t - f.FLY_START, f.flyY)
        if (t >= f.ARC_START) end = lerp(1f, g.ratio, springProgress(t - f.ARC_START, f.arc))

        return frameOf(
            g, px, py, d1, d2, end,
            logoAlpha = 1f,
            trackAlpha = track,
            tagAlpha = tagAlpha,
            tagTyped = typed,
            cursorAlpha = cursor * tagAlpha,
            nib1 = if (t >= f.EXIT_START) 0f else nib1,
            nib2 = if (t >= f.EXIT_START) 0f else nib2,
            home = homeSequence(t, f.HOME_START, f.HOME_STEP, f.HOME_DURATION),
        )
    }

    /** Акцентный кончик: горит, пока штрих пишется, и остывает за NIB_COOL после конца. */
    private fun nib(t: Float, stroke: ClosedFloatingPointRange<Float>): Float {
        val f = SplashTimings.Full
        return when {
            t <= stroke.start -> 0f
            t <= stroke.endInclusive -> 1f
            else -> 1f - Curves.Standard.transform(seg(t, stroke.endInclusive..(stroke.endInclusive + f.NIB_COOL)))
        }
    }

    private fun renderShort(t: Float, g: SplashGeometry): SplashFrame {
        val s = SplashTimings.Short
        val acc = Curves.EmphasizedAccelerate
        val d2 = 1f - acc.transform(seg(t, s.erase2))
        val d1 = 1f - acc.transform(seg(t, s.erase1))
        val px = springProgress(t, s.flyX)
        val py = springProgress(t, s.flyY)
        val end = lerp(1f, g.ratio, springProgress(t - s.ARC_START, s.arc))
        return frameOf(
            g, px, py, d1, d2, end,
            logoAlpha = Curves.Standard.transform(seg(t, s.logoIn)),
            trackAlpha = 1f,
            tagAlpha = 0f,
            tagTyped = 0f,
            cursorAlpha = 0f,
            nib1 = 0f,
            nib2 = 0f,
            home = homeSequence(t, s.HOME_START, s.HOME_STEP, s.HOME_DURATION),
        )
    }

    private fun frameOf(
        g: SplashGeometry,
        px: Float,
        py: Float,
        d1: Float,
        d2: Float,
        end: Float,
        logoAlpha: Float,
        trackAlpha: Float,
        tagAlpha: Float,
        tagTyped: Float,
        cursorAlpha: Float,
        nib1: Float,
        nib2: Float,
        home: List<Float>,
    ) = SplashFrame(
        stageAlpha = 1f,
        logoAlpha = logoAlpha,
        xCx = g.xCx,
        xCy = g.xCy,
        xHalf = g.xHalf,
        xStroke = g.xStroke,
        stroke1 = clamp01(d1),
        stroke2 = clamp01(d2),
        // Две разные пружины по X и Y дают путь дугой; радиус и толщина идут за Y.
        ringCx = lerp(g.oCx, g.target.cx, px),
        ringCy = lerp(g.oCy, g.target.cy, py),
        ringRadius = max(0f, lerp(g.oRadius, g.target.radius, py)),
        ringStroke = lerp(g.oStroke, g.target.stroke, clamp01(py)),
        arcStart = 0f,
        arcEnd = end,
        trackAlpha = trackAlpha,
        tagAlpha = tagAlpha,
        tagTyped = tagTyped,
        cursorAlpha = cursorAlpha,
        nib1 = nib1,
        nib2 = nib2,
        home = home,
    )

    private fun homeSequence(t: Float, start: Float, step: Float, duration: Float): List<Float> =
        List(HOME_ITEMS) { i ->
            val t0 = start + i * step
            Curves.EmphasizedDecelerate.transform(seg(t, t0..(t0 + duration)))
        }
}

/**
 * Правило выбора заставки. Решение владельца (2026-11-09, DECISIONS.md Р19): при каждом холодном запуске
 * показывается полная заставка, без укороченной версии. Короткий сценарий остаётся только для отладки.
 */
object SplashPolicy {
    fun decide(): SplashMode = SplashMode.Full
}
