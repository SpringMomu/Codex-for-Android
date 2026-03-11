package dev.codex.android.core.i18n

import androidx.core.os.LocaleListCompat

enum class AppLanguage(
    val storageValue: String,
    val localeTag: String?,
) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    SIMPLIFIED_CHINESE("zh-CN", "zh-CN"),
    ;

    fun toLocaleListCompat(): LocaleListCompat = localeTag?.let(LocaleListCompat::forLanguageTags)
        ?: LocaleListCompat.getEmptyLocaleList()

    companion object {
        fun fromStorage(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
