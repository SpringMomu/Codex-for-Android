package dev.codex.android.feature.chat

import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.data.model.ChatActivity
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.MessageRole
import dev.codex.android.data.remote.OpenAiCompatService
import dev.codex.android.data.repository.ConversationRepository
import dev.codex.android.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ActiveChatStream(
    val conversationId: Long,
    val assistantMessageId: Long,
)

data class SendMessageResult(
    val conversationId: Long,
    val createdConversationId: Long?,
)

class ChatStreamCoordinator(
    private val applicationScope: CoroutineScope,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val openAiCompatService: OpenAiCompatService,
    private val appStrings: AppStrings,
) {
    private val requestMutex = Mutex()
    private val activeStream = MutableStateFlow<ActiveChatStream?>(null)

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

    private fun startStream(
        conversationId: Long,
        assistantMessageId: Long,
        history: List<ChatMessage>,
    ) {
        activeStream.value = ActiveChatStream(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId,
        )
        applicationScope.launch {
            try {
                streamAssistantReply(
                    history = history,
                    assistantMessageId = assistantMessageId,
                )
            } finally {
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
        ) { event ->
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
        }

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
