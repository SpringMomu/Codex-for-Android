package dev.codex.android.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.codex.android.R
import dev.codex.android.data.model.ChatMessage
import dev.codex.android.data.model.MessageRole

@Composable
fun EditMessageScreen(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var value by rememberSaveable(message.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = message.content,
                selection = TextRange(message.content.length),
            ),
        )
    }
    var searchValue by rememberSaveable(message.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var currentMatchIndex by rememberSaveable(message.id) { mutableStateOf(-1) }
    var lastSearchQuery by rememberSaveable(message.id) { mutableStateOf("") }
    val matches = remember(value.text, searchValue.text) {
        findTextMatches(
            text = value.text,
            query = searchValue.text,
        )
    }

    LaunchedEffect(searchValue.text, matches) {
        val queryChanged = searchValue.text != lastSearchQuery
        lastSearchQuery = searchValue.text

        if (matches.isEmpty()) {
            currentMatchIndex = -1
            return@LaunchedEffect
        }

        if (queryChanged || currentMatchIndex !in matches.indices) {
            currentMatchIndex = 0
            value = selectMatch(value, matches.first())
        }
    }

    val matchSummary = when {
        searchValue.text.isBlank() -> null
        matches.isEmpty() -> stringResource(R.string.message_edit_find_result_empty)
        else -> stringResource(
            R.string.message_edit_find_result_count,
            currentMatchIndex + 1,
            matches.size,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(
                                if (message.role == MessageRole.ASSISTANT) {
                                    R.string.message_edit_title_assistant
                                } else {
                                    R.string.message_edit_title_user
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextButton(
                        onClick = { onConfirm(value.text.trim()) },
                        enabled = value.text.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchValue,
                        onValueChange = { searchValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (searchValue.text.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchValue = TextFieldValue()
                                        currentMatchIndex = -1
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cancel),
                                    )
                                }
                            }
                        },
                        suffix = {
                            matchSummary?.let { summary ->
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                    IconButton(
                        onClick = {
                            if (matches.isEmpty()) return@IconButton
                            val nextIndex = if (currentMatchIndex in matches.indices) {
                                (currentMatchIndex - 1 + matches.size) % matches.size
                            } else {
                                matches.lastIndex
                            }
                            currentMatchIndex = nextIndex
                            value = selectMatch(value, matches[nextIndex])
                        },
                        enabled = matches.isNotEmpty(),
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.message_edit_find_previous),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (matches.isEmpty()) return@IconButton
                            val nextIndex = if (currentMatchIndex in matches.indices) {
                                (currentMatchIndex + 1) % matches.size
                            } else {
                                0
                            }
                            currentMatchIndex = nextIndex
                            value = selectMatch(value, matches[nextIndex])
                        },
                        enabled = matches.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.message_edit_find_next),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private fun findTextMatches(
    text: String,
    query: String,
): List<TextRange> {
    if (query.isBlank()) return emptyList()

    val matches = mutableListOf<TextRange>()
    var startIndex = 0
    while (startIndex < text.length) {
        val matchIndex = text.indexOf(
            string = query,
            startIndex = startIndex,
            ignoreCase = true,
        )
        if (matchIndex < 0) break
        matches += TextRange(matchIndex, matchIndex + query.length)
        startIndex = matchIndex + query.length
    }
    return matches
}

private fun selectMatch(
    value: TextFieldValue,
    match: TextRange,
): TextFieldValue = value.copy(selection = match)
