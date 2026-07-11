package dev.codex.android.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.android.R
import dev.codex.android.core.i18n.AppLanguage
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.AppSettings
import dev.codex.android.data.model.ChatProvider
import dev.codex.android.ui.format.formatTimestamp
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory(container))
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSave: (AppSettings) -> Unit,
) {
    var chatProvider by rememberSaveable(uiState.settings.chatProvider.storageValue) {
        mutableStateOf(uiState.settings.chatProvider.storageValue)
    }
    var codexBaseUrl by rememberSaveable(uiState.settings.codexBaseUrl) {
        mutableStateOf(uiState.settings.codexBaseUrl)
    }
    var codexApiKey by rememberSaveable(uiState.settings.codexApiKey) {
        mutableStateOf(uiState.settings.codexApiKey)
    }
    var claudeBaseUrl by rememberSaveable(uiState.settings.claudeBaseUrl) {
        mutableStateOf(uiState.settings.claudeBaseUrl)
    }
    var claudeApiKey by rememberSaveable(uiState.settings.claudeApiKey) {
        mutableStateOf(uiState.settings.claudeApiKey)
    }
    var imageBaseUrl by rememberSaveable(uiState.settings.imageBaseUrl) { mutableStateOf(uiState.settings.imageBaseUrl) }
    var imageApiKey by rememberSaveable(uiState.settings.imageApiKey) { mutableStateOf(uiState.settings.imageApiKey) }
    var selectedCodexModel by rememberSaveable(uiState.settings.selectedCodexModel) {
        mutableStateOf(uiState.settings.selectedCodexModel)
    }
    var selectedClaudeModel by rememberSaveable(uiState.settings.selectedClaudeModel) {
        mutableStateOf(uiState.settings.selectedClaudeModel)
    }
    var codexModels by rememberSaveable(uiState.settings.codexModels) {
        mutableStateOf(uiState.settings.codexModels)
    }
    var claudeModels by rememberSaveable(uiState.settings.claudeModels) {
        mutableStateOf(uiState.settings.claudeModels)
    }
    var maxContextTokens by rememberSaveable(uiState.settings.maxContextTokens) {
        mutableStateOf(uiState.settings.maxContextTokens.toString())
    }
    var languageTag by rememberSaveable(uiState.settings.languageTag) { mutableStateOf(uiState.settings.languageTag) }
    var reasoningEffort by rememberSaveable(uiState.settings.reasoningEffort) {
        mutableStateOf(reasoningEffortIndex(uiState.settings.reasoningEffort))
    }
    var systemPrompt by rememberSaveable(uiState.settings.systemPrompt) { mutableStateOf(uiState.settings.systemPrompt) }
    var codexKeyVisible by rememberSaveable { mutableStateOf(false) }
    var claudeKeyVisible by rememberSaveable { mutableStateOf(false) }
    var imageKeyVisible by rememberSaveable { mutableStateOf(false) }
    var imageSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var addModelProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var editingModelProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var editingModelAlias by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingModelProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingModelAlias by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardChangesDialog by rememberSaveable { mutableStateOf(false) }
    val selectedChatProvider = ChatProvider.fromStorage(chatProvider)
    val rawCodexModels = AppSettings.normalizeModels(codexModels, selectedCodexModel)
    val rawClaudeModels = AppSettings.normalizeModels(claudeModels, selectedClaudeModel)
    val effectiveSelectedCodexModel = selectedCodexModel.trim().ifBlank { rawCodexModels.firstOrNull().orEmpty() }
    val effectiveSelectedClaudeModel = selectedClaudeModel.trim().ifBlank { rawClaudeModels.firstOrNull().orEmpty() }
    val normalizedCodexModels = AppSettings.normalizeModels(
        models = rawCodexModels,
        selectedModel = effectiveSelectedCodexModel,
    )
    val normalizedClaudeModels = AppSettings.normalizeModels(
        models = rawClaudeModels,
        selectedModel = effectiveSelectedClaudeModel,
    )
    val currentSettings = AppSettings(
        chatProvider = selectedChatProvider,
        codexBaseUrl = codexBaseUrl.trim(),
        codexApiKey = codexApiKey.trim(),
        claudeBaseUrl = claudeBaseUrl.trim(),
        claudeApiKey = claudeApiKey.trim(),
        imageBaseUrl = imageBaseUrl.trim(),
        imageApiKey = imageApiKey.trim(),
        selectedCodexModel = effectiveSelectedCodexModel,
        selectedClaudeModel = effectiveSelectedClaudeModel,
        codexModels = normalizedCodexModels,
        claudeModels = normalizedClaudeModels,
        maxContextTokens = parseMaxContextTokens(maxContextTokens),
        reasoningEffort = reasoningEffortValue(reasoningEffort).trim(),
        systemPrompt = systemPrompt.trim(),
        languageTag = languageTag.trim().ifBlank { AppLanguage.SYSTEM.storageValue },
    )
    val savedSettings = uiState.settings.copy(
        codexBaseUrl = uiState.settings.codexBaseUrl.trim(),
        codexApiKey = uiState.settings.codexApiKey.trim(),
        claudeBaseUrl = uiState.settings.claudeBaseUrl.trim(),
        claudeApiKey = uiState.settings.claudeApiKey.trim(),
        imageBaseUrl = uiState.settings.imageBaseUrl.trim(),
        imageApiKey = uiState.settings.imageApiKey.trim(),
        selectedCodexModel = uiState.settings.selectedModelFor(ChatProvider.CODEX),
        selectedClaudeModel = uiState.settings.selectedModelFor(ChatProvider.CLAUDE),
        codexModels = uiState.settings.modelsFor(ChatProvider.CODEX),
        claudeModels = uiState.settings.modelsFor(ChatProvider.CLAUDE),
        reasoningEffort = uiState.settings.reasoningEffort.trim(),
        systemPrompt = uiState.settings.systemPrompt.trim(),
        languageTag = uiState.settings.languageTag.trim().ifBlank { AppLanguage.SYSTEM.storageValue },
    )
    val hasUnsavedChanges = currentSettings != savedSettings
    val attemptBack = {
        if (hasUnsavedChanges) {
            showDiscardChangesDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !showDiscardChangesDialog) {
        showDiscardChangesDialog = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!uiState.isSaving) {
                        onSave(currentSettings)
                    }
                },
                containerColor = if (uiState.isSaving) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (uiState.isSaving) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = stringResource(
                        if (uiState.isSaving) R.string.settings_saving else R.string.settings_save,
                    ),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            uiState.lastSavedAt?.let { lastSavedAt ->
                Text(
                    text = stringResource(R.string.settings_saved_at, formatTimestamp(lastSavedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ChatProviderField(
                selectedProvider = selectedChatProvider,
                onProviderChange = { chatProvider = it.storageValue },
                codexBaseUrl = codexBaseUrl,
                onCodexBaseUrlChange = { codexBaseUrl = it },
                codexApiKey = codexApiKey,
                onCodexApiKeyChange = { codexApiKey = it },
                codexKeyVisible = codexKeyVisible,
                onCodexKeyVisibleChange = { codexKeyVisible = it },
                claudeBaseUrl = claudeBaseUrl,
                onClaudeBaseUrlChange = { claudeBaseUrl = it },
                claudeApiKey = claudeApiKey,
                onClaudeApiKeyChange = { claudeApiKey = it },
                claudeKeyVisible = claudeKeyVisible,
                onClaudeKeyVisibleChange = { claudeKeyVisible = it },
            )
            ChatModelsField(
                selectedProvider = selectedChatProvider,
                codexModels = normalizedCodexModels,
                selectedCodexModel = effectiveSelectedCodexModel,
                onSelectedCodexModelChange = { selectedCodexModel = it },
                claudeModels = normalizedClaudeModels,
                selectedClaudeModel = effectiveSelectedClaudeModel,
                onSelectedClaudeModelChange = { selectedClaudeModel = it },
                onAddModel = { provider -> addModelProvider = provider.storageValue },
                onEditModel = { provider, modelAlias ->
                    editingModelProvider = provider.storageValue
                    editingModelAlias = modelAlias
                },
                onDeleteModel = { provider, modelAlias ->
                    deletingModelProvider = provider.storageValue
                    deletingModelAlias = modelAlias
                },
            )
            ImageEndpointSettingsField(
                expanded = imageSettingsExpanded,
                onExpandedChange = { imageSettingsExpanded = it },
                imageBaseUrl = imageBaseUrl,
                onImageBaseUrlChange = { imageBaseUrl = it },
                imageApiKey = imageApiKey,
                onImageApiKeyChange = { imageApiKey = it },
                imageKeyVisible = imageKeyVisible,
                onImageKeyVisibleChange = { imageKeyVisible = it },
            )
            SettingField(
                title = stringResource(R.string.settings_max_context_tokens),
                hint = AppSettings.DEFAULT_MAX_CONTEXT_TOKENS.toString(),
                value = maxContextTokens,
                onValueChange = { maxContextTokens = it.filter(Char::isDigit).take(9) },
                leadingIcon = Icons.Rounded.Memory,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            LanguageField(
                selectedLanguageTag = languageTag,
                onLanguageTagChange = { languageTag = it },
            )
            if (selectedChatProvider == ChatProvider.CODEX) {
                ReasoningEffortField(
                    selectedIndex = reasoningEffort,
                    onSelectedIndexChange = { reasoningEffort = it },
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.settings_system_prompt), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                    )
                }
            }
            Spacer(modifier = Modifier.heightIn(min = 88.dp))
        }
    }

    addModelProvider?.let { providerValue ->
        val provider = ChatProvider.fromStorage(providerValue)
        ModelAliasDialog(
            provider = provider,
            title = stringResource(R.string.settings_add_model_title, chatProviderLabel(provider)),
            initialModelAlias = "",
            onDismiss = { addModelProvider = null },
            onConfirm = { modelAlias ->
                val normalizedModelAlias = modelAlias.trim()
                when (provider) {
                    ChatProvider.CODEX -> {
                        codexModels = AppSettings.normalizeModels(
                            models = codexModels + normalizedModelAlias,
                            selectedModel = normalizedModelAlias,
                        )
                        selectedCodexModel = normalizedModelAlias
                    }
                    ChatProvider.CLAUDE -> {
                        claudeModels = AppSettings.normalizeModels(
                            models = claudeModels + normalizedModelAlias,
                            selectedModel = normalizedModelAlias,
                        )
                        selectedClaudeModel = normalizedModelAlias
                    }
                }
                addModelProvider = null
            },
        )
    }

    if (editingModelProvider != null && editingModelAlias != null) {
        val provider = ChatProvider.fromStorage(editingModelProvider)
        val oldModelAlias = editingModelAlias.orEmpty()
        ModelAliasDialog(
            provider = provider,
            title = stringResource(R.string.settings_edit_model_title, chatProviderLabel(provider)),
            initialModelAlias = oldModelAlias,
            onDismiss = {
                editingModelProvider = null
                editingModelAlias = null
            },
            onConfirm = { modelAlias ->
                val update = updateCustomModelAlias(
                    models = when (provider) {
                        ChatProvider.CODEX -> codexModels
                        ChatProvider.CLAUDE -> claudeModels
                    },
                    selectedModel = when (provider) {
                        ChatProvider.CODEX -> effectiveSelectedCodexModel
                        ChatProvider.CLAUDE -> effectiveSelectedClaudeModel
                    },
                    oldModelAlias = oldModelAlias,
                    newModelAlias = modelAlias,
                )
                when (provider) {
                    ChatProvider.CODEX -> {
                        codexModels = update.models
                        selectedCodexModel = update.selectedModel
                    }
                    ChatProvider.CLAUDE -> {
                        claudeModels = update.models
                        selectedClaudeModel = update.selectedModel
                    }
                }
                editingModelProvider = null
                editingModelAlias = null
            },
        )
    }

    if (deletingModelProvider != null && deletingModelAlias != null) {
        val provider = ChatProvider.fromStorage(deletingModelProvider)
        val modelAlias = deletingModelAlias.orEmpty()
        AlertDialog(
            onDismissRequest = {
                deletingModelProvider = null
                deletingModelAlias = null
            },
            title = { Text(stringResource(R.string.settings_delete_model_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_delete_model_message,
                        modelAlias,
                        chatProviderLabel(provider),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val update = deleteCustomModelAlias(
                            models = when (provider) {
                                ChatProvider.CODEX -> codexModels
                                ChatProvider.CLAUDE -> claudeModels
                            },
                            selectedModel = when (provider) {
                                ChatProvider.CODEX -> effectiveSelectedCodexModel
                                ChatProvider.CLAUDE -> effectiveSelectedClaudeModel
                            },
                            modelAlias = modelAlias,
                        )
                        when (provider) {
                            ChatProvider.CODEX -> {
                                codexModels = update.models
                                selectedCodexModel = update.selectedModel
                            }
                            ChatProvider.CLAUDE -> {
                                claudeModels = update.models
                                selectedClaudeModel = update.selectedModel
                            }
                        }
                        deletingModelProvider = null
                        deletingModelAlias = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletingModelProvider = null
                        deletingModelAlias = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text(stringResource(R.string.settings_unsaved_changes_title)) },
            text = { Text(stringResource(R.string.settings_unsaved_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesDialog = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.settings_discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesDialog = false }) {
                    Text(stringResource(R.string.settings_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun ChatProviderField(
    selectedProvider: ChatProvider,
    onProviderChange: (ChatProvider) -> Unit,
    codexBaseUrl: String,
    onCodexBaseUrlChange: (String) -> Unit,
    codexApiKey: String,
    onCodexApiKeyChange: (String) -> Unit,
    codexKeyVisible: Boolean,
    onCodexKeyVisibleChange: (Boolean) -> Unit,
    claudeBaseUrl: String,
    onClaudeBaseUrlChange: (String) -> Unit,
    claudeApiKey: String,
    onClaudeApiKeyChange: (String) -> Unit,
    claudeKeyVisible: Boolean,
    onClaudeKeyVisibleChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_chat_provider), style = MaterialTheme.typography.titleMedium)
            ChatProviderEndpointSection(
                provider = ChatProvider.CODEX,
                selectedProvider = selectedProvider,
                onProviderChange = onProviderChange,
                baseUrl = codexBaseUrl,
                onBaseUrlChange = onCodexBaseUrlChange,
                apiKey = codexApiKey,
                onApiKeyChange = onCodexApiKeyChange,
                keyVisible = codexKeyVisible,
                onKeyVisibleChange = onCodexKeyVisibleChange,
                baseUrlHint = AppSettings.DEFAULT_CODEX_BASE_URL,
            )
            ChatProviderEndpointSection(
                provider = ChatProvider.CLAUDE,
                selectedProvider = selectedProvider,
                onProviderChange = onProviderChange,
                baseUrl = claudeBaseUrl,
                onBaseUrlChange = onClaudeBaseUrlChange,
                apiKey = claudeApiKey,
                onApiKeyChange = onClaudeApiKeyChange,
                keyVisible = claudeKeyVisible,
                onKeyVisibleChange = onClaudeKeyVisibleChange,
                baseUrlHint = AppSettings.DEFAULT_CLAUDE_BASE_URL,
            )
        }
    }
}

@Composable
private fun ChatProviderEndpointSection(
    provider: ChatProvider,
    selectedProvider: ChatProvider,
    onProviderChange: (ChatProvider) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    keyVisible: Boolean,
    onKeyVisibleChange: (Boolean) -> Unit,
    baseUrlHint: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onProviderChange(provider) }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedProvider == provider,
                onClick = { onProviderChange(provider) },
            )
            Text(
                text = chatProviderLabel(provider),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_base_url)) },
            placeholder = { Text(baseUrlHint) },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Cloud, contentDescription = null) },
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_api_key)) },
            placeholder = { Text("sk-") },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(imageVector = Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { onKeyVisibleChange(!keyVisible) }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (keyVisible) R.string.hide else R.string.show),
                    )
                }
            },
            singleLine = true,
        )
    }
}

