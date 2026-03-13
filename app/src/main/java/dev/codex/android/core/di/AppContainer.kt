package dev.codex.android.core.di

import android.content.Context
import dev.codex.android.core.i18n.AppLocaleManager
import dev.codex.android.core.i18n.AppStrings
import dev.codex.android.data.local.AttachmentStorage
import dev.codex.android.data.local.CodexDatabase
import dev.codex.android.data.remote.OpenAiCompatService
import dev.codex.android.data.repository.ConversationRepository
import dev.codex.android.data.repository.SettingsRepository
import dev.codex.android.feature.chat.ChatStreamCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val appLocaleManager = AppLocaleManager()
    val appStrings = AppStrings(context)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val database = CodexDatabase.create(context)
    private val attachmentStorage = AttachmentStorage(context)

    val settingsRepository = SettingsRepository(
        context = context,
    )
    val conversationRepository = ConversationRepository(
        chatDao = database.chatDao(),
        attachmentStorage = attachmentStorage,
        settingsRepository = settingsRepository,
        appStrings = appStrings,
    )
    val openAiCompatService = OpenAiCompatService(
        okHttpClient = httpClient,
        appStrings = appStrings,
    )
    val chatStreamCoordinator = ChatStreamCoordinator(
        appContext = context.applicationContext,
        applicationScope = applicationScope,
        conversationRepository = conversationRepository,
        settingsRepository = settingsRepository,
        openAiCompatService = openAiCompatService,
        appStrings = appStrings,
    )
}
