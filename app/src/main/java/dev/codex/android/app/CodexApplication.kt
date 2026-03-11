package dev.codex.android.app

import android.app.Application
import dev.codex.android.core.di.AppContainer
import kotlinx.coroutines.runBlocking

class CodexApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            container.appLocaleManager.apply(container.settingsRepository.currentLanguageTag())
        }
    }
}
