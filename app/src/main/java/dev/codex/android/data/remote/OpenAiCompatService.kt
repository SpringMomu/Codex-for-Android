package dev.codex.android.data.remote

import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.core.media.ImageProcessing
import dev.codex.android.data.model.AppSettings
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.MessageRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class OpenAiCompatService(
    private val okHttpClient: OkHttpClient,
    private val appStrings: AppStrings,
) {
    object WebSearchState {
        const val SEARCHING = "searching"
        const val COMPLETED = "completed"
    }

    sealed interface StreamEvent {
        data class TextDelta(val delta: String) : StreamEvent
        data class ReasoningSummaryDelta(val delta: String) : StreamEvent
        data class WebSearchStateChanged(val state: String) : StreamEvent
        data class Completed(val reply: AssistantReply) : StreamEvent
    }

    data class AssistantReply(
        val text: String,
        val reasoningSummary: String = "",
    )

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun createAssistantReply(
        settings: AppSettings,
        history: List<ChatMessage>,
    ): Result<AssistantReply> = withContext(Dispatchers.IO) {
        runCatching {
            var finalReply: AssistantReply? = null
            streamAssistantReply(
                settings = settings,
                history = history,
                onEvent = { event ->
                    if (event is StreamEvent.Completed) {
                        finalReply = event.reply
                    }
                },
            ).getOrThrow()
            finalReply ?: error(appStrings.errorNoVisibleReply(settings.languageTag))
        }
    }

    suspend fun streamAssistantReply(
        settings: AppSettings,
        history: List<ChatMessage>,
        onEvent: suspend (StreamEvent) -> Unit,
        onCallCreated: ((Call) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.baseUrl.isNotBlank()) { appStrings.errorFillBaseUrl(settings.languageTag) }
            require(settings.apiKey.isNotBlank()) { appStrings.errorFillApiKey(settings.languageTag) }
            require(settings.modelAlias.isNotBlank()) { appStrings.errorFillModelAlias(settings.languageTag) }

            var lastError: Throwable? = null
            for ((index, toolType) in WEB_SEARCH_TOOL_TYPES.withIndex()) {
                try {
                    executeStreamingRequest(
                        settings = settings,
                        history = history,
                        onEvent = onEvent,
                        webSearchToolType = toolType,
                        onCallCreated = onCallCreated,
                    )
                    lastError = null
                    break
                } catch (throwable: Throwable) {
                    lastError = throwable
                    val shouldRetry = index < WEB_SEARCH_TOOL_TYPES.lastIndex &&
                        isUnsupportedToolTypeError(throwable.message)
                    if (!shouldRetry) {
                        throw throwable
                    }
                }
            }
            lastError?.let { throw it }
            Unit
        }
    }

    private suspend fun executeStreamingRequest(
        settings: AppSettings,
        history: List<ChatMessage>,
        onEvent: suspend (StreamEvent) -> Unit,
        webSearchToolType: String,
        onCallCreated: ((Call) -> Unit)? = null,
    ) {
        val requestBody = ResponsesRequest(
            model = settings.modelAlias,
            stream = true,
            instructions = settings.systemPrompt.ifBlank { null },
            reasoning = ReasoningConfig(
                effort = settings.reasoningEffort.ifBlank { "high" },
                summary = "auto",
            ),
            tools = listOf(BuiltInTool(type = webSearchToolType)),
            input = buildRequestMessages(history),
        )

        val request = Request.Builder()
            .url(resolveEndpoint(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(ResponsesRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = okHttpClient.newCall(request)
        onCallCreated?.invoke(call)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val responseBody = response.body?.string().orEmpty()
                error(responseBody.ifBlank { appStrings.errorRequestFailedHttp(settings.languageTag, response.code) })
            }
            val responseBody = response.body ?: error(appStrings.errorEmptyResponseBody(settings.languageTag))
            parseStreamingResponse(
                reader = responseBody.charStream().buffered(),
                languageTag = settings.languageTag,
                onEvent = onEvent,
            )
        }
    }

    private fun isUnsupportedToolTypeError(message: String?): Boolean {
        val normalized = message.orEmpty()
        return normalized.contains("Unsupported tool type", ignoreCase = true) ||
            normalized.contains("unsupported tool", ignoreCase = true)
    }

    private fun buildRequestMessages(
        history: List<ChatMessage>,
    ): List<ResponseInputItem> = sanitizeHistoryForModel(history)
            .filterNot {
                it.role == MessageRole.SYSTEM ||
                    it.isError ||
                    (it.content.isBlank() && it.imagePaths.isEmpty())
            }
            .map { message ->
                ResponseInputItem(
                    role = message.role.toStorage(),
                    content = buildRequestContent(message),
                )
            }

    private fun sanitizeHistoryForModel(history: List<ChatMessage>): List<ChatMessage> = history.map { message ->
        // Reasoning summary is UI-only metadata and must never be sent back as model context.
        message.copy(reasoningSummary = "")
    }

    private fun buildRequestContent(message: ChatMessage): List<ResponseContentItem> {
        val items = mutableListOf<ResponseContentItem>()

        if (message.content.isNotBlank()) {
            items += ResponseContentItem(
                type = when (message.role) {
                    MessageRole.ASSISTANT -> "output_text"
                    MessageRole.USER -> "input_text"
                    MessageRole.SYSTEM -> "input_text"
                },
                text = message.content,
            )
        }

        if (message.role == MessageRole.USER) {
            items += message.imagePaths.map { path ->
                ResponseContentItem(
                    type = "input_image",
                    imageUrl = fileToDataUrl(path),
                )
            }
        }

        return items
    }

    private fun resolveEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().removeSuffix("/")
        return when {
            trimmed.endsWith("/v1/responses") -> trimmed
            trimmed.endsWith("/responses") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/responses"
            else -> "$trimmed/v1/responses"
        }
    }

    private suspend fun parseStreamingResponse(
        reader: java.io.BufferedReader,
        languageTag: String,
        onEvent: suspend (StreamEvent) -> Unit,
    ): AssistantReply {
        val content = StringBuilder()
        val reasoningSummary = StringBuilder()
        var pendingError: String? = null

        reader.useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data: ")) return@forEach
                val rawPayload = line.removePrefix("data: ").trim()
                if (rawPayload == "[DONE]" || rawPayload.isBlank()) return@forEach

                val payload = json.parseToJsonElement(rawPayload).jsonObject
                when (payload["type"]?.jsonPrimitive?.contentOrNull) {
                    "response.output_text.delta" -> {
                        val delta = payload["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotEmpty()) {
                            content.append(delta)
                            onEvent(StreamEvent.TextDelta(delta))
                        }
                    }

                    "response.output_text.done" -> {
                        if (content.isEmpty()) {
                            val text = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (text.isNotEmpty()) {
                                content.append(text)
                                onEvent(StreamEvent.TextDelta(text))
                            }
                        }
                    }

                    "response.reasoning_summary_text.delta" -> {
                        val delta = payload["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (delta.isNotEmpty()) {
                            reasoningSummary.append(delta)
                            onEvent(StreamEvent.ReasoningSummaryDelta(delta))
                        }
                    }

                    "response.reasoning_summary_text.done" -> {
                        if (reasoningSummary.isEmpty()) {
                            val text = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (text.isNotEmpty()) {
                                reasoningSummary.append(text)
                                onEvent(StreamEvent.ReasoningSummaryDelta(text))
                            }
                        }
                    }

                    "response.web_search_call.searching" -> {
                        onEvent(StreamEvent.WebSearchStateChanged(WebSearchState.SEARCHING))
                    }

                    "response.web_search_call.completed" -> {
                        onEvent(StreamEvent.WebSearchStateChanged(WebSearchState.COMPLETED))
                    }

                    "response.completed" -> {
                        if (content.isEmpty()) {
                            val text = extractCompletedText(payload)
                            if (text.isNotEmpty()) {
                                content.append(text)
                                onEvent(StreamEvent.TextDelta(text))
                            }
                        }
                        if (reasoningSummary.isEmpty()) {
                            val summary = extractCompletedReasoningSummary(payload)
                            if (summary.isNotEmpty()) {
                                reasoningSummary.append(summary)
                                onEvent(StreamEvent.ReasoningSummaryDelta(summary))
                            }
                        }
                    }

                    "response.failed" -> {
                        pendingError = extractErrorMessage(payload, languageTag)
                    }

                    "error" -> {
                        pendingError = payload["message"]?.jsonPrimitive?.contentOrNull
                    }
                }
            }
        }

        pendingError?.let { error(it) }
        val reply = AssistantReply(
            text = content.toString().trim().ifBlank { error(appStrings.errorNoVisibleReply(languageTag)) },
            reasoningSummary = reasoningSummary.toString().trim(),
        )
        onEvent(StreamEvent.Completed(reply))
        return reply
    }

    private fun extractCompletedText(payload: JsonObject): String {
        val output = payload["response"]
            ?.jsonObject
            ?.get("output")
            ?.jsonArray
            ?: return ""

        return output.firstNotNullOfOrNull { item ->
            val itemObject = item.jsonObject
            val content = itemObject["content"]?.jsonArray ?: return@firstNotNullOfOrNull null
            content.firstNotNullOfOrNull { part ->
                val partObject = part.jsonObject
                if (partObject["type"]?.jsonPrimitive?.contentOrNull == "output_text") {
                    partObject["text"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            }
        }.orEmpty()
    }

    private fun extractErrorMessage(
        payload: JsonObject,
        languageTag: String,
    ): String {
        return payload["response"]
            ?.jsonObject
            ?.get("error")
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: appStrings.errorModelInvocationFailed(languageTag)
    }

    private fun extractCompletedReasoningSummary(payload: JsonObject): String {
        val output = payload["response"]
            ?.jsonObject
            ?.get("output")
            ?.jsonArray
            ?: return ""

        return output.mapNotNull { item ->
            val itemObject = item.jsonObject
            if (itemObject["type"]?.jsonPrimitive?.contentOrNull != "reasoning") {
                return@mapNotNull null
            }

            val summary = itemObject["summary"]?.jsonArray ?: return@mapNotNull null
            summary.mapNotNull { part ->
                val summaryObject = part.jsonObject
                if (summaryObject["type"]?.jsonPrimitive?.contentOrNull == "summary_text") {
                    summaryObject["text"]?.jsonPrimitive?.contentOrNull
                } else {
                    null
                }
            }.joinToString("\n\n").ifBlank { null }
        }.joinToString("\n\n")
    }

    private fun fileToDataUrl(path: String): String {
        val preparedImage = ImageProcessing.prepareImageForUpload(path)
            ?: error("Unable to read attachment: $path")
        val base64 = Base64.getEncoder().encodeToString(preparedImage.bytes)
        return "data:${preparedImage.mimeType};base64,$base64"
    }

    @Serializable
    private data class ResponsesRequest(
        val model: String,
        val stream: Boolean,
        val instructions: String? = null,
        val reasoning: ReasoningConfig? = null,
        val tools: List<BuiltInTool> = emptyList(),
        val input: List<ResponseInputItem>,
    )

    @Serializable
    private data class BuiltInTool(
        val type: String,
    )

    @Serializable
    private data class ReasoningConfig(
        val effort: String,
        val summary: String? = null,
    )

    @Serializable
    private data class ResponseInputItem(
        val role: String,
        val content: List<ResponseContentItem>,
    )

    @Serializable
    private data class ResponseContentItem(
        val type: String,
        val text: String? = null,
        @kotlinx.serialization.SerialName("image_url")
        val imageUrl: String? = null,
    )

    private companion object {
        val WEB_SEARCH_TOOL_TYPES = listOf("web_search", "web_search_preview")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
