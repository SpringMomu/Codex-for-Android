package dev.codex.android.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.codex.android.core.di.AppContainer
import dev.codex.android.feature.chat.ChatRoute
import dev.codex.android.feature.history.HistoryRoute
import dev.codex.android.feature.settings.SettingsRoute
import dev.codex.android.ui.theme.CodexTheme

private enum class TopLevelDestination(
    val route: String,
) {
    CHAT("chat"),
    HISTORY("history"),
    SETTINGS("settings"),
}

@Composable
fun CodexApp(container: AppContainer) {
    CodexTheme {
        val navController = rememberNavController()
        var activeConversationId by rememberSaveable { mutableStateOf<Long?>(null) }
        var chatSessionNonce by rememberSaveable { mutableStateOf(0) }
        Surface(modifier = Modifier.fillMaxSize()) {
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
                        onOpenHistory = { navController.navigate(TopLevelDestination.HISTORY.route) },
                        onOpenSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                    )
                }
                composable(TopLevelDestination.HISTORY.route) {
                    HistoryRoute(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onConversationSelected = { id ->
                            activeConversationId = id
                            navController.navigate(TopLevelDestination.CHAT.route) {
                                popUpTo(TopLevelDestination.CHAT.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        },
                        onNewConversation = {
                            activeConversationId = null
                            chatSessionNonce += 1
                            navController.navigate(TopLevelDestination.CHAT.route) {
                                popUpTo(TopLevelDestination.CHAT.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(TopLevelDestination.SETTINGS.route) {
                    SettingsRoute(
                        container = container,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
