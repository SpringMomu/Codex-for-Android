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
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
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
import dev.codex.android.ui.theme.ErrorSoft
import dev.codex.android.ui.theme.Fog
import dev.codex.android.ui.theme.Ink
import dev.codex.android.ui.theme.Mist
import dev.codex.android.ui.theme.Panel
import dev.codex.android.ui.theme.PanelStrong
import dev.codex.android.ui.theme.Slate
import kotlinx.coroutines.Dispatchers
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
        onRetryMessage = viewModel::retryFailedMessage,
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
    onRetryMessage: (Long) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        onAddImages(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        val path = pendingCameraPath
        pendingCameraUri = null
        pendingCameraPath = null
        if (success && uri != null) {
            onAddImages(listOf(uri))
        } else if (path != null) {
            deleteCapturedImage(path)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
        bottomBar = {
            Surface(
                color = PanelStrong,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedImagePaths.isNotEmpty()) {
                        SelectedImagesRow(
                            imagePaths = selectedImagePaths,
                            onRemove = onRemoveSelectedImage,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        OutlinedIconButton(
                            onClick = { showImageSourceDialog = true },
                            enabled = !uiState.isSending,
                            border = BorderStroke(1.dp, Ink),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = stringResource(R.string.add_image),
                                tint = Ink,
                            )
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(20.dp),
                            minLines = 1,
                            maxLines = 5,
                        )
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (uiState.isSending) Fog else MaterialTheme.colorScheme.primary,
                        ) {
                            IconButton(
                                onClick = onSend,
                                enabled = !uiState.isSending && (draft.isNotBlank() || selectedImagePaths.isNotEmpty()),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.send),
                                    tint = if (uiState.isSending) Slate else MaterialTheme.colorScheme.onPrimary,
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
                    .padding(top = 8.dp, bottom = 10.dp),
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
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
                    item {
                        AnimatedVisibility(visible = uiState.streamingMessageId != null) {
                            TypingIndicatorBubble()
                        }
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
        EditMessageDialog(
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
                                pendingCameraUri = capture.uri
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
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ChatGPT",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = modelName,
                color = Slate,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedIconButton(onClick = onOpenHistory) {
            Icon(imageVector = Icons.Rounded.History, contentDescription = stringResource(R.string.open_history))
        }
        OutlinedIconButton(onClick = onOpenSettings) {
            Icon(imageVector = Icons.Rounded.Settings, contentDescription = stringResource(R.string.open_settings))
        }
        OutlinedIconButton(onClick = onNewConversation) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.start_new_conversation))
        }
    }
}

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "empty-state-cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "empty-state-cursor-alpha",
    )

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.empty_chat_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 28.dp)
                    .graphicsLayer { alpha = cursorAlpha }
                    .background(Ink, RoundedCornerShape(2.dp)),
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
                    color = Ink.copy(alpha = 0.72f),
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
    val isUser = message.role == MessageRole.USER
    val background = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> Mist
        isAssistant -> PanelStrong
        else -> Panel
    }
    val contentColor = when {
        message.isError -> ErrorSoft
        else -> MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (isAssistant) Alignment.Start else Alignment.End
    val border = if (isUser && !message.isError) BorderStroke(1.dp, Fog) else null

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = minOf(maxWidth * 0.92f, 420.dp)
        val bubbleModifier = messageBubbleModifier(
            bubbleMaxWidth = bubbleMaxWidth,
            message = message,
            isStreaming = isStreaming,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment,
        ) {
            Card(
                modifier = bubbleModifier,
                colors = CardDefaults.cardColors(containerColor = background),
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = if (isAssistant) 8.dp else 24.dp,
                    bottomEnd = if (isAssistant) 24.dp else 8.dp,
                ),
                border = border,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onLongClick = onLongPress,
                            )
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (message.activityLog.isNotEmpty()) {
                            ActivityTimeline(message.activityLog)
                        }
                        if (message.reasoningSummary.isNotBlank()) {
                            ReasoningSummarySection(
                                messageId = message.id,
                                summary = message.reasoningSummary,
                                contentColor = contentColor,
                            )
                        }
                        if (message.imagePaths.isNotEmpty()) {
                            MessageImagesRow(message.imagePaths)
                        }
                        if (message.content.isNotBlank()) {
                            if (isStreaming) {
                                StreamingMessageText(
                                    text = message.content,
                                    contentColor = contentColor,
                                )
                            } else {
                                MarkdownText(
                                    markdown = message.content,
                                    contentColor = contentColor,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTimestamp(message.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.68f),
                        )
                        if (canRetry) {
                            TextButton(
                                onClick = onRetry,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
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

private fun messageBubbleModifier(
    bubbleMaxWidth: Dp,
    message: ChatMessage,
    isStreaming: Boolean,
): Modifier {
    val shouldUseFixedWidth = isStreaming ||
        message.content.length > 160 ||
        message.content.contains('\n') ||
        message.imagePaths.isNotEmpty() ||
        message.reasoningSummary.isNotBlank() ||
        message.activityLog.isNotEmpty()

    return if (shouldUseFixedWidth) {
        Modifier.width(bubbleMaxWidth)
    } else {
        Modifier.widthIn(max = bubbleMaxWidth)
    }
}

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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        activityLog.forEachIndexed { index, activity ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Slate.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = activityLabel(activity.label),
                        color = Slate,
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
                                color = Fog,
                                shape = CircleShape,
                            ),
                    )
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
                        color = Slate,
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
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Fog.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.reasoning_summary),
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_reasoning_summary else R.string.expand_reasoning_summary,
                    ),
                    tint = Slate,
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
) {
    var previewPath by remember { mutableStateOf<String?>(null) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(imagePaths, key = { it }) { path ->
            AttachmentThumbnail(
                path = path,
                modifier = Modifier.size(132.dp),
                onClick = { previewPath = path },
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

@Composable
private fun AttachmentThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            loadBitmap(context, path)?.asImageBitmap()
        }
    }

    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(14.dp),
        color = Panel,
        border = BorderStroke(1.dp, Fog),
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
                    .background(Panel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = Slate,
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
                .background(Ink.copy(alpha = 0.94f), RoundedCornerShape(20.dp))
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
    if (!file.exists()) return null

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file))
    } else {
        BitmapFactory.decodeFile(file.absolutePath)
    }
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

@Composable
private fun TypingIndicatorBubble() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
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
                            color = Slate,
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
                    color = Slate,
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
private fun EditMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(message.id) { mutableStateOf(message.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (message.role == MessageRole.ASSISTANT) {
                        R.string.message_edit_title_assistant
                    } else {
                        R.string.message_edit_title_user
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun activityLabel(label: String): String = when (label) {
    "search" -> stringResource(R.string.activity_search)
    else -> label
}
