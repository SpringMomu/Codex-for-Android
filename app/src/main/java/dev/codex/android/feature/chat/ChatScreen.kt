package dev.codex.android.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.android.R
import dev.codex.android.core.di.AppContainer
import dev.codex.android.core.media.ImageProcessing
import dev.codex.android.data.model.ChatActivity
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.ChatProvider
import dev.codex.android.data.model.MessageRole
import dev.codex.android.ui.format.formatTimestamp
import dev.codex.android.ui.markdown.MarkdownChunk
import dev.codex.android.ui.markdown.MarkdownText
import dev.codex.android.ui.markdown.containsLikelyLatex
import dev.codex.android.ui.markdown.splitMarkdownChunks
import dev.codex.android.ui.theme.Mist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun ChatRoute(
    container: AppContainer,
    conversationId: Long?,
    sessionNonce: Int,
    onConversationCreated: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenImageMode: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-$conversationId-$sessionNonce",
        factory = chatViewModelFactory(
            container = container,
            conversationId = conversationId,
        ),
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(viewModel) {
        viewModel.createdConversation.collect(onConversationCreated)
    }

    ChatScreen(
        uiState = uiState,
        draft = viewModel.draft,
        selectedImagePaths = viewModel.selectedImagePaths,
        onDraftChange = viewModel::updateDraft,
        onSend = viewModel::sendMessage,
        onAddImages = viewModel::importSelectedImages,
        onRemoveSelectedImage = viewModel::removeSelectedImage,
        onNewConversation = onNewConversation,
        onOpenHistory = onOpenHistory,
        onOpenImageMode = onOpenImageMode,
        onOpenSettings = onOpenSettings,
        onUpdateMessage = viewModel::updateMessage,
        onDeleteMessage = viewModel::deleteMessage,
        onBranchMessage = viewModel::branchFromMessage,
        onPersistScrollPosition = viewModel::persistScrollPosition,
        onRetryMessage = viewModel::retryFailedMessage,
        onGenerateReply = viewModel::generateReplyFromMessage,
        onStopStreaming = viewModel::stopStreaming,
        onSelectModel = viewModel::selectModel,
    )
}

