package dev.codex.android.core.i18n

import android.content.Context
import dev.codex.android.R

class AppStrings(
    private val context: Context,
    private val localeManager: AppLocaleManager = AppLocaleManager(),
) {
    fun defaultConversationTitle(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.conversation_title_new)

    fun imageOnlyConversationTitle(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.conversation_title_image_only)

    fun imagePreview(languageTag: String, imageCount: Int): String = localizedContext(languageTag)
        .resources
        .getQuantityString(R.plurals.conversation_preview_images, imageCount, imageCount)

    fun imagePreviewWithText(
        languageTag: String,
        content: String,
        imageCount: Int,
    ): String = localizedContext(languageTag).getString(
        R.string.conversation_preview_with_images,
        content,
        imagePreview(languageTag, imageCount),
    )

    fun errorNoVisibleReply(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_no_visible_reply)

    fun errorFillBaseUrl(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_fill_base_url)

    fun errorFillApiKey(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_fill_api_key)

    fun errorFillModelAlias(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_fill_model_alias)

    fun errorRequestFailedHttp(
        languageTag: String,
        code: Int,
    ): String = localizedContext(languageTag).getString(R.string.error_request_failed_http, code)

    fun errorEmptyResponseBody(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_empty_response_body)

    fun errorModelInvocationFailed(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_model_invocation_failed)

    fun errorRequestFailedUnknown(languageTag: String): String = localizedContext(languageTag)
        .getString(R.string.error_request_failed_unknown)

    private fun localizedContext(languageTag: String): Context {
        return localeManager.localizedContext(context, languageTag)
    }
}
