package dev.codex.android.ui.markdown

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.codex.android.R
import dev.codex.android.ui.theme.Canvas
import dev.codex.android.ui.theme.Fog
import dev.codex.android.ui.theme.Ink
import dev.codex.android.ui.theme.PanelStrong
import dev.codex.android.ui.theme.Slate
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.delay

private val blockLatexRegex = Regex("""\\\[\s*([\s\S]*?)\s*\\\]""")
private val inlineLatexRegex = Regex("""\\\((.+?)\\\)""")

private sealed interface MarkdownSegment {
    data class Text(val markdown: String) : MarkdownSegment
    data class CodeBlock(val language: String?, val code: String) : MarkdownSegment
}

private data class FenceDefinition(
    val marker: Char,
    val length: Int,
    val language: String?,
)

private data class FenceState(
    val definition: FenceDefinition,
    val openingLine: String,
    val code: StringBuilder = StringBuilder(),
)

@Composable
fun MarkdownText(
    markdown: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = contentColor.toArgb()
    val markdownSegments = remember(markdown) { splitMarkdownSegments(markdown) }
    val markwon = remember(context, textColor) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(44f) { builder ->
                    builder
                        .inlinesEnabled(true)
                        .blocksEnabled(true)
                },
            )
            .build()
    }
    val codeSyntaxHighlighter = remember(context) {
        val prism4j = Prism4j(CodexGrammarLocator())
        val theme = Prism4jThemeDefault.create(Color.Transparent.toArgb())
        Prism4jSyntaxHighlight.create(prism4j, theme, "clike")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        markdownSegments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.markdown.isNotBlank()) {
                        MarkdownTextView(
                            markdown = segment.markdown,
                            contentColor = contentColor,
                            markwon = markwon,
                            onLongPress = onLongPress,
                        )
                    }
                }

                is MarkdownSegment.CodeBlock -> {
                    CodeBlockCard(
                        language = segment.language,
                        code = segment.code,
                        syntaxHighlighter = codeSyntaxHighlighter,
                        onLongPress = onLongPress,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTextView(
    markdown: String,
    contentColor: Color,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = contentColor.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AppCompatTextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.2f)
                includeFontPadding = false
                setTextColor(textColor)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
                isHapticFeedbackEnabled = false
                isLongClickable = onLongPress != null
                setOnLongClickListener {
                    onLongPress?.invoke()
                    onLongPress != null
                }
                markwon.setMarkdown(this, markdown)
                tag = markdown
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.isHapticFeedbackEnabled = false
            textView.isLongClickable = onLongPress != null
            textView.setOnLongClickListener {
                onLongPress?.invoke()
                onLongPress != null
            }
            if (textView.tag != markdown) {
                markwon.setMarkdown(textView, markdown)
                textView.tag = markdown
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBlockCard(
    language: String?,
    code: String,
    syntaxHighlighter: Prism4jSyntaxHighlight,
    onLongPress: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val highlightedCode = remember(code, language) {
        runCatching { syntaxHighlighter.highlight(language, code) }.getOrElse { code }
    }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .noHapticPressGesture(onLongPress = onLongPress),
        color = Canvas,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Fog),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelStrong)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = languageLabel(language),
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                    fontFamily = FontFamily.Monospace,
                )
                TextButton(
                    modifier = Modifier.noHapticPressGesture(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        },
                        onLongPress = {},
                    ),
                    onClick = {},
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.copied_code else R.string.copy_code,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(if (copied) R.string.copied_code else R.string.copy_code),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.42f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(verticalScrollState),
                ) {
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        CodeBlockTextView(
                            highlightedCode = highlightedCode,
                            onLongPress = onLongPress,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBlockTextView(
    highlightedCode: CharSequence,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = Ink.toArgb()

    AndroidView(
        modifier = modifier,
        factory = {
            AppCompatTextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.25f)
                typeface = Typeface.MONOSPACE
                includeFontPadding = false
                setTextColor(textColor)
                setTextIsSelectable(false)
                setHorizontallyScrolling(true)
                isHapticFeedbackEnabled = false
                isLongClickable = onLongPress != null
                setOnLongClickListener {
                    onLongPress?.invoke()
                    onLongPress != null
                }
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.isHapticFeedbackEnabled = false
            textView.isLongClickable = onLongPress != null
            textView.setOnLongClickListener {
                onLongPress?.invoke()
                onLongPress != null
            }
            if (textView.text != highlightedCode) {
                textView.text = highlightedCode
            }
        },
    )
}

private fun splitMarkdownSegments(markdown: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val textBuffer = StringBuilder()
    var activeFence: FenceState? = null

    fun flushTextBuffer() {
        if (textBuffer.isNotEmpty()) {
            appendTextSegment(segments, textBuffer.toString())
            textBuffer.clear()
        }
    }

    for (line in markdown.linesWithSeparators()) {
        val lineBody = line.trimLineEnding()
        val openingFence = parseOpeningFence(lineBody)
        val currentFence = activeFence

        if (currentFence == null) {
            if (openingFence != null) {
                flushTextBuffer()
                activeFence = FenceState(
                    definition = openingFence,
                    openingLine = line,
                )
            } else {
                textBuffer.append(line)
            }
            continue
        }

        if (isClosingFence(lineBody, currentFence.definition)) {
            segments += MarkdownSegment.CodeBlock(
                language = currentFence.definition.language,
                code = currentFence.code.toString().trimEnd('\r', '\n'),
            )
            activeFence = null
        } else {
            currentFence.code.append(line)
        }
    }

    if (activeFence != null) {
        textBuffer.append(activeFence.openingLine)
        textBuffer.append(activeFence.code)
    }

    flushTextBuffer()

    if (segments.isEmpty()) {
        appendTextSegment(segments, markdown)
    }

    return segments
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

private fun parseOpeningFence(line: String): FenceDefinition? {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > 3) return null

    val trimmed = line.drop(indent)
    val marker = trimmed.firstOrNull() ?: return null
    if (marker != '`' && marker != '~') return null

    val fenceLength = trimmed.takeWhile { it == marker }.length
    if (fenceLength < 3) return null

    val info = trimmed.drop(fenceLength)
    if (marker == '`' && info.contains('`')) return null

    val language = info
        .trim()
        .substringBefore(' ')
        .substringBefore('{')
        .ifBlank { null }

    return FenceDefinition(
        marker = marker,
        length = fenceLength,
        language = language,
    )
}

private fun isClosingFence(
    line: String,
    definition: FenceDefinition,
): Boolean {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > 3) return false

    val trimmed = line.drop(indent)
    val fenceLength = trimmed.takeWhile { it == definition.marker }.length
    if (fenceLength < definition.length) return false

    return trimmed.drop(fenceLength).isBlank()
}

private fun String.linesWithSeparators(): Sequence<String> = sequence {
    var start = 0
    var index = 0

    while (index < length) {
        if (this@linesWithSeparators[index] == '\n') {
            yield(substring(start, index + 1))
            start = index + 1
        }
        index += 1
    }

    if (start < length) {
        yield(substring(start))
    }
}

private fun String.trimLineEnding(): String = trimEnd('\r', '\n')

private fun appendTextSegment(
    segments: MutableList<MarkdownSegment>,
    text: String,
) {
    val normalized = normalizeLatexSyntax(text)
    if (normalized.isNotBlank()) {
        segments += MarkdownSegment.Text(normalized)
    }
}

private fun normalizeLatexSyntax(markdown: String): String {
    val normalizedBlocks = blockLatexRegex.replace(markdown) { match ->
        val content = match.groupValues[1].trim('\n')
        "\n${'$'}${'$'}\n$content\n${'$'}${'$'}\n"
    }

    return inlineLatexRegex.replace(normalizedBlocks) { match ->
        "${'$'}${'$'}${match.groupValues[1]}${'$'}${'$'}"
    }
}

private fun languageLabel(language: String?): String =
    language
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
        ?: "CODE"
