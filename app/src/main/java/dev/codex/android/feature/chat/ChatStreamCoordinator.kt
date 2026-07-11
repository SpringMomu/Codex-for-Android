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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

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
    private val activeStreams = MutableStateFlow<Map<Long, ActiveChatStream>>(emptyMap())
    private val activeStreamJobs = ConcurrentHashMap<Long, Job>()
    private val activeStreamCalls = ConcurrentHashMap<Long, Call>()
    private val stoppedAssistantMessageIds = ConcurrentHashMap.newKeySet<Long>()

    val activeStreamsState: StateFlow<Map<Long, ActiveChatStream>> = activeStreams.asStateFlow()

    fun isConversationStreaming(conversationId: Long?): Boolean {
        return conversationId != null && activeStreams.value.containsKey(conversationId)
    }

    suspend fun sendMessage(
        activeConversationId: Long?,
        prompt: String,
        imagePaths: List<String>,
    ): SendMessageResult? = requestMutex.withLock {
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
        if (activeStreams.value.containsKey(conversationId)) return null

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
        val targetMessage = conversationRepository.getMessage(messageId) ?: return false
        val conversationId = targetMessage.conversationId ?: return false
        if (activeStreams.value.containsKey(conversationId)) return false

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

    suspend fun generateReplyFromMessage(messageId: Long): Boolean = requestMutex.withLock {
        val targetMessage = conversationRepository.getMessage(messageId) ?: return false
        val conversationId = targetMessage.conversationId ?: return false
        if (activeStreams.value.containsKey(conversationId)) return false

        val history = conversationRepository.getMessages(conversationId)
        val targetIndex = history.indexOfFirst { it.id == messageId }
        if (targetIndex == -1) return false

        val historyForReply = when (history[targetIndex].role) {
            MessageRole.USER -> history.take(targetIndex + 1)
            MessageRole.ASSISTANT -> history.take(targetIndex)
            MessageRole.SYSTEM -> return false
        }
        if (historyForReply.none { it.role == MessageRole.USER }) return false

        val messagesToDelete = when (history[targetIndex].role) {
            MessageRole.USER -> history.drop(targetIndex + 1)
            MessageRole.ASSISTANT -> history.drop(targetIndex)
            MessageRole.SYSTEM -> emptyList()
        }
        if (messagesToDelete.isNotEmpty()) {
            conversationRepository.deleteMessages(
                conversationId = conversationId,
                messages = messagesToDelete,
            )
        }

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
            history = historyForReply,
        )
        true
    }

    fun stopActiveStream() {
        activeStreams.value.keys.forEach { conversationId ->
            stopActiveStreamInternal(conversationId)
        }
    }

    fun stopActiveStream(conversationId: Long?) {
        stopActiveStreamInternal(conversationId)
    }

    private fun startStream(
        conversationId: Long,
        assistantMessageId: Long,
        history: List<ChatMessage>,
    ) {
        stoppedAssistantMessageIds.remove(assistantMessageId)
        activeStreams.update { current ->
            current + (conversationId to ActiveChatStream(
                conversationId = conversationId,
                assistantMessageId = assistantMessageId,
            ))
        }
        runCatching {
            StreamingForegroundService.start(appContext)
        }
        val streamJob = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                streamAssistantReply(
                    conversationId = conversationId,
                    history = history,
                    assistantMessageId = assistantMessageId,
                )
            } finally {
                activeStreamCalls.remove(conversationId)
                activeStreamJobs.remove(conversationId)
                stoppedAssistantMessageIds.remove(assistantMessageId)
                activeStreams.update { current ->
                    if (current[conversationId]?.assistantMessageId == assistantMessageId) {
                        current - conversationId
                    } else {
                        current
                    }
                }
                if (activeStreams.value.isEmpty()) {
                    runCatching {
                        StreamingForegroundService.stop(appContext)
                    }
                }
            }
        }
        activeStreamJobs[conversationId] = streamJob
        streamJob.start()
    }

    private suspend fun streamAssistantReply(
        conversationId: Long,
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
                                incrementActivityCount(activityLog, "search")
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
            onCallCreated = { call ->
                activeStreamCalls[conversationId] = call
                if (stoppedAssistantMessageIds.contains(assistantMessageId)) {
                    call.cancel()
                }
            },
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
                        conversationId = conversationId,
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
        val stoppedThisMessage = stoppedAssistantMessageIds.contains(assistantMessageId)
        val canceled = throwable is CancellationException || throwable.causes().any { cause ->
            val message = cause.message.orEmpty()
            message.contains("Canceled", ignoreCase = true) ||
                message.contains("Socket closed", ignoreCase = true) ||
                message.contains("Software caused connection abort", ignoreCase = true)
        }
        return stoppedThisMessage && canceled
    }

    private fun shouldAttemptRecovery(
        assistantMessageId: Long,
        throwable: Throwable,
    ): Boolean {
        if (stoppedAssistantMessageIds.contains(assistantMessageId)) return false
        return throwable.causes().any { cause ->
            val message = cause.message.orEmpty()
            cause is EOFException ||
                cause is SocketException ||
                cause is SocketTimeoutException ||
                cause is IOException && (
                    message.contains("connection", ignoreCase = true) ||
                        message.contains("socket", ignoreCase = true) ||
                        message.contains("stream", ignoreCase = true) ||
                        message.contains("unexpected end", ignoreCase = true)
                    ) ||
                message.contains("Software caused connection abort", ignoreCase = true) ||
                message.contains("Connection reset", ignoreCase = true) ||
                message.contains("Broken pipe", ignoreCase = true)
        }
    }

    private suspend fun recoverAbortedStream(
        conversationId: Long,
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

        repeat(ABORTED_STREAM_RECOVERY_ATTEMPTS) { attempt ->
            if (stoppedAssistantMessageIds.contains(assistantMessageId)) return false
            if (attempt > 0) {
                delay(RECOVERY_RETRY_BASE_DELAY_MS * attempt)
            }

            val recovery = openAiCompatService.createAssistantReply(
                settings = settings,
                history = resumeHistory,
                onCallCreated = { call ->
                    activeStreamCalls[conversationId] = call
                    if (stoppedAssistantMessageIds.contains(assistantMessageId)) {
                        call.cancel()
                    }
                },
            )
            recovery.onSuccess { reply ->
                conversationRepository.updateStreamingMessage(
                    messageId = assistantMessageId,
                    content = mergeRecoveredText(streamedText, reply.text),
                    reasoningSummary = mergeRecoveredText(streamedReasoningSummary, reply.reasoningSummary),
                    activityLog = activityLog,
                    webSearchState = streamedWebSearchState,
                    isError = false,
                )
                return true
            }
            val recoveryError = recovery.exceptionOrNull() ?: return false
            if (!shouldAttemptRecovery(assistantMessageId, recoveryError)) return false
        }
        return false
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

    private fun stopActiveStreamInternal(conversationId: Long?) {
        if (conversationId == null) return
        val current = activeStreams.value[conversationId] ?: return
        stoppedAssistantMessageIds.add(current.assistantMessageId)
        activeStreamCalls[conversationId]?.cancel()
        activeStreamJobs[conversationId]?.cancel()
    }
}

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { current ->
    current.cause?.takeUnless { it === current }
}.take(MAX_CAUSE_DEPTH)

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

private fun incrementActivityCount(
    current: List<ChatActivity>,
    label: String,
): List<ChatActivity> {
    val targetIndex = current.indexOfLast { it.label == label }
    if (targetIndex == -1) {
        return current + ChatActivity(
            label = label,
            status = ACTIVITY_RUNNING,
            count = 1,
        )
    }

    return current.mapIndexed { index, item ->
        if (index == targetIndex) {
            item.copy(
                status = ACTIVITY_RUNNING,
                count = item.count.coerceAtLeast(1) + 1,
            )
        } else {
            item
        }
    }
}

private const val STREAMING_UI_COMMIT_INTERVAL_MS = 80L
private const val ABORTED_STREAM_RECOVERY_ATTEMPTS = 3
private const val RECOVERY_RETRY_BASE_DELAY_MS = 750L
private const val MAX_CAUSE_DEPTH = 8

private const val ACTIVITY_RUNNING = "running"
private const val ACTIVITY_COMPLETED = "completed"
