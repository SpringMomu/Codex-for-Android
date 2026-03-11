package dev.codex.android.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.ConversationSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<ConversationSummary> = emptyList(),
) {
    val sessionCount: Int get() = sessions.size
}

class HistoryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val uiState = container.conversationRepository.observeConversations()
        .map { HistoryUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            container.conversationRepository.deleteConversation(conversationId)
        }
    }
}

fun historyViewModelFactory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryViewModel(container) as T
    }
}
