package dev.codex.android.feature.image

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.ImageGeneration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImageGenerationUiState(
    val generations: List<ImageGeneration> = emptyList(),
    val activeGenerationIds: Set<Long> = emptySet(),
    val hasCredentials: Boolean = false,
    val baseUrl: String = "",
)

class ImageGenerationViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var prompt by mutableStateOf("")
        private set

    var referenceImagePath by mutableStateOf<String?>(null)
        private set

    var isImportingReferenceImage by mutableStateOf(false)
        private set

    val saveResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    val uiState: StateFlow<ImageGenerationUiState> = combine(
        container.imageGenerationRepository.observeGenerations(),
        container.imageGenerationCoordinator.activeGenerationIds,
        container.settingsRepository.settings,
    ) { generations, activeIds, settings ->
        ImageGenerationUiState(
            generations = generations,
            activeGenerationIds = activeIds,
            hasCredentials = settings.apiKey.isNotBlank(),
            baseUrl = settings.baseUrl,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImageGenerationUiState(),
    )

    fun updatePrompt(value: String) {
        prompt = value
    }

    fun importReferenceImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            isImportingReferenceImage = true
            val previousPath = referenceImagePath
            val importedPath = container.imageGenerationRepository.importReferenceImage(uri)
            if (importedPath != null) {
                referenceImagePath = importedPath
                previousPath?.let { container.imageGenerationRepository.deleteLocalAttachment(it) }
            }
            isImportingReferenceImage = false
        }
    }

    fun removeReferenceImage() {
        val path = referenceImagePath ?: return
        viewModelScope.launch {
            referenceImagePath = null
            container.imageGenerationRepository.deleteLocalAttachment(path)
        }
    }

    fun generate() {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank()) return
        val currentReference = referenceImagePath
        viewModelScope.launch {
            container.imageGenerationCoordinator.enqueue(
                prompt = trimmedPrompt,
                referenceImagePath = currentReference,
            )
            prompt = ""
            referenceImagePath = null
        }
    }

    fun retry(id: Long) {
        viewModelScope.launch {
            container.imageGenerationCoordinator.retry(id)
        }
    }

    fun stop(id: Long) {
        container.imageGenerationCoordinator.stop(id)
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            container.imageGenerationRepository.deleteGeneration(id)
        }
    }

    fun saveToGallery(path: String) {
        viewModelScope.launch {
            saveResult.tryEmit(container.imageGenerationRepository.saveImageToGallery(path) != null)
        }
    }
}

fun imageGenerationViewModelFactory(
    container: AppContainer,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ImageGenerationViewModel(container) as T
}
