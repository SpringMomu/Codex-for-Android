package dev.codex.android.feature.chat

import android.content.Context
import dev.codex.android.app.StreamingForegroundService
import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.data.model.ChatActivity
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.MessageRole
import dev.codex.android.data.remote.OpenAiCompatService
import dev.codex.android.data.repository.ConversationRepository
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

data class ActiveChatStream(
    val conversationId: Long,
    val assistantMessageId: Long,
)

data class SendMessageResult(
    val conversationId: Long,
    val createdConversationId: Long?,
)

class ChatStreamCoordinator(
    private val appContext: Context,
    private val applicationScope: CoroutineScope,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val openAiCompatService: OpenAiCompatService,
    private val appStrings: AppStrings,
) {
    private val requestMutex = Mutex()
    private val activeStream = MutableStateFlow<ActiveChatStream?>(null)
    @Volatile
    private var activeStreamJob: Job? = null
    @Volatile
    private var activeStreamCall: Call? = null
    @Volatile
    private var stoppedAssistantMessageId: Long? = null

    val activeStreamState: StateFlow<ActiveChatStream?> = activeStream.asStateFlow()

    suspend fun sendMessage(
        activeConversationId: Long?,
        prompt: String,
        imagePaths: List<String>,
    ): SendMessageResult? = requestMutex.withLock {
        if (activeStream.value != null) return null

        var createdConversationId: Long? = null
        val existingConversationId = activeConversationId?.takeIf {
            conversationRepository.conversationExists(it)
        }
        val conversationId = existingConversationId ?: conversationRepository.createConversation(
            firstPrompt = prompt,
            imageCount = imagePaths.size,
        ).also { createdId ->
            createdConversationId = createdId
        }

        conversationRepository.addMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = prompt,
            imagePaths = imagePaths,
        )

        val history = conversationRepository.getMessages(conversationId)
        val assistantMessageId = conversationRepository.addMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            reasoningSummary = "",
            activityLog = emptyList(),
            webSearchState = "",
        )

        startStream(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
            history = history,
        )

        SendMessageResult(
            conversationId = conversationId,
            createdConversationId = createdConversationId,
        )
    }

    suspend fun retryFailedMessage(messageId: Long): Boolean = requestMutex.withLock {
        if (activeStream.value != null) return false

        val targetMessage = conversationRepository.getMessage(messageId) ?: return false
        val conversationId = targetMessage.conversationId ?: return false
        val history = conversationRepository.getMessages(conversationId)
        val targetIndex = history.indexOfFirst { it.id == messageId }
        if (targetIndex == -1) return false

        val latestMessage = history.lastOrNull()
        if (latestMessage?.id != messageId ||
            targetMessage.role != MessageRole.ASSISTANT ||
            !targetMessage.isError
        ) {
            return false
        }

        conversationRepository.updateStreamingMessage(
            messageId = messageId,
            content = "",
            reasoningSummary = "",
            activityLog = emptyList(),
            webSearchState = "",
            isError = false,
        )

        startStream(
            conversationId = conversationId,
            assistantMessageId = messageId,
            history = history.take(targetIndex),
        )
        true
    }

    fun stopActiveStream() {
        stopActiveStreamInternal()
    }

    private fun startStream(
        conversationId: Long,
        assistantMessageId: Long,
        history: List<ChatMessage>,
    ) {
        stoppedAssistantMessageId = null
        activeStream.value = ActiveChatStream(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
        )
        runCatching {
            StreamingForegroundService.start(appContext)
        }
        activeStreamJob = applicationScope.launch {
            try {
                streamAssistantReply(
                    history = history,
                    assistantMessageId = assistantMessageId,
                )
            } finally {
                runCatching {
                    StreamingForegroundService.stop(appContext)
                }
                activeStreamCall = null
                activeStreamJob = null
                activeStream.update { current ->
                    if (current?.assistantMessageId == assistantMessageId) {
                        null
                    } else {
                        current
                    }
                }
            }
        }
    }

    private suspend fun streamAssistantReply(
        history: List<ChatMessage>,
        assistantMessageId: Long,
    ) {
        val settings = settingsRepository.currentSettings()
        var streamedText = ""
        var streamedReasoningSummary = ""
        var streamedWebSearchState = ""
        var activityLog = emptyList<ChatActivity>()
        var lastStreamingUiCommitAt = 0L

        suspend fun pushStreamingState(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastStreamingUiCommitAt < STREAMING_UI_COMMIT_INTERVAL_MS) {
                return
            }
            lastStreamingUiCommitAt = now
            conversationRepository.updateStreamingMessage(
                messageId = assistantMessageId,
                content = streamedText,
                reasoningSummary = streamedReasoningSummary,
                activityLog = activityLog,
                webSearchState = streamedWebSearchState,
            )
        }

        val result = openAiCompatService.streamAssistantReply(
            settings = settings,
            history = history,
            onEvent = { event ->
                when (event) {
                    is OpenAiCompatService.StreamEvent.TextDelta -> {
                        streamedText += event.delta
                        pushStreamingState()
                    }

                    is OpenAiCompatService.StreamEvent.ReasoningSummaryDelta -> {
                        streamedReasoningSummary += event.delta
                        pushStreamingState()
                    }

                    is OpenAiCompatService.StreamEvent.WebSearchStateChanged -> {
                        streamedWebSearchState = event.state
                        activityLog = when (event.state) {
                            OpenAiCompatService.WebSearchState.SEARCHING -> {
                                activityLog + ChatActivity(
                                    label = "search",
                                    status = ACTIVITY_RUNNING,
                                )
                            }

                            OpenAiCompatService.WebSearchState.COMPLETED -> {
                                markLatestStepCompleted(activityLog, "search")
                            }

                            else -> activityLog
                        }
                        pushStreamingState(force = true)
                    }

                    is OpenAiCompatService.StreamEvent.Completed -> {
                        streamedText = event.reply.text
                        streamedReasoningSummary = event.reply.reasoningSummary
                        pushStreamingState(force = true)
                    }
                }
            },
            onCallCreated = { call -> activeStreamCall = call },
        )

        result.fold(
            onSuccess = {
                if (streamedText.isBlank() && streamedReasoningSummary.isNotBlank()) {
                    conversationRepository.updateStreamingMessage(
                        messageId = assistantMessageId,
                        content = " ",
                        reasoningSummary = streamedReasoningSummary,
                        activityLog = activityLog,
                        webSearchState = streamedWebSearchState,
                    )
                }
            },
            onFailure = { throwable ->
                if (shouldTreatAsStopped(assistantMessageId, throwable)) {
                    conversationRepository.updateStreamingMessage(
                        messageId = assistantMessageId,
                        content = streamedText,
                        reasoningSummary = streamedReasoningSummary,
                        activityLog = activityLog,
                        webSearchState = streamedWebSearchState,
                        isError = false,
                    )
                    return@fold
                }
                if (shouldAttemptRecovery(assistantMessageId, throwable)) {
                    val recovered = recoverAbortedStream(
                        settings = settings,
                        history = history,
                        assistantMessageId = assistantMessageId,
                        streamedText = streamedText,
                        streamedReasoningSummary = streamedReasoningSummary,
                        activityLog = activityLog,
                        streamedWebSearchState = streamedWebSearchState,
                    )
                    if (recovered) {
                        return@fold
                    }
                }
                val errorText = throwable.message
                    ?.takeIf { it.isNotBlank() }
                    ?: throwable::class.java.simpleName
                    ?: appStrings.errorRequestFailedUnknown(settings.languageTag)
                conversationRepository.updateStreamingMessage(
                    messageId = assistantMessageId,
                    content = if (streamedText.isNotBlank()) streamedText else errorText,
                    reasoningSummary = streamedReasoningSummary,
                    activityLog = activityLog,
                    webSearchState = streamedWebSearchState,
                    isError = true,
                )
            },
        )
    }

    private fun shouldTreatAsStopped(
        assistantMessageId: Long,
        throwable: Throwable,
    ): Boolean {
        val stoppedThisMessage = stoppedAssistantMessageId == assistantMessageId
        val canceled = throwable is CancellationException ||
            throwable.message?.contains("Canceled", ignoreCase = true) == true ||
            throwable.message?.contains("Socket closed", ignoreCase = true) == true ||
            throwable.message?.contains("Software caused connection abort", ignoreCase = true) == true
        return stoppedThisMessage && canceled
    }

    private fun shouldAttemptRecovery(
        assistantMessageId: Long,
        throwable: Throwable,
    ): Boolean {
        if (stoppedAssistantMessageId == assistantMessageId) return false
        val message = throwable.message.orEmpty()
        return message.contains("Software caused connection abort", ignoreCase = true) ||
            message.contains("Connection reset", ignoreCase = true) ||
            message.contains("Broken pipe", ignoreCase = true)
    }

    private suspend fun recoverAbortedStream(
        settings: dev.codex.android.data.model.AppSettings,
        history: List<ChatMessage>,
        assistantMessageId: Long,
        streamedText: String,
        streamedReasoningSummary: String,
        activityLog: List<ChatActivity>,
        streamedWebSearchState: String,
    ): Boolean {
        val resumeHistory = history + ChatMessage(
            id = assistantMessageId,
            role = MessageRole.ASSISTANT,
            content = streamedText,
            reasoningSummary = streamedReasoningSummary,
            activityLog = activityLog,
            webSearchState = streamedWebSearchState,
            createdAt = System.currentTimeMillis(),
        )

        val recovery = openAiCompatService.createAssistantReply(
            settings = settings,
            history = resumeHistory,
        )

        return recovery.fold(
            onSuccess = { reply ->
                conversationRepository.updateStreamingMessage(
                    messageId = assistantMessageId,
                    content = mergeRecoveredText(streamedText, reply.text),
                    reasoningSummary = mergeRecoveredText(streamedReasoningSummary, reply.reasoningSummary),
                    activityLog = activityLog,
                    webSearchState = streamedWebSearchState,
                    isError = false,
                )
                true
            },
            onFailure = {
                false
            },
        )
    }

    private fun mergeRecoveredText(
        existing: String,
        incoming: String,
    ): String {
        if (existing.isBlank()) return incoming
        if (incoming.isBlank()) return existing
        if (incoming.startsWith(existing)) return incoming
        if (existing.startsWith(incoming)) return existing

        val maxOverlap = minOf(existing.length, incoming.length)
        val overlap = (maxOverlap downTo 1).firstOrNull { size ->
            existing.takeLast(size) == incoming.take(size)
        } ?: 0
        return existing + incoming.drop(overlap)
    }

    private fun stopActiveStreamInternal() {
        val current = activeStream.value ?: return
        stoppedAssistantMessageId = current.assistantMessageId
        activeStreamCall?.cancel()
        activeStreamJob?.cancel()
    }
}

private fun markLatestStepCompleted(
    current: List<ChatActivity>,
    label: String,
): List<ChatActivity> {
    val targetIndex = current.indexOfLast { it.label == label && it.status == ACTIVITY_RUNNING }
    if (targetIndex == -1) return current

    return current.mapIndexed { index, item ->
        if (index == targetIndex) {
            item.copy(status = ACTIVITY_COMPLETED)
        } else {
            item
        }
    }
}

private const val STREAMING_UI_COMMIT_INTERVAL_MS = 80L

private const val ACTIVITY_RUNNING = "running"
private const val ACTIVITY_COMPLETED = "completed"
