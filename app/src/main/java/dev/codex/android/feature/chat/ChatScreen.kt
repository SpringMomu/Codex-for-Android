package dev.codex.android.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import dev.codex.android.data.model.ChatActivity
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.MessageRole
import dev.codex.android.ui.format.formatTimestamp
import dev.codex.android.ui.markdown.MarkdownText
import dev.codex.android.ui.markdown.containsLikelyLatex
import dev.codex.android.ui.theme.Mist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ChatRoute(
    container: AppContainer,
    conversationId: Long?,
    sessionNonce: Int,
    onConversationCreated: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit,
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
        onOpenSettings = onOpenSettings,
        onUpdateMessage = viewModel::updateMessage,
        onDeleteMessage = viewModel::deleteMessage,
        onPersistScrollPosition = viewModel::persistScrollPosition,
        onRetryMessage = viewModel::retryFailedMessage,
        onStopStreaming = viewModel::stopStreaming,
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
    onOpenSettings: () -> Unit,
    onUpdateMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onPersistScrollPosition: (Long?, Int, Int) -> Unit,
    onRetryMessage: (Long) -> Unit,
    onStopStreaming: () -> Unit,
) {
    val context = LocalContext.current
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

    LaunchedEffect(uiState.activeConversationId, uiState.messages, uiState.savedScrollPosition) {
        if (hasRestoredScroll || uiState.messages.isEmpty()) return@LaunchedEffect

        val savedPosition = uiState.savedScrollPosition
        val targetIndex = when {
            savedPosition?.anchorMessageId != null -> {
                uiState.messages.indexOfFirst { it.id == savedPosition.anchorMessageId }
                    .takeIf { it >= 0 }
            }
            savedPosition != null -> {
                savedPosition.firstVisibleItemIndex.coerceIn(0, uiState.messages.lastIndex)
            }
            else -> uiState.messages.lastIndex
        }

        withFrameNanos { }
        listState.scrollToItem(
            index = targetIndex ?: uiState.messages.lastIndex,
            scrollOffset = savedPosition?.firstVisibleItemScrollOffset?.coerceAtLeast(0) ?: 0,
        )
        lastObservedMessageCount = uiState.messages.size
        hasRestoredScroll = true
    }

    LaunchedEffect(uiState.activeConversationId, uiState.messages.size, uiState.streamingMessageId, hasRestoredScroll) {
        if (!hasRestoredScroll) return@LaunchedEffect

        val currentCount = uiState.messages.size
        val previousCount = lastObservedMessageCount
        val hasNewMessages = currentCount > previousCount
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
        val totalItemCount = currentCount + if (uiState.streamingMessageId != null) 1 else 0
        val isNearBottom = totalItemCount == 0 || lastVisibleIndex >= totalItemCount - 2

        if (hasNewMessages && (previousCount == 0 || isNearBottom)) {
            withFrameNanos { }
            listState.animateScrollToItem((totalItemCount - 1).coerceAtLeast(0))
        }

        lastObservedMessageCount = currentCount
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(uiState.activeConversationId, uiState.messages) {
        if (uiState.activeConversationId == null || uiState.messages.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            Triple(
                uiState.messages.getOrNull(listState.firstVisibleItemIndex)?.id,
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

    DisposableEffect(uiState.activeConversationId, uiState.messages) {
        onDispose {
            if (uiState.activeConversationId != null && uiState.messages.isNotEmpty()) {
                onPersistScrollPosition(
                    uiState.messages.getOrNull(listState.firstVisibleItemIndex)?.id,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedImagePaths.isNotEmpty()) {
                    SelectedImagesRow(
                        imagePaths = selectedImagePaths,
                        onRemove = onRemoveSelectedImage,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.92f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            IconButton(
                                onClick = { showImageSourceDialog = true },
                                enabled = !uiState.isSending,
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = stringResource(R.string.add_image),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 34.dp)
                                .padding(horizontal = 2.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.merge(
                                    TextStyle(color = MaterialTheme.colorScheme.onSurface),
                                ),
                                minLines = 1,
                                maxLines = 4,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.isSending) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ) {
                            IconButton(
                                onClick = if (uiState.isSending) onStopStreaming else onSend,
                                enabled = uiState.isSending || draft.isNotBlank() || selectedImagePaths.isNotEmpty(),
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSending) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(
                                        if (uiState.isSending) R.string.stop_generation else R.string.send,
                                    ),
                                    tint = if (uiState.isSending) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
) { innerPadding ->
    val modelPlaceholder = stringResource(R.string.model_not_configured)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        ) {
            ChatHeader(
                modelName = uiState.modelAlias.ifBlank { modelPlaceholder },
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                onNewConversation = onNewConversation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
            )
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
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, message -> message.id },
                        ) { _, message ->
                            MessageBubble(
                                message = message,
                                isStreaming = uiState.streamingMessageId == message.id &&
                                    message.role == MessageRole.ASSISTANT,
                                canRetry = message.isError &&
                                    message.role == MessageRole.ASSISTANT &&
                                    message.id == lastMessageId &&
                                    !uiState.isSending,
                                onRetry = { onRetryMessage(message.id) },
                                onLongPress = { actionMessage = message },
                            )
                        }
                        if (uiState.streamingMessageId != null) {
                            item(key = "typing-indicator") {
                                TypingIndicatorBubble()
                            }
                        }
                    }

                    if (uiState.messages.isNotEmpty()) {
                        QuickScrollControls(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp, bottom = 28.dp),
                            canScrollUp = listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 0,
                            canScrollDown = listState.canScrollForward,
                            onScrollUp = {
                                val target = previousMessageIndex(
                                    listState = listState,
                                    messageCount = uiState.messages.size,
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
                                    messageCount = uiState.messages.size,
                                )
                                coroutineScope.launch {
                                    if (target != null) {
                                        listState.animateScrollToItem(target)
                                    } else {
                                        scrollToConversationBottom(
                                            listState = listState,
                                            lastMessageIndex = uiState.messages.lastIndex,
                                            animated = true,
                                        )
                                    }
                                }
                            },
                            onLongScrollDown = {
                                coroutineScope.launch {
                                    scrollToConversationBottom(
                                        listState = listState,
                                        lastMessageIndex = (uiState.messages.size - 1).coerceAtLeast(0),
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

    actionMessage?.let { message ->
        MessageActionDialog(
            message = message,
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
    modelName: String,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "ChatGPT",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = modelName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HeaderActionButton(
            icon = Icons.Rounded.History,
            contentDescription = stringResource(R.string.open_history),
            onClick = onOpenHistory,
        )
        HeaderActionButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.open_settings),
            onClick = onOpenSettings,
        )
        HeaderActionButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.start_new_conversation),
            onClick = onNewConversation,
        )
    }
}

@Composable
private fun HeaderActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
        )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.92f)),
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.size(34.dp),
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
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.empty_chat_title)
    var visibleLength by remember(title) { mutableStateOf(0) }

    LaunchedEffect(title) {
        visibleLength = 0
        title.forEachIndexed { index, _ ->
            visibleLength = index + 1
            kotlinx.coroutines.delay(88)
        }
    }

    val transition = rememberInfiniteTransition(label = "empty-state-cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.keyframes {
                durationMillis = 960
                1f at 0
                1f at 480
                0f at 481
                0f at 960
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "empty-state-cursor-alpha",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.take(visibleLength),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 24.dp)
                    .graphicsLayer { alpha = cursorAlpha }
                    .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp)),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onLongPress: () -> Unit,
) {
    val isAssistant = message.role == MessageRole.ASSISTANT
    val background = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.role == MessageRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        isAssistant -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        message.isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
        message.role == MessageRole.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.92f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .noHapticPressGesture(onLongPress = onLongPress),
            colors = CardDefaults.cardColors(containerColor = background),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isAssistant) 10.dp else 24.dp,
                bottomEnd = if (isAssistant) 24.dp else 10.dp,
            ),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (message.activityLog.isNotEmpty()) {
                        ActivityTimeline(message.activityLog)
                    }
                    if (message.reasoningSummary.isNotBlank()) {
                        ReasoningSummarySection(
                            messageId = message.id,
                            summary = message.reasoningSummary,
                            contentColor = contentColor,
                            onLongPress = onLongPress,
                        )
                    }
                    if (message.imagePaths.isNotEmpty()) {
                        MessageImagesRow(
                            imagePaths = message.imagePaths,
                            onLongPress = onLongPress,
                        )
                    }
                    if (message.content.isNotBlank()) {
                        MessageBody(
                            message = message,
                            isStreaming = isStreaming,
                            contentColor = contentColor,
                            onLongPress = onLongPress,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTimestamp(message.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.56f),
                    )
                    if (canRetry) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
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

@Composable
private fun MessageBody(
    message: ChatMessage,
    isStreaming: Boolean,
    contentColor: Color,
    onLongPress: () -> Unit,
) {
    if (isStreaming) {
        StreamingMessageText(
            text = message.content,
            contentColor = contentColor,
        )
        return
    }

    if (shouldRenderWithMarkdown(message)) {
        MarkdownText(
            markdown = message.content,
            contentColor = contentColor,
            onLongPress = onLongPress,
        )
    } else {
        PlainMessageText(
            text = message.content,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun PlainMessageText(
    text: String,
    contentColor: Color,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
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
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        activityLog.forEachIndexed { index, activity ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Text(
                            text = activityLabel(activity.label),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    if (activity.status == "running") {
                        InlineActivityDots()
                    } else {
                        Spacer(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                ),
                        )
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReasoningSummarySection(
    messageId: Long,
    summary: String,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.88f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                Text(
                    text = stringResource(R.string.reasoning_summary),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                MarkdownText(
                    markdown = summary,
                    contentColor = contentColor,
                )
            }
        }
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
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            loadBitmap(context, path)?.asImageBitmap()
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
        shape = RoundedCornerShape(18.dp),
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
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            loadBitmap(context, path)?.asImageBitmap()
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
                .background(Color.Black.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
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
    context: Context,
    path: String,
): Bitmap? {
    val file = File(path)
    if (!file.exists() || file.length() == 0L) return null

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.setOnPartialImageListener { exception ->
                    exception.error == ImageDecoder.DecodeException.SOURCE_INCOMPLETE
                }
            }
        } else {
            BitmapFactory.decodeFile(file.absolutePath)
        }
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
    messageCount: Int,
): Int? {
    if (messageCount == 0) return null

    val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, messageCount - 1)
    return if (listState.firstVisibleItemScrollOffset > 0) {
        currentIndex
    } else {
        (currentIndex - 1).takeIf { it >= 0 }
    }
}

private fun nextMessageIndex(
    listState: LazyListState,
    messageCount: Int,
): Int? {
    if (messageCount == 0) return null

    val currentIndex = listState.firstVisibleItemIndex.coerceIn(0, messageCount - 1)
    return (currentIndex + 1).takeIf { it < messageCount }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 480, delayMillis = index * 120),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "typing-alpha-$index",
                )
                val translateY by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 480, delayMillis = index * 120),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "typing-offset-$index",
                )
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            translationY = translateY
                        }
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun MessageActionDialog(
    message: ChatMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.edit))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.delete))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun activityLabel(label: String): String = when (label) {
    "search" -> stringResource(R.string.activity_search)
    else -> label
}
