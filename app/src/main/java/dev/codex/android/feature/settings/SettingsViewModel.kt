package dev.codex.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaving: Boolean = false,
    val lastSavedAt: Long? = null,
)

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val isSaving = MutableStateFlow(false)
    private val lastSavedAt = MutableStateFlow<Long?>(null)

    val uiState = combine(
        container.settingsRepository.settings,
        isSaving,
        lastSavedAt,
    ) { settings, saving, savedAt ->
        SettingsUiState(
            settings = settings,
            isSaving = saving,
            lastSavedAt = savedAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun save(settings: AppSettings) {
        viewModelScope.launch {
            isSaving.value = true
            container.settingsRepository.save(settings)
            container.appLocaleManager.apply(settings.languageTag)
            lastSavedAt.value = System.currentTimeMillis()
            isSaving.value = false
        }
    }
}

fun settingsViewModelFactory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container) as T
    }
}
