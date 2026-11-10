package com.projectxo.app.core.data

import com.projectxo.app.core.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

/** Настройки и профиль (Р55). Чтение мгновенное: нужно ещё до первого кадра, чтобы выбрать тему. */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}
