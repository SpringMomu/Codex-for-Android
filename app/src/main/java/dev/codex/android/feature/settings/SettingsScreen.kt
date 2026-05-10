package dev.codex.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.android.R
import dev.codex.android.core.i18n.AppLanguage
import dev.codex.android.core.di.AppContainer
import dev.codex.android.data.model.AppSettings
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
    var baseUrl by rememberSaveable(uiState.settings.baseUrl) { mutableStateOf(uiState.settings.baseUrl) }
    var apiKey by rememberSaveable(uiState.settings.apiKey) { mutableStateOf(uiState.settings.apiKey) }
    var imageBaseUrl by rememberSaveable(uiState.settings.imageBaseUrl) { mutableStateOf(uiState.settings.imageBaseUrl) }
    var imageApiKey by rememberSaveable(uiState.settings.imageApiKey) { mutableStateOf(uiState.settings.imageApiKey) }
    var modelAlias by rememberSaveable(uiState.settings.modelAlias) { mutableStateOf(uiState.settings.modelAlias) }
    var languageTag by rememberSaveable(uiState.settings.languageTag) { mutableStateOf(uiState.settings.languageTag) }
    var reasoningEffort by rememberSaveable(uiState.settings.reasoningEffort) {
        mutableStateOf(reasoningEffortIndex(uiState.settings.reasoningEffort))
    }
    var systemPrompt by rememberSaveable(uiState.settings.systemPrompt) { mutableStateOf(uiState.settings.systemPrompt) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var imageKeyVisible by rememberSaveable { mutableStateOf(false) }
    var imageSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
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

            SettingField(
                title = stringResource(R.string.settings_base_url),
                hint = "https://api.openai.com",
                value = baseUrl,
                onValueChange = { baseUrl = it },
                leadingIcon = Icons.Rounded.Cloud,
            )
            SettingField(
                title = stringResource(R.string.settings_api_key),
                hint = "sk-",
                value = apiKey,
                onValueChange = { apiKey = it },
                leadingIcon = Icons.Rounded.Key,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingAction = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (keyVisible) R.string.hide else R.string.show),
                        )
                    }
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
                title = stringResource(R.string.settings_model_alias),
                hint = "gpt-5.4",
                value = modelAlias,
                onValueChange = { modelAlias = it },
                leadingIcon = Icons.Rounded.Memory,
            )
            LanguageField(
                selectedLanguageTag = languageTag,
                onLanguageTagChange = { languageTag = it },
            )
            ReasoningEffortField(
                selectedIndex = reasoningEffort,
                onSelectedIndexChange = { reasoningEffort = it },
            )
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

            Button(
                onClick = {
                    onSave(
                        AppSettings(
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            imageBaseUrl = imageBaseUrl,
                            imageApiKey = imageApiKey,
                            modelAlias = modelAlias,
                            reasoningEffort = reasoningEffortValue(reasoningEffort),
                            systemPrompt = systemPrompt,
                            languageTag = languageTag,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                enabled = !uiState.isSaving,
            ) {
                Text(stringResource(if (uiState.isSaving) R.string.settings_saving else R.string.settings_save))
            }
        }
    }
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
                valueRange = 0f..3f,
                steps = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("low", "medium", "high", "xhigh").forEach { value ->
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
    else -> 2
}

private fun reasoningEffortValue(index: Int): String = when (index.coerceIn(0, 3)) {
    0 -> "low"
    1 -> "medium"
    2 -> "high"
    else -> "xhigh"
}

@Composable
private fun reasoningEffortLabel(value: String): String = stringResource(
    when (value.lowercase()) {
        "low" -> R.string.reasoning_low
        "medium" -> R.string.reasoning_medium
        "high" -> R.string.reasoning_high
        else -> R.string.reasoning_xhigh
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
                singleLine = true,
            )
        }
    }
}
