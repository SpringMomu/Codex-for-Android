package dev.codex.android.feature.chat

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.ConversationScrollPosition
import dev.codex.android.data.model.MessageRole
import dev.codex.android.core.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val title: String = "",
    val activeConversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val streamingMessageId: Long? = null,
    val baseUrl: String = "",
    val modelAlias: String = "",
    val reasoningEffort: String = "",
    val hasCredentials: Boolean = false,
    val savedScrollPosition: ConversationScrollPosition? = null,
)

class ChatViewModel(
    initialConversationId: Long?,
    private val container: AppContainer,
) : ViewModel() {
    var draft by mutableStateOf("")
        private set

    var selectedImagePaths by mutableStateOf<List<String>>(emptyList())
        private set

    private val activeConversationId = MutableStateFlow(initialConversationId)

    val createdConversation = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messages = activeConversationId.flatMapLatest { conversationId ->
        if (conversationId == null) {
            flowOf(emptyList())
        } else {
            container.conversationRepository.observeMessages(conversationId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val savedScrollPosition = activeConversationId.flatMapLatest { conversationId ->
        if (conversationId == null) {
            flowOf<ConversationScrollPosition?>(null)
        } else {
            flow {
                emit(container.settingsRepository.getConversationScrollPosition(conversationId))
            }
        }
    }

    val uiState = combine(
        activeConversationId,
        messages,
        container.chatStreamCoordinator.activeStreamState,
        container.settingsRepository.settings,
        savedScrollPosition,
    ) { conversationId, messageList, activeStream, settings, scrollPosition ->
        ChatUiState(
            title = messageList.firstOrNull { it.role == MessageRole.USER }?.content?.take(36).orEmpty()
                .ifBlank { "" },
            activeConversationId = conversationId,
            messages = messageList,
            isSending = activeStream != null,
            streamingMessageId = activeStream
                ?.takeIf { it.conversationId == conversationId }
                ?.assistantMessageId,
            baseUrl = settings.baseUrl,
            modelAlias = settings.modelAlias,
            reasoningEffort = settings.reasoningEffort,
            hasCredentials = settings.apiKey.isNotBlank(),
            savedScrollPosition = scrollPosition,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    fun updateDraft(value: String) {
        draft = value
    }

    fun sendMessage() {
        val prompt = draft.trim()
        val imagePaths = selectedImagePaths
        if ((prompt.isBlank() && imagePaths.isEmpty()) || container.chatStreamCoordinator.activeStreamState.value != null) return
        viewModelScope.launch {
            val result = container.chatStreamCoordinator.sendMessage(
                activeConversationId = activeConversationId.value,
                prompt = prompt,
                imagePaths = imagePaths,
            ) ?: return@launch

            draft = ""
            selectedImagePaths = emptyList()
            activeConversationId.value = result.conversationId
            result.createdConversationId?.let(createdConversation::tryEmit)
        }
    }

    fun retryFailedMessage(messageId: Long) {
        if (container.chatStreamCoordinator.activeStreamState.value != null) return
        viewModelScope.launch {
            container.chatStreamCoordinator.retryFailedMessage(messageId)
        }
    }

    fun stopStreaming() {
        container.chatStreamCoordinator.stopActiveStream()
    }

    fun importSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = container.conversationRepository.importImages(uris)
            if (imported.isNotEmpty()) {
                selectedImagePaths = selectedImagePaths + imported
            }
        }
    }

    fun removeSelectedImage(path: String) {
        viewModelScope.launch {
            selectedImagePaths = selectedImagePaths - path
            container.conversationRepository.deleteLocalAttachments(listOf(path))
        }
    }

    fun updateMessage(
        messageId: Long,
        content: String,
    ) {
        viewModelScope.launch {
            container.conversationRepository.updateMessage(
                messageId = messageId,
                content = content,
            )
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            val conversationDeleted = container.conversationRepository.deleteMessage(messageId)
            if (conversationDeleted) {
                activeConversationId.value = null
            }
        }
    }

    fun persistScrollPosition(
        anchorMessageId: Long?,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        val conversationId = activeConversationId.value ?: return
        viewModelScope.launch {
            container.settingsRepository.saveConversationScrollPosition(
                conversationId = conversationId,
                position = ConversationScrollPosition(
                    anchorMessageId = anchorMessageId,
                    firstVisibleItemIndex = firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
                ),
            )
        }
    }
}

fun chatViewModelFactory(
    container: AppContainer,
    conversationId: Long?,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            initialConversationId = conversationId,
            container = container,
        ) as T
    }
}