@Composable
private fun ChatModelsField(
    selectedProvider: ChatProvider,
    codexModels: List<String>,
    selectedCodexModel: String,
    onSelectedCodexModelChange: (String) -> Unit,
    claudeModels: List<String>,
    selectedClaudeModel: String,
    onSelectedClaudeModelChange: (String) -> Unit,
    onAddModel: (ChatProvider) -> Unit,
    onEditModel: (ChatProvider, String) -> Unit,
    onDeleteModel: (ChatProvider, String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_models), style = MaterialTheme.typography.titleMedium)
            ChatModelSection(
                provider = ChatProvider.CODEX,
                currentProvider = selectedProvider,
                models = codexModels,
                selectedModel = selectedCodexModel,
                onSelectedModelChange = onSelectedCodexModelChange,
                onAddModel = onAddModel,
                onEditModel = onEditModel,
                onDeleteModel = onDeleteModel,
            )
            ChatModelSection(
                provider = ChatProvider.CLAUDE,
                currentProvider = selectedProvider,
                models = claudeModels,
                selectedModel = selectedClaudeModel,
                onSelectedModelChange = onSelectedClaudeModelChange,
                onAddModel = onAddModel,
                onEditModel = onEditModel,
                onDeleteModel = onDeleteModel,
            )
        }
    }
}

