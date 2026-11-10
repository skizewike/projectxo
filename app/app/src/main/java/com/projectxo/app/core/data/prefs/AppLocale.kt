package com.projectxo.app.core.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.projectxo.app.core.model.AppLanguage
import java.util.Locale

/** Язык интерфейса, выбранный в приложении (Р62). «По телефону» ничего не меняет. */
object AppLocale {
    fun saved(context: Context): AppLanguage {
        val name = context.getSharedPreferences(PrefsSettingsRepository.FILE, Context.MODE_PRIVATE).getString("language", null)
        return AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.System
    }

    private fun locale(l: AppLanguage): Locale? = when (l) {
        AppLanguage.System -> null
        AppLanguage.Ru -> Locale.forLanguageTag("ru")
        AppLanguage.En -> Locale.forLanguageTag("en")
    }

    /** Контекст с нужным языком. Для «По телефону» возвращается тот же контекст. */
    fun wrap(base: Context, language: AppLanguage = saved(base)): Context {
        val loc = locale(language) ?: return base
        val cfg = Configuration(base.resources.configuration).apply { setLocale(loc) }
        return base.createConfigurationContext(cfg)
    }

    /** Ресурсы для приложения целиком (уведомления, виджет). null — без подмены. */
    fun resources(base: Context, language: AppLanguage): Resources? = if (language == AppLanguage.System) null else wrap(base, language).resources
}
