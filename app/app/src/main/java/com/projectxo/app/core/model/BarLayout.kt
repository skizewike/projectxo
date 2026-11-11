package com.projectxo.app.core.model

/**
 * Какие модули стоят в нижней панели (Р38, Р76). «Сегодня» первая и «Ещё» последняя, их менять нельзя.
 * Если включённых модулей не больше четырёх, все они во вкладках. Если больше, во вкладках три закреплённых,
 * остальные в «Ещё». Закреплённые выбирает пользователь; пока не выбрал, это вода, еда и тренировки.
 */
object BarLayout {
    /** Модулей во вкладках, когда есть «Ещё». */
    const val PINNED_SLOTS = 3

    /** Всего вкладок-модулей, когда «Ещё» не нужно. */
    const val MAX_MODULE_TABS = 4

    val DEFAULT_PINS: List<Module> = listOf(Module.Water, Module.Food, Module.Workouts)

    data class Result(val bar: List<Module>, val overflow: List<Module>) {
        val hasMore: Boolean get() = overflow.isNotEmpty()
    }

    /** Сколько мест можно менять: все вкладки, если модулей немного, иначе три закреплённых. */
    fun slots(enabledCount: Int): Int = if (enabledCount <= MAX_MODULE_TABS) enabledCount else PINNED_SLOTS

    /** Модули во вкладках по порядку: выбор пользователя (или по умолчанию) только из включённых, остальное по порядку модулей. */
    fun pinned(modules: Set<Module>, order: List<Module>, pins: List<Module>?): List<Module> {
        val enabled = order.filter { it in modules }
        val n = slots(enabled.size)
        val wanted = (pins ?: DEFAULT_PINS).filter { it in enabled }.distinct().take(n)
        return (wanted + enabled.filterNot { it in wanted }).take(n)
    }

    fun resolve(modules: Set<Module>, order: List<Module>, pins: List<Module>?): Result {
        val enabled = order.filter { it in modules }
        val bar = pinned(modules, order, pins)
        return Result(bar, enabled.filterNot { it in bar })
    }

    /**
     * Поставить [m] на место [slot] (0..2). Если [m] уже стоит в другом месте, они меняются местами.
     * Возвращает новый список закреплённых.
     */
    fun withSlot(current: List<Module>, slot: Int, m: Module): List<Module> {
        if (slot !in current.indices) return current
        val list = current.toMutableList()
        val from = list.indexOf(m)
        if (from >= 0) {
            list[from] = list[slot]
        }
        list[slot] = m
        return list
    }
}
