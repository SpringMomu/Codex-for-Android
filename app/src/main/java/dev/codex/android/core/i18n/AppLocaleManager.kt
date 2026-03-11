package dev.codex.android.core.i18n

import androidx.appcompat.app.AppCompatDelegate

class AppLocaleManager {
    fun apply(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(AppLanguage.fromStorage(languageTag).toLocaleListCompat())
    }
}