@Composable
private fun ChatModelSection(
    provider: ChatProvider,
    currentProvider: ChatProvider,
    models: List<String>,
    selectedModel: String,
    onSelectedModelChange: (String) -> Unit,
    onAddModel: (ChatProvider) -> Unit,
    onEditModel: (ChatProvider, String) -> Unit,
    onDeleteModel: (ChatProvider, String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = chatProviderLabel(provider),
            color = if (provider == currentProvider) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            items(models, key = { it }) { modelAlias ->
                ChatModelChip(
                    modelAlias = modelAlias,
                    selected = modelAlias == selectedModel,
                    onClick = { onSelectedModelChange(modelAlias) },
                    editable = true,
                    onEdit = { onEditModel(provider, modelAlias) },
                    onDelete = { onDeleteModel(provider, modelAlias) },
                )
            }
            item(key = "${provider.storageValue}-add-model") {
                TextButton(
                    onClick = { onAddModel(provider) },
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(stringResource(R.string.settings_add_model))
                }
            }
        }
    }
}

@Composable
private fun ChatModelChip(
    modelAlias: String,
    selected: Boolean,
    onClick: () -> Unit,
    editable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .widthIn(max = if (editable) 260.dp else 220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (editable) 4.dp else 12.dp,
                top = 3.dp,
                bottom = 3.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelAlias,
                modifier = Modifier.widthIn(max = if (editable) 156.dp else 196.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            if (editable) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelAliasDialog(
    provider: ChatProvider,
    title: String,
    initialModelAlias: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var modelAlias by rememberSaveable(provider.storageValue, initialModelAlias) {
        mutableStateOf(initialModelAlias)
    }
    val canSave = modelAlias.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            OutlinedTextField(
                value = modelAlias,
                onValueChange = { modelAlias = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_model_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(modelAlias) },
                enabled = canSave,
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
private fun ImageEndpointSettingsField(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    imageBaseUrl: String,
    onImageBaseUrlChange: (String) -> Unit,
    imageApiKey: String,
    onImageApiKeyChange: (String) -> Unit,
    imageKeyVisible: Boolean,
    onImageKeyVisibleChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_image_endpoint),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_image_endpoint_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = imageBaseUrl,
                    onValueChange = onImageBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.openai.com") },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Cloud, contentDescription = null) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = imageApiKey,
                    onValueChange = onImageApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-") },
                    visualTransformation = if (imageKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { onImageKeyVisibleChange(!imageKeyVisible) }) {
                            Icon(
                                imageVector = if (imageKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (imageKeyVisible) R.string.hide else R.string.show),
                            )
                        }
                    },
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun LanguageField(
    selectedLanguageTag: String,
    onLanguageTagChange: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageTagChange(language.storageValue) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedLanguageTag == language.storageValue,
                        onClick = { onLanguageTagChange(language.storageValue) },
                    )
                    Text(
                        text = languageLabel(language),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningEffortField(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.settings_reasoning_effort), style = MaterialTheme.typography.titleMedium)
            Text(
                text = reasoningEffortLabel(reasoningEffortValue(selectedIndex)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { onSelectedIndexChange(it.roundToInt()) },
                valueRange = 0f..4f,
                steps = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("low", "medium", "high", "xhigh", "max").forEach { value ->
                    Text(
                        text = reasoningEffortLabel(value),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun reasoningEffortIndex(value: String): Int = when (value.lowercase()) {
    "low" -> 0
    "medium" -> 1
    "high" -> 2
    "xhigh" -> 3
    "max" -> 4
    else -> 2
}

private fun reasoningEffortValue(index: Int): String = when (index.coerceIn(0, 4)) {
    0 -> "low"
    1 -> "medium"
    2 -> "high"
    3 -> "xhigh"
    else -> "max"
}

private fun parseMaxContextTokens(value: String): Int =
    value.toIntOrNull()?.coerceAtLeast(1) ?: AppSettings.DEFAULT_MAX_CONTEXT_TOKENS

private data class ModelListUpdate(
    val models: List<String>,
    val selectedModel: String,
)

private fun updateCustomModelAlias(
    models: List<String>,
    selectedModel: String,
    oldModelAlias: String,
    newModelAlias: String,
): ModelListUpdate {
    val oldAlias = oldModelAlias.trim()
    val newAlias = newModelAlias.trim()
    if (oldAlias.isBlank() || newAlias.isBlank()) {
        return ModelListUpdate(
            models = AppSettings.normalizeModels(models, selectedModel),
            selectedModel = selectedModel,
        )
    }

    val updatedModels = models.map { model ->
        if (model == oldAlias) newAlias else model
    }
    val updatedSelectedModel = if (selectedModel == oldAlias) newAlias else selectedModel
    return ModelListUpdate(
        models = AppSettings.normalizeModels(updatedModels, updatedSelectedModel),
        selectedModel = updatedSelectedModel,
    )
}

private fun deleteCustomModelAlias(
    models: List<String>,
    selectedModel: String,
    modelAlias: String,
): ModelListUpdate {
    val alias = modelAlias.trim()
    if (alias.isBlank()) {
        return ModelListUpdate(
            models = AppSettings.normalizeModels(models, selectedModel),
            selectedModel = selectedModel,
        )
    }

    val remainingModels = AppSettings.normalizeModels(models.filterNot { it == alias })
    val updatedSelectedModel = if (selectedModel == alias) {
        remainingModels.firstOrNull().orEmpty()
    } else {
        selectedModel
    }
    return ModelListUpdate(
        models = AppSettings.normalizeModels(
            models = remainingModels,
            selectedModel = updatedSelectedModel,
        ),
        selectedModel = updatedSelectedModel,
    )
}

@Composable
private fun chatProviderLabel(provider: ChatProvider): String = stringResource(
    when (provider) {
        ChatProvider.CODEX -> R.string.settings_chat_provider_codex
        ChatProvider.CLAUDE -> R.string.settings_chat_provider_claude
    },
)

@Composable
private fun reasoningEffortLabel(value: String): String = stringResource(
    when (value.lowercase()) {
        "low" -> R.string.reasoning_low
        "medium" -> R.string.reasoning_medium
        "high" -> R.string.reasoning_high
        "xhigh" -> R.string.reasoning_xhigh
        else -> R.string.reasoning_max
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_simplified_chinese
    },
)

@Composable
private fun SettingField(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingAction: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                visualTransformation = visualTransformation,
                leadingIcon = { Icon(imageVector = leadingIcon, contentDescription = null) },
                trailingIcon = trailingAction,
                keyboardOptions = keyboardOptions,
                singleLine = true,
            )
        }
    }
}
