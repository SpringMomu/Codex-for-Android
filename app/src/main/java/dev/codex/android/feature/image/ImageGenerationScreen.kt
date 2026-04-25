package dev.codex.android.feature.image

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.android.R
import dev.codex.android.core.di.AppContainer
import dev.codex.android.core.media.ImageProcessing
import dev.codex.android.data.model.ImageGeneration
import dev.codex.android.data.model.ImageGenerationStatus
import dev.codex.android.ui.format.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImageGenerationRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: ImageGenerationViewModel = viewModel(
        factory = imageGenerationViewModelFactory(container),
    )
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.saveResult.collect { saved ->
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.image_saved_to_gallery else R.string.image_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    ImageGenerationScreen(
        uiState = uiState,
        prompt = viewModel.prompt,
        referenceImagePath = viewModel.referenceImagePath,
        isImportingReferenceImage = viewModel.isImportingReferenceImage,
        onPromptChange = viewModel::updatePrompt,
        onReferenceSelected = viewModel::importReferenceImage,
        onRemoveReference = viewModel::removeReferenceImage,
        onGenerate = viewModel::generate,
        onRetry = viewModel::retry,
        onStop = viewModel::stop,
        onDelete = viewModel::delete,
        onSave = viewModel::saveToGallery,
        onBack = onBack,
    )
}

@Composable
private fun ImageGenerationScreen(
    uiState: ImageGenerationUiState,
    prompt: String,
    referenceImagePath: String?,
    isImportingReferenceImage: Boolean,
    onPromptChange: (String) -> Unit,
    onReferenceSelected: (Uri?) -> Unit,
    onRemoveReference: () -> Unit,
    onGenerate: () -> Unit,
    onRetry: (Long) -> Unit,
    onStop: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = onReferenceSelected,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ImageHeader(onBack = onBack)
            ImageComposer(
                prompt = prompt,
                referenceImagePath = referenceImagePath,
                isImportingReferenceImage = isImportingReferenceImage,
                canGenerate = prompt.isNotBlank() && uiState.hasCredentials,
                onPromptChange = onPromptChange,
                onPickReference = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveReference = onRemoveReference,
                onGenerate = onGenerate,
            )
            Text(
                text = stringResource(R.string.image_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (uiState.generations.isEmpty()) {
                EmptyImageHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(uiState.generations, key = { it.id }) { generation ->
                        ImageGenerationCard(
                            generation = generation,
                            isActive = uiState.activeGenerationIds.contains(generation.id),
                            onRetry = { onRetry(generation.id) },
                            onStop = { onStop(generation.id) },
                            onDelete = { onDelete(generation.id) },
                            onSave = onSave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.image_mode_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.image_mode_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageComposer(
    prompt: String,
    referenceImagePath: String?,
    isImportingReferenceImage: Boolean,
    canGenerate: Boolean,
    onPromptChange: (String) -> Unit,
    onPickReference: () -> Unit,
    onRemoveReference: () -> Unit,
    onGenerate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                placeholder = { Text(stringResource(R.string.image_prompt_placeholder)) },
            )
            if (isImportingReferenceImage) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.image_reference_importing),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            referenceImagePath?.let { path ->
                Box(modifier = Modifier.size(96.dp)) {
                    LocalImageThumbnail(
                        path = path,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(24.dp)
                            .clickable { onRemoveReference() },
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.remove_image),
                            tint = Color.White,
                            modifier = Modifier.padding(5.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.image_reference_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPickReference) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.image_reference_pick))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onGenerate,
                    enabled = canGenerate,
                ) {
                    Text(stringResource(R.string.image_generate))
                }
            }
        }
    }
}

@Composable
private fun EmptyImageHistory() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.image_history_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageGenerationCard(
    generation: ImageGeneration,
    isActive: Boolean,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String) -> Unit,
) {
    var previewPath by remember { mutableStateOf<String?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                generation.referenceImagePath?.let { path ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = stringResource(R.string.image_reference_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LocalImageThumbnail(
                            path = path,
                            modifier = Modifier.size(82.dp),
                        )
                    }
                }
                generation.generatedImagePath?.let { path ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = stringResource(R.string.image_result_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LocalImageThumbnail(
                            path = path,
                            modifier = Modifier
                                .size(132.dp)
                                .clickable { previewPath = path },
                        )
                    }
                }
            }
            Text(
                text = generation.prompt,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (generation.errorMessage.isNotBlank()) {
                Text(
                    text = generation.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = statusText(generation.status, isActive),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimestamp(generation.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isActive) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop_generation))
                    }
                }
                if (generation.status == ImageGenerationStatus.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.retry))
                    }
                }
                generation.generatedImagePath?.let { path ->
                    IconButton(onClick = { onSave(path) }) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(R.string.save))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }

    previewPath?.let { path ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { previewPath = null }) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Black) {
                LocalImageThumbnail(
                    path = path,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 620.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun LocalImageThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val density = LocalDensity.current
    val targetPx = with(density) { 640.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = withContext(Dispatchers.IO) {
            ImageProcessing.loadBitmapForDisplay(path, targetPx, targetPx)?.asImageBitmap()
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null)
            }
        }
    }
}

@Composable
private fun statusText(
    status: ImageGenerationStatus,
    isActive: Boolean,
): String = when {
    isActive -> stringResource(R.string.image_status_running)
    status == ImageGenerationStatus.QUEUED -> stringResource(R.string.image_status_queued)
    status == ImageGenerationStatus.RUNNING -> stringResource(R.string.image_status_running)
    status == ImageGenerationStatus.SUCCEEDED -> stringResource(R.string.image_status_succeeded)
    status == ImageGenerationStatus.FAILED -> stringResource(R.string.image_status_failed)
    else -> status.name
}
