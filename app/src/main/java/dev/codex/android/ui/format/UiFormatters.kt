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

@Composable
fun formatElapsedDuration(elapsedMillis: Long): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()

    return remember(elapsedMillis, locale) {
        val totalSeconds = (elapsedMillis.coerceAtLeast(0L) + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        if (locale.language == "zh") {
            when {
                hours > 0 -> "%d小时%02d分%02d秒".format(locale, hours, minutes, seconds)
                minutes > 0 -> "%d分%02d秒".format(locale, minutes, seconds)
                else -> "%d秒".format(locale, seconds)
            }
        } else {
            when {
                hours > 0 -> "%dh %02dm %02ds".format(locale, hours, minutes, seconds)
                minutes > 0 -> "%dm %02ds".format(locale, minutes, seconds)
                else -> "%ds".format(locale, seconds)
            }
        }
    }
}