@Composable
private fun ChatScreen(
    uiState: ChatUiState,
    draft: String,
    selectedImagePaths: List<String>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onRemoveSelectedImage: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenImageMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onBranchMessage: (Long) -> Unit,
    onPersistScrollPosition: (Long?, Int, Int) -> Unit,
    onRetryMessage: (Long) -> Unit,
    onGenerateReply: (Long) -> Unit,
    onStopStreaming: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var hasRestoredScroll by remember(uiState.activeConversationId) { mutableStateOf(false) }
    var lastObservedMessageCount by remember(uiState.activeConversationId) { mutableStateOf(0) }
    var isSearchMode by rememberSaveable(uiState.activeConversationId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(uiState.activeConversationId) { mutableStateOf("") }
    var currentSearchResultIndex by rememberSaveable(uiState.activeConversationId) { mutableStateOf(-1) }
    var lastSearchQuery by rememberSaveable(uiState.activeConversationId) { mutableStateOf("") }
    var searchViewport by remember { mutableStateOf<SearchViewport?>(null) }
    var activeSearchTargetCenterY by remember { mutableStateOf<Float?>(null) }
    val conversationItems = remember(uiState.messages, uiState.streamingMessageId) {
        buildConversationListItems(
            messages = uiState.messages,
            streamingMessageId = uiState.streamingMessageId,
        )
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        onAddImages(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUriString?.let(Uri::parse)
        val path = pendingCameraPath
        pendingCameraUriString = null
        pendingCameraPath = null
        if (success && uri != null) {
            onAddImages(listOf(uri))
        } else if (path != null) {
            deleteCapturedImage(path)
        }
    }

    LaunchedEffect(uiState.activeConversationId, conversationItems, uiState.savedScrollPosition) {
        if (hasRestoredScroll || conversationItems.isEmpty()) return@LaunchedEffect

        val savedPosition = uiState.savedScrollPosition
        val savedListIndexIsValid = savedPosition != null &&
            conversationItems.getOrNull(savedPosition.firstVisibleItemIndex)?.message?.id ==
            savedPosition.anchorMessageId
        val targetIndex = when {
            savedListIndexIsValid -> savedPosition.firstVisibleItemIndex
            savedPosition?.anchorMessageId != null -> {
                conversationItems.indexOfFirst { it.message.id == savedPosition.anchorMessageId }
                    .takeIf { it >= 0 }
            }
            savedPosition != null -> {
                conversationItems.indexOfFirst {
                    it.messageIndex == savedPosition.firstVisibleItemIndex.coerceIn(0, uiState.messages.lastIndex)
                }.takeIf { it >= 0 }
            }
            else -> conversationItems.lastIndex
        }

        withFrameNanos { }
        listState.scrollToItem(
            index = targetIndex ?: conversationItems.lastIndex,
            scrollOffset = if (savedListIndexIsValid) {
                savedPosition?.firstVisibleItemScrollOffset?.coerceAtLeast(0) ?: 0
            } else {
                0
            },
        )
        lastObservedMessageCount = uiState.messages.size
        hasRestoredScroll = true
    }

    LaunchedEffect(
        uiState.activeConversationId,
        uiState.messages.size,
        uiState.streamingMessageId,
        conversationItems.size,
        hasRestoredScroll,
    ) {
        if (!hasRestoredScroll) return@LaunchedEffect

        val currentCount = uiState.messages.size
        val previousCount = lastObservedMessageCount
        val hasNewMessages = currentCount > previousCount
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
        val totalItemCount = conversationItems.size + if (uiState.streamingMessageId != null) 1 else 0
        val isNearBottom = totalItemCount == 0 || lastVisibleIndex >= totalItemCount - 2

        if (hasNewMessages && (previousCount == 0 || isNearBottom)) {
            withFrameNanos { }
            listState.animateScrollToItem((totalItemCount - 1).coerceAtLeast(0))
        }

        lastObservedMessageCount = currentCount
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(uiState.activeConversationId, conversationItems) {
        if (uiState.activeConversationId == null || conversationItems.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            Triple(
                conversationItems.getOrNull(listState.firstVisibleItemIndex)?.message?.id,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
            .distinctUntilChanged()
            .debounce(250)
            .collect { (anchorMessageId, index, offset) ->
                onPersistScrollPosition(anchorMessageId, index, offset)
            }
    }

    DisposableEffect(uiState.activeConversationId, conversationItems) {
        onDispose {
            if (uiState.activeConversationId != null && conversationItems.isNotEmpty()) {
                onPersistScrollPosition(
                    conversationItems.getOrNull(listState.firstVisibleItemIndex)?.message?.id,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    val effectiveSearchQuery = searchQuery.takeIf { isSearchMode }.orEmpty()
    val searchMatches = remember(uiState.messages, effectiveSearchQuery) {
        findConversationMatches(
            messages = uiState.messages,
            query = effectiveSearchQuery,
        )
    }
    val matchedContentMessageIds = remember(searchMatches) {
        searchMatches.asSequence()
            .filter { it.section == SearchSection.CONTENT }
            .map { it.messageId }
            .toSet()
    }
    val matchedReasoningMessageIds = remember(searchMatches) {
        searchMatches.asSequence()
            .filter { it.section == SearchSection.REASONING_SUMMARY }
            .map { it.messageId }
            .toSet()
    }
    val activeSearchMatch = searchMatches.getOrNull(currentSearchResultIndex)
    val activeSearchListItemIndex = remember(
        activeSearchMatch,
        conversationItems,
        effectiveSearchQuery,
    ) {
        activeSearchMatch?.let { match ->
            findConversationListItemIndexForSearchMatch(
                items = conversationItems,
                match = match,
                query = effectiveSearchQuery,
            )
        }
    }

    LaunchedEffect(isSearchMode, effectiveSearchQuery, searchMatches) {
        if (!isSearchMode) return@LaunchedEffect

        if (effectiveSearchQuery.isBlank()) {
            currentSearchResultIndex = -1
            lastSearchQuery = effectiveSearchQuery
            return@LaunchedEffect
        }

        val queryChanged = effectiveSearchQuery != lastSearchQuery
        lastSearchQuery = effectiveSearchQuery

        if (searchMatches.isEmpty()) {
            currentSearchResultIndex = -1
            return@LaunchedEffect
        }

        if (queryChanged || currentSearchResultIndex !in searchMatches.indices) {
            currentSearchResultIndex = 0
        }
    }

    LaunchedEffect(activeSearchMatch, activeSearchListItemIndex, currentSearchResultIndex, isSearchMode) {
        if (!isSearchMode || activeSearchMatch == null || activeSearchListItemIndex == null) {
            return@LaunchedEffect
        }

        activeSearchTargetCenterY = null
        withFrameNanos { }

        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeSearchListItemIndex }
        if (!isVisible) {
            listState.scrollToItem(activeSearchListItemIndex)
            withFrameNanos { }
        }

        val viewport = searchViewport ?: return@LaunchedEffect
        val desiredCenterY = viewport.height / 2f

        var targetCenterY = resolvePreciseSearchTargetCenterInViewport(
            searchViewport = searchViewport,
            activeSearchTargetCenterY = activeSearchTargetCenterY,
        )
        for (frame in 0 until SEARCH_TARGET_WAIT_FRAMES) {
            if (targetCenterY != null && searchViewport != null) {
                break
            }
            withFrameNanos { }
            targetCenterY = resolvePreciseSearchTargetCenterInViewport(
                searchViewport = searchViewport,
                activeSearchTargetCenterY = activeSearchTargetCenterY,
            )
        }

        val initialTargetCenterY = targetCenterY ?: resolveSearchTargetCenterInViewport(
            listState = listState,
            messageIndex = activeSearchListItemIndex,
            searchViewport = viewport,
            activeSearchTargetCenterY = activeSearchTargetCenterY,
        ) ?: return@LaunchedEffect

        val initialDelta = initialTargetCenterY - desiredCenterY
        if (abs(initialDelta) > SEARCH_CENTERING_TOLERANCE_PX) {
            val before = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            listState.scrollBy(initialDelta)
            withFrameNanos { }
            val after = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            if (before == after) {
                return@LaunchedEffect
            }
        }

        var refinedTargetCenterY = resolvePreciseSearchTargetCenterInViewport(
            searchViewport = searchViewport,
            activeSearchTargetCenterY = activeSearchTargetCenterY,
        )
        for (frame in 0 until SEARCH_REFINEMENT_WAIT_FRAMES) {
            if (refinedTargetCenterY != null && searchViewport != null) {
                break
            }
            withFrameNanos { }
            refinedTargetCenterY = resolvePreciseSearchTargetCenterInViewport(
                searchViewport = searchViewport,
                activeSearchTargetCenterY = activeSearchTargetCenterY,
            )
        }

        val correctedViewport = searchViewport ?: viewport
        val correctedTargetCenterY = refinedTargetCenterY ?: resolveSearchTargetCenterInViewport(
            listState = listState,
            messageIndex = activeSearchListItemIndex,
            searchViewport = correctedViewport,
            activeSearchTargetCenterY = activeSearchTargetCenterY,
        ) ?: return@LaunchedEffect
        val correctionDelta = correctedTargetCenterY - correctedViewport.height / 2f
        if (abs(correctionDelta) <= SEARCH_CENTERING_TOLERANCE_PX) {
            return@LaunchedEffect
        }

        listState.scrollBy(correctionDelta)
    }

    val modelPlaceholder = stringResource(R.string.model_not_configured)
    val providerTitle = stringResource(
        when (uiState.chatProvider) {
            ChatProvider.CODEX -> R.string.settings_chat_provider_codex
            ChatProvider.CLAUDE -> R.string.settings_chat_provider_claude
        },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 920.dp)
                        .align(Alignment.Center)
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedImagePaths.isNotEmpty()) {
                        SelectedImagesRow(
                            imagePaths = selectedImagePaths,
                            onRemove = onRemoveSelectedImage,
                        )
                    }
                    val canSubmit = draft.isNotBlank() || selectedImagePaths.isNotEmpty()
                    val sendContainerColor by animateColorAsState(
                        targetValue = when {
                            uiState.isSending -> MaterialTheme.colorScheme.surfaceVariant
                            canSubmit -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        animationSpec = tween(durationMillis = 160),
                        label = "composer-action-color",
                    )
                    Surface(
                        modifier = Modifier.animateContentSize(
                            animationSpec = tween(durationMillis = 180),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 2.dp,
                    ) {
                        Column {
                            ChatModelSelector(
                                models = uiState.availableModels,
                                selectedModel = uiState.modelAlias,
                                placeholder = modelPlaceholder,
                                enabled = !uiState.isSending,
                                onSelectModel = onSelectModel,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = { showImageSourceDialog = true },
                                    enabled = !uiState.isSending,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = stringResource(R.string.add_image),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                BasicTextField(
                                    value = draft,
                                    onValueChange = onDraftChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .padding(vertical = 9.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.merge(
                                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                                    ),
                                    minLines = 1,
                                    maxLines = 5,
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (draft.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.chat_input_placeholder),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = sendContainerColor,
                                ) {
                                    IconButton(
                                        onClick = if (uiState.isSending) onStopStreaming else onSend,
                                        enabled = uiState.isSending || canSubmit,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isSending) {
                                                Icons.Rounded.Stop
                                            } else {
                                                Icons.AutoMirrored.Rounded.Send
                                            },
                                            contentDescription = stringResource(
                                                if (uiState.isSending) R.string.stop_generation else R.string.send,
                                            ),
                                            tint = if (uiState.isSending || !canSubmit) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onPrimary
                                            },
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp),
        ) {
            ChatHeader(
                title = providerTitle,
                modelName = uiState.modelAlias.ifBlank { modelPlaceholder },
                isSearchMode = isSearchMode,
                onOpenSearch = {
                    isSearchMode = true
                },
                onOpenHistory = onOpenHistory,
                onOpenImageMode = onOpenImageMode,
                onOpenSettings = onOpenSettings,
                onNewConversation = onNewConversation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
            )
            AnimatedVisibility(
                visible = isSearchMode,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                ConversationSearchBar(
                    query = searchQuery,
                    matchSummary = when {
                        searchQuery.isBlank() -> null
                        searchMatches.isEmpty() -> stringResource(R.string.chat_search_result_empty)
                        else -> stringResource(
                            R.string.chat_search_result_count,
                            currentSearchResultIndex + 1,
                            searchMatches.size,
                        )
                    },
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchMode = false
                    },
                    onPrevious = {
                        if (searchMatches.isEmpty()) return@ConversationSearchBar
                        currentSearchResultIndex = if (currentSearchResultIndex in searchMatches.indices) {
                            (currentSearchResultIndex - 1 + searchMatches.size) % searchMatches.size
                        } else {
                            searchMatches.lastIndex
                        }
                    },
                    onNext = {
                        if (searchMatches.isEmpty()) return@ConversationSearchBar
                        currentSearchResultIndex = if (currentSearchResultIndex in searchMatches.indices) {
                            (currentSearchResultIndex + 1) % searchMatches.size
                        } else {
                            0
                        }
                    },
                    hasMatches = searchMatches.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            if (uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyChatState()
                    if (uiState.streamingMessageId != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        TypingIndicatorBubble()
                    }
                }
            } else {
                val lastMessageId = uiState.messages.lastOrNull()?.id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                searchViewport = SearchViewport(
                                    topY = coordinates.positionInRoot().y,
                                    height = coordinates.size.height.toFloat(),
                                )
                            },
                        contentPadding = PaddingValues(bottom = 18.dp),
                    ) {
                        items(
                            items = conversationItems,
                            key = { item -> item.key },
                        ) { item ->
                            val message = item.message
                            MessageBubble(
                                message = message,
                                content = item.content,
                                renderContentAsMarkdown = item.renderContentAsMarkdown,
                                isFirstChunk = item.isFirstChunk,
                                isLastChunk = item.isLastChunk,
                                isStreaming = uiState.streamingMessageId == message.id &&
                                    message.role == MessageRole.ASSISTANT,
                                canRetry = message.isError &&
                                    message.role == MessageRole.ASSISTANT &&
                                    message.id == lastMessageId &&
                                    !uiState.isSending,
                                contentSearchQuery = effectiveSearchQuery.takeIf {
                                    matchedContentMessageIds.contains(message.id)
                                }.orEmpty(),
                                reasoningSearchQuery = effectiveSearchQuery.takeIf {
                                    matchedReasoningMessageIds.contains(message.id)
                                }.orEmpty(),
                                activeContentOccurrenceIndex = activeSearchMatch
                                    ?.takeIf {
                                        it.messageId == message.id &&
                                            it.section == SearchSection.CONTENT
                                    }
                                    ?.occurrenceIndex
                                    ?.let { occurrenceIndex ->
                                        item.localContentOccurrenceIndex(
                                            query = effectiveSearchQuery,
                                            occurrenceIndex = occurrenceIndex,
                                        )
                                    },
                                activeReasoningOccurrenceIndex = activeSearchMatch
                                    ?.takeIf {
                                        item.isFirstChunk &&
                                        it.messageId == message.id &&
                                            it.section == SearchSection.REASONING_SUMMARY
                                    }
                                    ?.occurrenceIndex,
                                forceExpandReasoning = isSearchMode &&
                                    item.isFirstChunk &&
                                    activeSearchMatch?.messageId == message.id &&
                                    activeSearchMatch?.section == SearchSection.REASONING_SUMMARY,
                                onActiveSearchTargetPositioned = { centerY ->
                                    activeSearchTargetCenterY = centerY
                                },
                                onRetry = { onRetryMessage(message.id) },
                                onLongPress = { actionMessage = message },
                                modifier = Modifier.padding(
                                    end = if (message.role == MessageRole.USER) 0.dp else 48.dp,
                                ),
                            )
                        }
                        if (uiState.streamingMessageId != null) {
                            item(key = "typing-indicator") {
                                TypingIndicatorBubble()
                            }
                        }
                    }

                    if (uiState.messages.isNotEmpty()) {
                        val canScrollUp = listState.firstVisibleItemIndex > 0 ||
                            listState.firstVisibleItemScrollOffset > 0
                        val canScrollDown = listState.canScrollForward
                        if (canScrollUp || canScrollDown) {
                            QuickScrollControls(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 2.dp, bottom = 28.dp),
                                canScrollUp = canScrollUp,
                                canScrollDown = canScrollDown,
                                onScrollUp = {
                                    val target = previousMessageIndex(
                                        listState = listState,
                                        items = conversationItems,
                                    )
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(target ?: 0)
                                    }
                                },
                                onLongScrollUp = {
                                    coroutineScope.launch {
                                        listState.scrollToItem(0)
                                    }
                                },
                                onScrollDown = {
                                    val target = nextMessageIndex(
                                        listState = listState,
                                        items = conversationItems,
                                    )
                                    coroutineScope.launch {
                                        if (target != null) {
                                            listState.animateScrollToItem(target)
                                        } else {
                                            scrollToConversationBottom(
                                                listState = listState,
                                                lastMessageIndex = conversationItems.lastIndex,
                                                animated = true,
                                            )
                                        }
                                    }
                                },
                                onLongScrollDown = {
                                    coroutineScope.launch {
                                        scrollToConversationBottom(
                                            listState = listState,
                                            lastMessageIndex = conversationItems.lastIndex,
                                            animated = false,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    actionMessage?.let { message ->
        MessageActionDialog(
            message = message,
            canGenerateReply = !uiState.isSending,
            onGenerateReply = {
                actionMessage = null
                onGenerateReply(message.id)
            },
            onCopy = {
                actionMessage = null
                clipboardManager.setText(AnnotatedString(message.content))
                Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
            },
            onBranch = {
                actionMessage = null
                onBranchMessage(message.id)
            },
            onEdit = {
                actionMessage = null
                editingMessage = message
            },
            onDelete = {
                actionMessage = null
                deletingMessage = message
            },
            onDismiss = { actionMessage = null },
        )
    }

    editingMessage?.let { message ->
        EditMessageScreen(
            message = message,
            onDismiss = { editingMessage = null },
            onConfirm = { newContent ->
                onUpdateMessage(message.id, newContent)
                editingMessage = null
            },
        )
    }

    deletingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMessage(message.id)
                        deletingMessage = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.add_image)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImageSourceDialog = false
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Text(stringResource(R.string.open_gallery))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            val capture = createCameraImageCapture(context)
                            if (capture != null) {
                                pendingCameraUriString = capture.uri.toString()
                                pendingCameraPath = capture.path
                                cameraLauncher.launch(capture.uri)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.open_camera))
                    }
                    TextButton(onClick = { showImageSourceDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    modelName: String,
    isSearchMode: Boolean,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenImageMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderActionButton(
            icon = Icons.Rounded.Menu,
            contentDescription = stringResource(R.string.open_history),
            onClick = onOpenHistory,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = modelName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderActionButton(
            icon = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.chat_search_open),
            onClick = onOpenSearch,
            active = isSearchMode,
        )
        Box {
            HeaderActionButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more_actions),
                onClick = { moreMenuExpanded = true },
            )
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.start_new_conversation)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = {
                        moreMenuExpanded = false
                        onNewConversation()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.image_mode_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    onClick = {
                        moreMenuExpanded = false
                        onOpenImageMode()
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_settings)) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        moreMenuExpanded = false
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatModelSelector(
    models: List<String>,
    selectedModel: String,
    placeholder: String,
    enabled: Boolean,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleModels = remember(models, selectedModel) {
        (models + selectedModel)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
    var expanded by remember { mutableStateOf(false) }
    val displayModel = selectedModel.ifBlank { placeholder }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "model-selector-arrow",
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled && visibleModels.isNotEmpty(),
                    onClick = { expanded = true },
                ),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            color = if (visibleModels.isEmpty()) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = displayModel,
                    modifier = Modifier.weight(1f),
                    color = if (visibleModels.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            visibleModels.forEach { modelAlias ->
                val selected = modelAlias == selectedModel
                DropdownMenuItem(
                    text = {
                        Text(
                            text = modelAlias,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectModel(modelAlias)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ConversationSearchBar(
    query: String,
    matchSummary: String?,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasMatches: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(stringResource(R.string.chat_search_placeholder))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.chat_search_close),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    matchSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = hasMatches,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.chat_search_previous),
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = hasMatches,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.chat_search_next),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickScrollControls(
    modifier: Modifier = Modifier,
    canScrollUp: Boolean,
    canScrollDown: Boolean,
    onScrollUp: () -> Unit,
    onLongScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onLongScrollDown: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QuickScrollButton(
            icon = Icons.Rounded.KeyboardArrowUp,
            enabled = canScrollUp,
            onClick = onScrollUp,
            onLongPress = onLongScrollUp,
        )
        QuickScrollButton(
            icon = Icons.Rounded.KeyboardArrowDown,
            enabled = canScrollDown,
            onClick = onScrollDown,
            onLongPress = onLongScrollDown,
        )
    }
}

@Composable
private fun QuickScrollButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        modifier = Modifier.noHapticPressGesture(
            onClick = onClick.takeIf { enabled },
            onLongPress = onLongPress.takeIf { enabled },
        ),
        shape = CircleShape,
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(
                animationSpec = tween(360),
                initialOffsetY = { it / 8 },
            ),
        ) {
            Text(
                text = stringResource(R.string.empty_chat_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SelectedImagesRow(
    imagePaths: List<String>,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(imagePaths, key = { it }) { path ->
            Box {
                AttachmentThumbnail(
                    path = path,
                    modifier = Modifier.size(72.dp),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clickable { onRemove(path) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.remove_image),
                            tint = Mist,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class ConversationListItem(
    val message: ChatMessage,
    val messageIndex: Int,
    val contentChunks: List<MarkdownChunk>,
    val chunkIndex: Int,
    val renderContentAsMarkdown: Boolean,
) {
    val content: String = contentChunks[chunkIndex].markdown
    val isFirstChunk: Boolean = chunkIndex == 0
    val isLastChunk: Boolean = chunkIndex == contentChunks.lastIndex
    val key: String = "message-${message.id}-chunk-$chunkIndex"

    fun localContentOccurrenceIndex(
        query: String,
        occurrenceIndex: Int,
    ): Int? {
        if (query.isBlank()) return null

        var consumedOccurrences = 0
        for (index in 0 until chunkIndex) {
            consumedOccurrences += findTextMatches(contentChunks[index].searchableText, query).size
        }
        val localOccurrenceIndex = occurrenceIndex - consumedOccurrences
        val localOccurrenceCount = findTextMatches(contentChunks[chunkIndex].searchableText, query).size
        return localOccurrenceIndex.takeIf { it in 0 until localOccurrenceCount }
    }
}

private fun buildConversationListItems(
    messages: List<ChatMessage>,
    streamingMessageId: Long?,
): List<ConversationListItem> = buildList {
    messages.forEachIndexed { messageIndex, message ->
        val renderAsMarkdown = shouldRenderWithMarkdown(message)
        val splitChunks = if (
            renderAsMarkdown &&
            message.id != streamingMessageId &&
            message.content.length >= MARKDOWN_VIRTUALIZATION_MIN_CHARS
        ) {
            splitMarkdownChunks(message.content)
        } else {
            emptyList()
        }
        val contentChunks = splitChunks.takeIf {
            it.size >= MARKDOWN_VIRTUALIZATION_MIN_CHUNKS
        } ?: listOf(
            MarkdownChunk(
                markdown = message.content,
                searchableText = message.content,
            ),
        )

        contentChunks.indices.forEach { chunkIndex ->
            add(
                ConversationListItem(
                    message = message,
                    messageIndex = messageIndex,
                    contentChunks = contentChunks,
                    chunkIndex = chunkIndex,
                    renderContentAsMarkdown = renderAsMarkdown,
                ),
            )
        }
    }
}

private fun findConversationListItemIndexForSearchMatch(
    items: List<ConversationListItem>,
    match: ConversationSearchMatch,
    query: String,
): Int? {
    val firstMessageItemIndex = items.indexOfFirst { it.message.id == match.messageId }
        .takeIf { it >= 0 }
        ?: return null
    if (match.section == SearchSection.REASONING_SUMMARY) return firstMessageItemIndex

    return items.indices.firstOrNull { index ->
        val item = items[index]
        item.message.id == match.messageId &&
            item.localContentOccurrenceIndex(query, match.occurrenceIndex) != null
    } ?: firstMessageItemIndex
}

private const val MARKDOWN_VIRTUALIZATION_MIN_CHARS = 5_000
private const val MARKDOWN_VIRTUALIZATION_MIN_CHUNKS = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    content: String,
    renderContentAsMarkdown: Boolean,
    isFirstChunk: Boolean,
    isLastChunk: Boolean,
    isStreaming: Boolean,
    canRetry: Boolean,
    contentSearchQuery: String,
    reasoningSearchQuery: String,
    activeContentOccurrenceIndex: Int?,
    activeReasoningOccurrenceIndex: Int?,
    forceExpandReasoning: Boolean,
    onActiveSearchTargetPositioned: (Float) -> Unit,
    onRetry: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAssistant = message.role == MessageRole.ASSISTANT
    val isUser = message.role == MessageRole.USER
    val background = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.role == MessageRole.USER -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        isAssistant -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        message.isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
        message.role == MessageRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val assistantReadingSurface = isAssistant && !message.isError

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isUser) Modifier.padding(start = 40.dp) else Modifier),
            horizontalArrangement = if (isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
        ) {
            Surface(
                modifier = if (isUser) {
                    Modifier.widthIn(max = 620.dp)
                } else {
                    Modifier.fillMaxWidth()
                },
                color = background,
                shape = if (assistantReadingSurface) {
                    RoundedCornerShape(0.dp)
                } else {
                    RoundedCornerShape(
                        topStart = if (isFirstChunk) 14.dp else 0.dp,
                        topEnd = if (isFirstChunk) 14.dp else 0.dp,
                        bottomStart = if (isLastChunk) 14.dp else 0.dp,
                        bottomEnd = if (isLastChunk) 5.dp else 0.dp,
                    )
                },
                border = if (assistantReadingSurface) null else BorderStroke(1.dp, borderColor),
            ) {
                Column(
                    modifier = if (isUser) Modifier else Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = (if (isUser) Modifier else Modifier.fillMaxWidth())
                            .padding(
                                start = if (assistantReadingSurface) 0.dp else 14.dp,
                                end = if (assistantReadingSurface) 12.dp else 14.dp,
                                top = if (isFirstChunk) {
                                    if (assistantReadingSurface) 8.dp else 13.dp
                                } else {
                                    10.dp
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isFirstChunk && message.activityLog.isNotEmpty()) {
                            ActivityTimeline(message.activityLog)
                        }
                        if (isFirstChunk && message.reasoningSummary.isNotBlank()) {
                            ReasoningSummarySection(
                                messageId = message.id,
                                summary = message.reasoningSummary,
                                contentColor = contentColor,
                                highlightQuery = reasoningSearchQuery,
                                activeOccurrenceIndex = activeReasoningOccurrenceIndex,
                                forceExpanded = forceExpandReasoning,
                                onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
                                onLongPress = onLongPress,
                            )
                        }
                        if (isFirstChunk && message.imagePaths.isNotEmpty()) {
                            MessageImagesRow(
                                imagePaths = message.imagePaths,
                                onLongPress = onLongPress,
                            )
                        }
                        if (content.isNotBlank()) {
                            MessageBody(
                                content = content,
                                renderAsMarkdown = renderContentAsMarkdown,
                                isStreaming = isStreaming,
                                fillWidth = !isUser,
                                contentColor = contentColor,
                                highlightQuery = contentSearchQuery,
                                activeOccurrenceIndex = activeContentOccurrenceIndex,
                                onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
                                onLongPress = onLongPress,
                            )
                        }
                    }
                    if (isLastChunk) {
                        Row(
                            modifier = (if (isUser) Modifier else Modifier.fillMaxWidth())
                                .noHapticPressGesture(onLongPress = onLongPress)
                                .padding(
                                    start = if (assistantReadingSurface) 0.dp else 14.dp,
                                    end = if (assistantReadingSurface) 4.dp else 10.dp,
                                    top = 9.dp,
                                    bottom = if (assistantReadingSurface) 4.dp else 10.dp,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatTimestamp(message.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.52f),
                            )
                            if (canRetry) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (message.isError) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                    },
                                ) {
                                    TextButton(
                                        onClick = onRetry,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.retry),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = contentColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isLastChunk) {
            Spacer(modifier = Modifier.height(if (isAssistant) 18.dp else 14.dp))
        }
    }
}

private data class ConversationSearchMatch(
    val messageId: Long,
    val messageIndex: Int,
    val section: SearchSection,
    val occurrenceIndex: Int,
)

private data class SearchViewport(
    val topY: Float,
    val height: Float,
)

private enum class SearchSection {
    CONTENT,
    REASONING_SUMMARY,
}

private fun findConversationMatches(
    messages: List<ChatMessage>,
    query: String,
): List<ConversationSearchMatch> {
    if (query.isBlank()) return emptyList()

    return buildList {
        messages.forEachIndexed { index, message ->
            findTextMatches(message.content, query).forEachIndexed { occurrenceIndex, _ ->
                add(
                    ConversationSearchMatch(
                        messageId = message.id,
                        messageIndex = index,
                        section = SearchSection.CONTENT,
                        occurrenceIndex = occurrenceIndex,
                    ),
                )
            }
            findTextMatches(message.reasoningSummary, query).forEachIndexed { occurrenceIndex, _ ->
                add(
                    ConversationSearchMatch(
                        messageId = message.id,
                        messageIndex = index,
                        section = SearchSection.REASONING_SUMMARY,
                        occurrenceIndex = occurrenceIndex,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MessageBody(
    content: String,
    renderAsMarkdown: Boolean,
    isStreaming: Boolean,
    fillWidth: Boolean,
    contentColor: Color,
    highlightQuery: String,
    activeOccurrenceIndex: Int?,
    onActiveSearchTargetPositioned: (Float) -> Unit,
    onLongPress: () -> Unit,
) {
    if (isStreaming) {
        StreamingMessageText(
            text = content,
            contentColor = contentColor,
            highlightQuery = highlightQuery,
            activeOccurrenceIndex = activeOccurrenceIndex,
            onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
        )
        return
    }

    if (renderAsMarkdown) {
        MarkdownText(
            markdown = content,
            contentColor = contentColor,
            highlightQuery = highlightQuery,
            activeOccurrenceIndex = activeOccurrenceIndex,
            onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
            onLongPress = onLongPress,
        )
    } else {
        PlainMessageText(
            text = content,
            fillWidth = fillWidth,
            contentColor = contentColor,
            highlightQuery = highlightQuery,
            activeOccurrenceIndex = activeOccurrenceIndex,
            onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
            onLongPress = onLongPress,
        )
    }
}

@Composable
private fun PlainMessageText(
    text: String,
    fillWidth: Boolean,
    contentColor: Color,
    highlightQuery: String,
    activeOccurrenceIndex: Int?,
    onActiveSearchTargetPositioned: (Float) -> Unit,
    onLongPress: () -> Unit,
) {
    val inactiveHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.88f)
    val activeHighlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f)
    val displayText = remember(text, highlightQuery, activeOccurrenceIndex, contentColor, inactiveHighlightColor, activeHighlightColor) {
        buildHighlightedAnnotatedString(
            text = text,
            query = highlightQuery,
            textColor = contentColor,
            inactiveHighlightColor = inactiveHighlightColor,
            activeHighlightColor = activeHighlightColor,
            activeOccurrenceIndex = activeOccurrenceIndex,
        )
    }
    Text(
        text = displayText,
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .noHapticPressGesture(onLongPress = onLongPress)
            .onGloballyPositioned { coordinates ->
                if (highlightQuery.isNotBlank() && activeOccurrenceIndex != null) {
                    onActiveSearchTargetPositioned(
                        coordinates.positionInRoot().y + coordinates.size.height / 2f,
                    )
                }
            },
        style = MaterialTheme.typography.bodyLarge,
        color = contentColor,
    )
}

private fun shouldRenderWithMarkdown(
    message: ChatMessage,
): Boolean {
    if (message.role == MessageRole.ASSISTANT) return true

    val content = message.content
    if (content.isBlank()) return false

    val markdownPatterns = listOf(
        "```",
        "|",
        "# ",
        "- ",
        "* ",
        "> ",
        "[",
        "](",
        "\\(",
        "\\[",
    )
    return markdownPatterns.any(content::contains) ||
        containsLikelyLatex(content) ||
        containsAsciiTable(content)
}

private fun containsAsciiTable(content: String): Boolean {
    val lines = content.lines()
    if (lines.size < 3) return false

    for (index in 0..lines.lastIndex - 2) {
        if (isAsciiTableBorder(lines[index]) &&
            lines[index + 1].trim().startsWith("|") &&
            isAsciiTableBorder(lines[index + 2])
        ) {
            return true
        }
    }

    return false
}

private fun isAsciiTableBorder(line: String): Boolean =
    Regex("""^\+(?:-+\+){2,}$""").matches(line.trim())

@Composable
private fun StreamingMessageText(
    text: String,
    contentColor: androidx.compose.ui.graphics.Color,
    highlightQuery: String,
    activeOccurrenceIndex: Int?,
    onActiveSearchTargetPositioned: (Float) -> Unit,
) {
    val inactiveHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.88f)
    val activeHighlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f)
    val displayText = remember(text, highlightQuery, activeOccurrenceIndex, contentColor, inactiveHighlightColor, activeHighlightColor) {
        buildHighlightedAnnotatedString(
            text = text,
            query = highlightQuery,
            textColor = contentColor,
            inactiveHighlightColor = inactiveHighlightColor,
            activeHighlightColor = activeHighlightColor,
            activeOccurrenceIndex = activeOccurrenceIndex,
        )
    }
    Text(
        text = displayText,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (highlightQuery.isNotBlank() && activeOccurrenceIndex != null) {
                    onActiveSearchTargetPositioned(
                        coordinates.positionInRoot().y + coordinates.size.height / 2f,
                    )
                }
            },
        style = MaterialTheme.typography.bodyLarge,
        color = contentColor,
        softWrap = true,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun ActivityTimeline(
    activityLog: List<ChatActivity>,
) {
    val displayActivityLog = remember(activityLog) {
        compressActivityLog(activityLog)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        displayActivityLog.forEach { activity ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (activity.status == "running") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = activityLabel(activity),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (activity.status == "running") {
                    InlineActivityDots()
                }
            }
        }
    }
}

@Composable
private fun InlineActivityDots() {
    val transition = rememberInfiniteTransition(label = "activity")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "activity-alpha-$index",
            )
            Spacer(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun ReasoningSummarySection(
    messageId: Long,
    summary: String,
    contentColor: androidx.compose.ui.graphics.Color,
    highlightQuery: String,
    activeOccurrenceIndex: Int?,
    forceExpanded: Boolean,
    onActiveSearchTargetPositioned: (Float) -> Unit,
    onLongPress: () -> Unit,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }

    LaunchedEffect(forceExpanded, highlightQuery, activeOccurrenceIndex) {
        if (forceExpanded && highlightQuery.isNotBlank() && activeOccurrenceIndex != null) {
            expanded = true
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noHapticPressGesture(
                        onClick = { expanded = !expanded },
                        onLongPress = onLongPress,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    )
                    Text(
                        text = stringResource(R.string.reasoning_summary),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_reasoning_summary else R.string.expand_reasoning_summary,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                MarkdownText(
                    markdown = summary,
                    contentColor = contentColor,
                    highlightQuery = highlightQuery,
                    activeOccurrenceIndex = activeOccurrenceIndex,
                    onActiveSearchTargetPositioned = onActiveSearchTargetPositioned,
                )
            }
        }
    }
}

private data class TextMatch(
    val start: Int,
    val endExclusive: Int,
)

private fun findTextMatches(
    text: String,
    query: String,
): List<TextMatch> {
    if (query.isBlank()) return emptyList()

    val matches = mutableListOf<TextMatch>()
    var startIndex = 0
    while (startIndex < text.length) {
        val matchIndex = text.indexOf(
            string = query,
            startIndex = startIndex,
            ignoreCase = true,
        )
        if (matchIndex < 0) break
        matches += TextMatch(
            start = matchIndex,
            endExclusive = matchIndex + query.length,
        )
        startIndex = matchIndex + query.length
    }
    return matches
}

private fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    textColor: Color,
    inactiveHighlightColor: Color,
    activeHighlightColor: Color,
    activeOccurrenceIndex: Int?,
) = buildAnnotatedString {
    val matches = findTextMatches(text, query)
    if (matches.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    var cursor = 0
    matches.forEachIndexed { index, match ->
        if (cursor < match.start) {
            append(text.substring(cursor, match.start))
        }
        withStyle(
            SpanStyle(
                color = textColor,
                background = if (index == activeOccurrenceIndex) {
                    activeHighlightColor
                } else {
                    inactiveHighlightColor
                },
            ),
        ) {
            append(text.substring(match.start, match.endExclusive))
        }
        cursor = match.endExclusive
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

@Composable
private fun MessageImagesRow(
    imagePaths: List<String>,
    onLongPress: () -> Unit,
) {
    var previewPath by remember { mutableStateOf<String?>(null) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(imagePaths, key = { it }) { path ->
            AttachmentThumbnail(
                path = path,
                modifier = Modifier.size(116.dp),
                onClick = { previewPath = path },
                onLongPress = onLongPress,
            )
        }
    }

    previewPath?.let { path ->
        ImagePreviewDialog(
            path = path,
            onDismiss = { previewPath = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val thumbnailSizePx = with(density) {
        val longestEdge = maxOf(
            72.dp.roundToPx(),
            116.dp.roundToPx(),
        )
        longestEdge * 2
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, thumbnailSizePx) {
        value = withContext(Dispatchers.IO) {
            loadBitmap(
                path = path,
                targetWidthPx = thumbnailSizePx,
                targetHeightPx = thumbnailSizePx,
            )?.asImageBitmap()
        }
    }

    Surface(
        modifier = when {
            onClick != null || onLongPress != null -> {
                modifier.noHapticPressGesture(
                    onClick = onClick,
                    onLongPress = onLongPress,
                )
            }
            else -> modifier
        },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    path: String,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val previewWidthPx = with(density) {
        configuration.screenWidthDp.dp.roundToPx().coerceAtLeast(1) * 2
    }
    val previewHeightPx = with(density) {
        configuration.screenHeightDp.dp.roundToPx().coerceAtLeast(1) * 2
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, previewWidthPx, previewHeightPx) {
        value = withContext(Dispatchers.IO) {
            loadBitmap(
                path = path,
                targetWidthPx = previewWidthPx,
                targetHeightPx = previewHeightPx,
            )?.asImageBitmap()
        }
    }
    var scale by remember(path) { mutableStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(path) { mutableStateOf(IntSize.Zero) }

    fun boundedOffset(target: Offset, targetScale: Float): Offset {
        if (containerSize == IntSize.Zero || targetScale <= 1f) {
            return Offset.Zero
        }
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .padding(12.dp)
                .onSizeChanged { containerSize = it }
                .pointerInput(path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        val nextOffset = if (nextScale <= 1f) {
                            Offset.Zero
                        } else {
                            boundedOffset(offset + pan, nextScale)
                        }
                        scale = nextScale
                        offset = nextOffset
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = Mist,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close_preview),
                    tint = Mist,
                )
            }
        }
    }
}

private fun loadBitmap(
    path: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
): Bitmap? {
    return runCatching {
        ImageProcessing.loadBitmapForDisplay(
            path = path,
            targetWidthPx = targetWidthPx,
            targetHeightPx = targetHeightPx,
        )
    }.getOrNull()
}

private data class CameraImageCapture(
    val uri: Uri,
    val path: String,
)

private fun createCameraImageCapture(context: Context): CameraImageCapture? {
    val imageDirectory = File(context.cacheDir, "captured-images").apply { mkdirs() }
    val imageFile = File(imageDirectory, "${UUID.randomUUID()}.jpg")
    return runCatching {
        CameraImageCapture(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile,
            ),
            path = imageFile.absolutePath,
        )
    }.getOrNull()
}

private fun deleteCapturedImage(path: String) {
    runCatching {
        File(path).delete()
    }
}

private fun Modifier.noHapticPressGesture(
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
): Modifier = composed {
    pointerInput(onClick, onLongPress) {
        detectTapGestures(
            onTap = { onClick?.invoke() },
            onLongPress = { onLongPress?.invoke() },
        )
    }
}

private fun previousMessageIndex(
    listState: LazyListState,
    items: List<ConversationListItem>,
): Int? {
    if (items.isEmpty()) return null

    val currentListIndex = listState.firstVisibleItemIndex.coerceIn(items.indices)
    val currentMessageIndex = items[currentListIndex].messageIndex
    val currentMessageFirstListIndex = items.indexOfFirst { it.messageIndex == currentMessageIndex }
    if (currentListIndex > currentMessageFirstListIndex || listState.firstVisibleItemScrollOffset > 0) {
        return currentMessageFirstListIndex
    }

    return items.indexOfFirst { it.messageIndex == currentMessageIndex - 1 }
        .takeIf { it >= 0 }
}

private fun nextMessageIndex(
    listState: LazyListState,
    items: List<ConversationListItem>,
): Int? {
    if (items.isEmpty()) return null

    val currentListIndex = listState.firstVisibleItemIndex.coerceIn(items.indices)
    val currentMessageIndex = items[currentListIndex].messageIndex
    return items.indexOfFirst { it.messageIndex > currentMessageIndex }
        .takeIf { it >= 0 }
}

private fun resolveSearchTargetCenterInViewport(
    listState: LazyListState,
    messageIndex: Int,
    searchViewport: SearchViewport,
    activeSearchTargetCenterY: Float?,
): Float? {
    resolvePreciseSearchTargetCenterInViewport(
        searchViewport = searchViewport,
        activeSearchTargetCenterY = activeSearchTargetCenterY,
    )?.let { return it }

    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == messageIndex }
        ?: return null
    return itemInfo.offset + itemInfo.size / 2f
}

private const val SEARCH_CENTERING_TOLERANCE_PX = 24f
private const val SEARCH_TARGET_WAIT_FRAMES = 12
private const val SEARCH_REFINEMENT_WAIT_FRAMES = 8

private fun resolvePreciseSearchTargetCenterInViewport(
    searchViewport: SearchViewport?,
    activeSearchTargetCenterY: Float?,
): Float? {
    if (searchViewport == null || activeSearchTargetCenterY == null) return null
    return activeSearchTargetCenterY - searchViewport.topY
}

private suspend fun scrollToConversationBottom(
    listState: LazyListState,
    lastMessageIndex: Int,
    animated: Boolean,
) {
    if (lastMessageIndex < 0) return

    if (animated) {
        listState.animateScrollToItem(lastMessageIndex, Int.MAX_VALUE)
    } else {
        listState.scrollToItem(lastMessageIndex, Int.MAX_VALUE)
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "reply-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "reply-loading-phase",
    )
    val barHeights = remember { listOf(9.dp, 15.dp, 12.dp, 17.dp) }

    Row(
        modifier = Modifier.padding(start = 2.dp, end = 48.dp, top = 10.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            barHeights.forEachIndexed { index, barHeight ->
                val wave = ((sin(phase - index * 0.82f) + 1f) / 2f)
                Spacer(
                    modifier = Modifier
                        .size(width = 3.dp, height = barHeight)
                        .graphicsLayer {
                            scaleY = 0.38f + wave * 0.62f
                            alpha = 0.48f + wave * 0.52f
                        }
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            text = stringResource(R.string.chat_reply_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionDialog(
    message: ChatMessage,
    canGenerateReply: Boolean,
    onGenerateReply: () -> Unit,
    onCopy: () -> Unit,
    onBranch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (message.role == MessageRole.ASSISTANT) {
                        R.string.message_action_title_assistant
                    } else {
                        R.string.message_action_title_user
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.message_action_hint),
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            MessageActionButton(
                icon = Icons.Rounded.Autorenew,
                text = stringResource(
                    if (message.role == MessageRole.ASSISTANT) {
                        R.string.message_regenerate_reply
                    } else {
                        R.string.message_generate_reply
                    },
                ),
                onClick = onGenerateReply,
                enabled = canGenerateReply,
            )
            MessageActionButton(
                icon = Icons.Rounded.ContentCopy,
                text = stringResource(R.string.copy),
                onClick = onCopy,
            )
            MessageActionButton(
                icon = Icons.AutoMirrored.Rounded.AltRoute,
                text = stringResource(R.string.message_branch),
                onClick = onBranch,
            )
            MessageActionButton(
                icon = Icons.Rounded.Edit,
                text = stringResource(R.string.edit),
                onClick = onEdit,
            )
            MessageActionButton(
                icon = Icons.Rounded.DeleteOutline,
                text = stringResource(R.string.delete),
                onClick = onDelete,
                destructive = true,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            MessageActionButton(
                icon = Icons.Rounded.Close,
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun activityLabel(activity: ChatActivity): String = when (activity.label) {
    "search" -> pluralStringResource(
        R.plurals.activity_search_count,
        activity.count.coerceAtLeast(1),
        activity.count.coerceAtLeast(1),
    )
    else -> activity.label
}

private fun compressActivityLog(activityLog: List<ChatActivity>): List<ChatActivity> {
    if (activityLog.isEmpty()) return emptyList()

    val compressed = mutableListOf<ChatActivity>()
    activityLog.forEach { activity ->
        val normalized = activity.copy(count = activity.count.coerceAtLeast(1))
        val last = compressed.lastOrNull()
        if (last != null && last.label == normalized.label) {
            compressed[compressed.lastIndex] = last.copy(
                status = normalized.status,
                count = last.count + normalized.count,
            )
        } else {
            compressed += normalized
        }
    }
    return compressed
}
