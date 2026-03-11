package dev.codex.android.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun formatTimestamp(epochMillis: Long): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(
            if (locale.language == "zh") "M月d日 HH:mm" else "MMM d, HH:mm",
            locale,
        )
    }

    return remember(epochMillis, locale) {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
}
