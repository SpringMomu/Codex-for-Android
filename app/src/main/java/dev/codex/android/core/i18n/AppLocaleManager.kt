package dev.codex.android.core.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class AppLocaleManager {
    fun apply(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(AppLanguage.fromStorage(languageTag).toLocaleListCompat())
    }

    fun localizedContext(
        context: Context,
        languageTag: String,
    ): Context {
        val language = AppLanguage.fromStorage(languageTag)
        val localeTag = language.localeTag ?: return context
        val locale = Locale.forLanguageTag(localeTag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
