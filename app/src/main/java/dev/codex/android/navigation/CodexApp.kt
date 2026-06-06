package dev.codex.android.navigation

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.AppSettings
import dev.codex.android.feature.chat.ChatRoute
import dev.codex.android.feature.history.HistorySidebarRoute
import dev.codex.android.feature.image.ImageGenerationRoute
import dev.codex.android.feature.settings.SettingsRoute
import dev.codex.android.ui.theme.CodexTheme
import kotlinx.coroutines.launch

private enum class TopLevelDestination(
    val route: String,
) {
    CHAT("chat"),
    IMAGE("image"),
    SETTINGS("settings"),
}

@Composable
fun CodexApp(container: AppContainer) {
    val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings()).value
    val baseContext = LocalContext.current
    val activityResultRegistryOwner = requireNotNull(LocalActivityResultRegistryOwner.current) {
        "CodexApp requires an ActivityResultRegistryOwner."
    }
    val localizedContext = remember(baseContext, settings.languageTag) {
        container.appLocaleManager.localizedContext(baseContext, settings.languageTag)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
    ) {
        CodexTheme {
            val navController = rememberNavController()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val coroutineScope = rememberCoroutineScope()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val isChatContentVisible = currentBackStackEntry?.destination?.route == TopLevelDestination.CHAT.route &&
                drawerState.currentValue == DrawerValue.Closed
            val drawerWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
            val activeStreams = container.chatStreamCoordinator.activeStreamsState
                .collectAsStateWithLifecycle()
                .value
            var previouslyStreamingConversationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
            var unreadConversationIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
            var activeConversationId by rememberSaveable { mutableStateOf<Long?>(null) }
            var chatSessionNonce by rememberSaveable { mutableStateOf(0) }

            LaunchedEffect(activeStreams, activeConversationId, isChatContentVisible) {
                val streamingConversationIds = activeStreams.keys.toSet()
                val completedConversationIds = previouslyStreamingConversationIds - streamingConversationIds
                val updatedUnread = unreadConversationIds.toSet() +
                    completedConversationIds.filter { conversationId ->
                        conversationId != activeConversationId || !isChatContentVisible
                    }
                val visibleUnread = if (isChatContentVisible) {
                    activeConversationId?.let { updatedUnread - it } ?: updatedUnread
                } else {
                    updatedUnread
                }
                unreadConversationIds = visibleUnread.toList()
                previouslyStreamingConversationIds = streamingConversationIds
            }

            fun navigateToChat() {
                navController.navigate(TopLevelDestination.CHAT.route) {
                    popUpTo(TopLevelDestination.CHAT.route) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(drawerWidth),
                        ) {
                            HistorySidebarRoute(
                                container = container,
                                activeConversationId = activeConversationId,
                                unreadConversationIds = unreadConversationIds.toSet(),
                                onConversationSelected = { id ->
                                    activeConversationId = id
                                    unreadConversationIds = unreadConversationIds.filterNot { it == id }
                                    navigateToChat()
                                    coroutineScope.launch { drawerState.close() }
                                },
                                onNewConversation = {
                                    activeConversationId = null
                                    chatSessionNonce += 1
                                    navigateToChat()
                                    coroutineScope.launch { drawerState.close() }
                                },
                            )
                        }
                    },
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = TopLevelDestination.CHAT.route,
                    ) {
                        composable(TopLevelDestination.CHAT.route) {
                            ChatRoute(
                                container = container,
                                conversationId = activeConversationId,
                                sessionNonce = chatSessionNonce,
                                onConversationCreated = { activeConversationId = it },
                                onNewConversation = {
                                    activeConversationId = null
                                    chatSessionNonce += 1
                                },
                                onOpenHistory = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onOpenImageMode = { navController.navigate(TopLevelDestination.IMAGE.route) },
                                onOpenSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                            )
                        }
                        composable(TopLevelDestination.SETTINGS.route) {
                            SettingsRoute(
                                container = container,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(TopLevelDestination.IMAGE.route) {
                            ImageGenerationRoute(
                                container = container,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
