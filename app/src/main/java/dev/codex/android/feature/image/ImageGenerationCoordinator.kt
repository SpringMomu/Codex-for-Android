package dev.codex.android.feature.image

import android.content.Context
import dev.codex.android.app.StreamingForegroundService
import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.data.model.ImageGeneration
import dev.codex.android.data.model.ImageGenerationStatus
import dev.codex.android.data.remote.OpenAiCompatService
import dev.codex.android.data.repository.ImageGenerationRepository
import dev.codex.android.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call

class ImageGenerationCoordinator(
    private val appContext: Context,
    private val applicationScope: CoroutineScope,
    private val repository: ImageGenerationRepository,
    private val settingsRepository: SettingsRepository,
    private val openAiCompatService: OpenAiCompatService,
    private val appStrings: AppStrings,
) {
    private val mutex = Mutex()
    private val activeIds = MutableStateFlow<Set<Long>>(emptySet())
    private val jobs = mutableMapOf<Long, Job>()
    private val calls = mutableMapOf<Long, Call>()

    val activeGenerationIds: StateFlow<Set<Long>> = activeIds.asStateFlow()

    init {
        applicationScope.launch {
            repository.getPendingGenerations().forEach { generation ->
                startGeneration(generation)
            }
        }
    }

    suspend fun enqueue(
        prompt: String,
        referenceImagePath: String?,
    ): Long = mutex.withLock {
        val id = repository.createGeneration(
            prompt = prompt,
            referenceImagePath = referenceImagePath,
        )
        repository.getGeneration(id)?.let(::startGeneration)
        id
    }

    suspend fun retry(id: Long): Boolean = mutex.withLock {
        if (activeIds.value.contains(id)) return false
        if (!repository.retry(id)) return false
        repository.getGeneration(id)?.let(::startGeneration)
        true
    }

    fun stop(id: Long) {
        calls[id]?.cancel()
        jobs[id]?.cancel()
    }

    private fun startGeneration(generation: ImageGeneration) {
        if (generation.status == ImageGenerationStatus.SUCCEEDED || activeIds.value.contains(generation.id)) return

        activeIds.update { it + generation.id }
        runCatching { StreamingForegroundService.start(appContext) }
        jobs[generation.id] = applicationScope.launch {
            try {
                runGeneration(generation)
            } finally {
                calls.remove(generation.id)
                jobs.remove(generation.id)
                activeIds.update { it - generation.id }
                if (activeIds.value.isEmpty()) {
                    runCatching { StreamingForegroundService.stop(appContext) }
                }
            }
        }
    }

    private suspend fun runGeneration(generation: ImageGeneration) {
        repository.markRunning(generation.id)
        val settings = settingsRepository.currentSettings()
        val result = openAiCompatService.generateImage(
            settings = settings,
            prompt = generation.prompt,
            referenceImagePath = generation.referenceImagePath,
            onCallCreated = { call -> calls[generation.id] = call },
        )
        result.fold(
            onSuccess = { imageBase64 ->
                repository.markSucceeded(generation.id, imageBase64)
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) {
                    repository.markFailed(generation.id, "Canceled")
                } else {
                    repository.markFailed(
                        id = generation.id,
                        errorMessage = throwable.message?.takeIf { it.isNotBlank() }
                            ?: appStrings.errorRequestFailedUnknown(settings.languageTag),
                    )
                }
            },
        )
    }
}
