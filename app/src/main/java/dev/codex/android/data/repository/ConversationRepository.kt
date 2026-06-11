package dev.codex.android.data.repository

import dev.codex.android.data.local.AttachmentStorage
import dev.codex.android.data.local.ChatDao
import dev.codex.android.data.local.ConversationEntity
import dev.codex.android.data.local.MessageEntity
import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.data.model.ChatActivity
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.ConversationSummary
import dev.codex.android.data.model.MessageRole
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ConversationRepository(
    private val chatDao: ChatDao,
    private val attachmentStorage: AttachmentStorage,
    private val settingsRepository: SettingsRepository,
    private val appStrings: AppStrings,
) {
    private val json = Json

    fun observeConversations(query: String = ""): Flow<List<ConversationSummary>> {
        val source = if (query.isBlank()) {
            chatDao.observeConversations()
        } else {
            chatDao.observeConversationsByQuery(query.trim())
        }
        return source.map { items ->
            items.map { entity ->
                ConversationSummary(
                    id = entity.id,
                    title = entity.title,
                    preview = entity.lastPreview,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                )
            }
        }
    }

    fun observeAllConversations(): Flow<List<ConversationSummary>> = chatDao.observeConversations().map { items ->
        items.map { entity ->
            ConversationSummary(
                id = entity.id,
                title = entity.title,
                preview = entity.lastPreview,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
    }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> = chatDao.observeMessages(conversationId).map { items ->
        items.map { entity ->
            ChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                role = MessageRole.fromStorage(entity.role),
                content = entity.content,
                imagePaths = decodeImagePaths(entity.imagePaths),
                reasoningSummary = entity.reasoningSummary,
                activityLog = decodeActivityLog(entity.activityLog),
                webSearchState = entity.webSearchState,
                createdAt = entity.createdAt,
                isError = entity.isError,
            )
        }
    }

    suspend fun getMessages(conversationId: Long): List<ChatMessage> = chatDao.getMessages(conversationId).map { entity ->
        ChatMessage(
            id = entity.id,
            conversationId = entity.conversationId,
            role = MessageRole.fromStorage(entity.role),
            content = entity.content,
            imagePaths = decodeImagePaths(entity.imagePaths),
            reasoningSummary = entity.reasoningSummary,
            activityLog = decodeActivityLog(entity.activityLog),
            webSearchState = entity.webSearchState,
            createdAt = entity.createdAt,
            isError = entity.isError,
        )
    }

    suspend fun getMessage(messageId: Long): ChatMessage? = chatDao.getMessage(messageId)?.let { entity ->
        ChatMessage(
            id = entity.id,
            conversationId = entity.conversationId,
            role = MessageRole.fromStorage(entity.role),
            content = entity.content,
            imagePaths = decodeImagePaths(entity.imagePaths),
            reasoningSummary = entity.reasoningSummary,
            activityLog = decodeActivityLog(entity.activityLog),
            webSearchState = entity.webSearchState,
            createdAt = entity.createdAt,
            isError = entity.isError,
        )
    }

    suspend fun conversationExists(conversationId: Long): Boolean = chatDao.getConversation(conversationId) != null

    suspend fun importImages(uris: List<Uri>): List<String> = attachmentStorage.importImages(uris)

    suspend fun deleteLocalAttachments(paths: List<String>) {
        attachmentStorage.deleteAttachments(paths)
    }

    suspend fun createConversation(
        firstPrompt: String,
        imageCount: Int = 0,
    ): Long {
        val now = System.currentTimeMillis()
        val languageTag = settingsRepository.currentLanguageTag()
        return chatDao.insertConversation(
            ConversationEntity(
                title = titleFrom(firstPrompt, imageCount, languageTag),
                lastPreview = previewFrom(firstPrompt, imageCount, languageTag),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun addMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        imagePaths: List<String> = emptyList(),
        reasoningSummary: String = "",
        activityLog: List<ChatActivity> = emptyList(),
        webSearchState: String = "",
        isError: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        val messageId = chatDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = role.toStorage(),
                content = content,
                imagePaths = encodeImagePaths(imagePaths),
                reasoningSummary = reasoningSummary,
                activityLog = encodeActivityLog(activityLog),
                webSearchState = webSearchState,
                createdAt = now,
                isError = isError,
            ),
        )
        val conversation = chatDao.getConversation(conversationId)
        if (conversation != null) {
            val languageTag = settingsRepository.currentLanguageTag()
            chatDao.updateConversation(
                conversation.copy(
                    title = if (conversation.title == appStrings.defaultConversationTitle(languageTag) && role == MessageRole.USER) {
                        titleFrom(content, imagePaths.size, languageTag)
                    } else {
                        conversation.title
                    },
                    lastPreview = previewFrom(content, imagePaths.size, languageTag),
                    updatedAt = now,
                ),
            )
        }
        return messageId
    }

    suspend fun updateMessage(
        messageId: Long,
        content: String,
    ) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        val message = chatDao.getMessage(messageId) ?: return
        chatDao.updateMessage(
            message.copy(
                content = trimmed,
                isError = false,
            ),
        )
        refreshConversationSnapshot(message.conversationId)
    }

    suspend fun updateStreamingMessage(
        messageId: Long,
        content: String,
        reasoningSummary: String,
        activityLog: List<ChatActivity>,
        webSearchState: String,
        isError: Boolean = false,
    ) {
        val message = chatDao.getMessage(messageId) ?: return
        chatDao.updateMessage(
            message.copy(
                content = content,
                reasoningSummary = reasoningSummary,
                activityLog = encodeActivityLog(activityLog),
                webSearchState = webSearchState,
                isError = isError,
            ),
        )
        refreshConversationSnapshot(message.conversationId)
    }

    suspend fun deleteMessage(messageId: Long): Boolean {
        val message = chatDao.getMessage(messageId) ?: return false
        chatDao.deleteMessage(messageId)
        attachmentStorage.deleteAttachments(decodeImagePaths(message.imagePaths))
        return refreshConversationSnapshot(message.conversationId)
    }

    suspend fun deleteMessages(
        conversationId: Long,
        messages: List<ChatMessage>,
    ): Boolean {
        if (messages.isEmpty()) return false
        chatDao.deleteMessages(messages.map { it.id })
        attachmentStorage.deleteAttachments(messages.flatMap { it.imagePaths })
        return refreshConversationSnapshot(conversationId)
    }

    /**
     * Creates a new conversation that contains copies of every message from the start of the
     * source conversation up to and including [messageId]. Attachments are duplicated so the new
     * branch is fully independent of the original. Returns the new conversation id, or null when
     * the message could not be found.
     */
    suspend fun branchConversationFromMessage(messageId: Long): Long? {
        val target = chatDao.getMessage(messageId) ?: return null
        val sourceMessages = chatDao.getMessages(target.conversationId)
        val cutoff = sourceMessages.indexOfFirst { it.id == messageId }
        if (cutoff < 0) return null
        val branchMessages = sourceMessages.subList(0, cutoff + 1)
        if (branchMessages.isEmpty()) return null

        val now = System.currentTimeMillis()
        val languageTag = settingsRepository.currentLanguageTag()
        val firstUserMessage = branchMessages.firstOrNull {
            MessageRole.fromStorage(it.role) == MessageRole.USER
        }
        val lastMessage = branchMessages.last()
        val newConversationId = chatDao.insertConversation(
            ConversationEntity(
                title = titleFrom(
                    firstUserMessage?.content.orEmpty(),
                    firstUserMessage?.let { decodeImagePaths(it.imagePaths).size } ?: 0,
                    languageTag,
                ),
                lastPreview = previewFrom(
                    lastMessage.content,
                    decodeImagePaths(lastMessage.imagePaths).size,
                    languageTag,
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )

        branchMessages.forEach { entity ->
            val originalPaths = decodeImagePaths(entity.imagePaths)
            val copiedPaths = if (originalPaths.isEmpty()) {
                emptyList()
            } else {
                attachmentStorage.copyAttachments(originalPaths)
            }
            chatDao.insertMessage(
                entity.copy(
                    id = 0,
                    conversationId = newConversationId,
                    imagePaths = encodeImagePaths(copiedPaths),
                ),
            )
        }
        return newConversationId
    }

    suspend fun deleteConversation(conversationId: Long) {
        val attachments = chatDao.getMessages(conversationId).flatMap { decodeImagePaths(it.imagePaths) }
        chatDao.deleteConversation(conversationId)
        attachmentStorage.deleteAttachments(attachments)
        settingsRepository.clearConversationScrollPosition(conversationId)
    }

    private suspend fun refreshConversationSnapshot(conversationId: Long): Boolean {
        val conversation = chatDao.getConversation(conversationId) ?: return true
        val messages = chatDao.getMessages(conversationId)
        if (messages.isEmpty()) {
            chatDao.deleteConversation(conversationId)
            settingsRepository.clearConversationScrollPosition(conversationId)
            return true
        }

        val languageTag = settingsRepository.currentLanguageTag()
        val firstUserMessage = messages.firstOrNull { MessageRole.fromStorage(it.role) == MessageRole.USER }
        val lastMessage = messages.last()
        chatDao.updateConversation(
            conversation.copy(
                title = titleFrom(
                    firstUserMessage?.content.orEmpty(),
                    firstUserMessage?.let { decodeImagePaths(it.imagePaths).size } ?: 0,
                    languageTag,
                ),
                lastPreview = previewFrom(
                    lastMessage.content,
                    decodeImagePaths(lastMessage.imagePaths).size,
                    languageTag,
                ),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return false
    }

    private fun titleFrom(
        prompt: String,
        imageCount: Int,
        languageTag: String,
    ): String {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return if (imageCount > 0) {
                appStrings.imageOnlyConversationTitle(languageTag)
            } else {
                appStrings.defaultConversationTitle(languageTag)
            }
        }
        return trimmed.take(36)
    }

    private fun previewFrom(
        content: String,
        imageCount: Int,
        languageTag: String,
    ): String {
        val trimmed = content.trim()
        return when {
            trimmed.isNotEmpty() && imageCount > 0 -> appStrings.imagePreviewWithText(languageTag, trimmed, imageCount)
            trimmed.isNotEmpty() -> trimmed
            imageCount > 0 -> appStrings.imagePreview(languageTag, imageCount)
            else -> ""
        }
    }

    private fun encodeImagePaths(imagePaths: List<String>): String = json.encodeToString(imagePaths)
    private fun encodeActivityLog(activityLog: List<ChatActivity>): String = json.encodeToString(activityLog)

    private fun decodeImagePaths(raw: String): List<String> = runCatching {
        json.decodeFromString<List<String>>(raw)
    }.getOrElse { emptyList() }

    private fun decodeActivityLog(raw: String): List<ChatActivity> = runCatching {
        json.decodeFromString<List<ChatActivity>>(raw)
    }.getOrElse { emptyList() }
}
