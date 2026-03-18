package dev.codex.android.ui.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.codex.android.R
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.syntax.Prism4jSyntaxHighlight
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.delay

private val blockLatexRegex = Regex("""\\\[\s*([\s\S]*?)\s*\\\]""")
private val inlineLatexRegex = Regex("""\\\((.+?)\\\)""")
private val singleDollarLatexRegex = Regex("""(?<!\\)(?<!\$)\$(?!\$)([^\n$]+?)(?<!\\)\$(?!\$)""")
private val doubleDollarLatexRegex = Regex("""(?<!\\)\$\$([\s\S]+?)(?<!\\)\$\$""")
private val asciiTableBorderRegex = Regex("""^\+(?:-+\+){2,}$""")
private val latexCommandRegex = Regex("""\\[A-Za-z]+""")
private val latexMathOperatorRegex = Regex("""[=^_{}]|\s[+\-*/]\s|\d+\s*[+\-*/=]\s*[A-Za-z(\\]""")

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
    highlightQuery: String = "",
    activeOccurrenceIndex: Int? = null,
    onActiveSearchTargetPositioned: ((Float) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = contentColor.toArgb()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val markdownSegments = remember(markdown) { splitMarkdownSegments(markdown) }
    val inactiveHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.88f).toArgb()
    val activeHighlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f).toArgb()
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
    val codeSyntaxHighlighter = remember(context, isDarkTheme) {
        val prism4j = Prism4j(CodexGrammarLocator())
        val theme = if (isDarkTheme) {
            Prism4jThemeDarkula.create()
        } else {
            Prism4jThemeDefault.create(Color.Transparent.toArgb())
        }
        Prism4jSyntaxHighlight.create(prism4j, theme, "clike")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var consumedMatches = 0
        markdownSegments.forEach { segment ->
            val segmentText = when (segment) {
                is MarkdownSegment.Text -> segment.markdown
                is MarkdownSegment.CodeBlock -> segment.code
            }
            val matchCount = remember(segmentText, highlightQuery) {
                findHighlightMatches(segmentText, highlightQuery).size
            }
            val localActiveOccurrenceIndex = activeOccurrenceIndex
                ?.takeIf { it in consumedMatches until (consumedMatches + matchCount) }
                ?.minus(consumedMatches)

            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.markdown.isNotBlank()) {
                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                if (
                                    highlightQuery.isNotBlank() &&
                                    localActiveOccurrenceIndex != null &&
                                    onActiveSearchTargetPositioned != null
                                ) {
                                    onActiveSearchTargetPositioned(
                                        coordinates.positionInRoot().y + coordinates.size.height / 2f,
                                    )
                                }
                            },
                        ) {
                            MarkdownTextView(
                                markdown = segment.markdown,
                                contentColor = contentColor,
                                markwon = markwon,
                                highlightQuery = highlightQuery,
                                inactiveHighlightColor = inactiveHighlightColor,
                                activeHighlightColor = activeHighlightColor,
                                activeOccurrenceIndex = localActiveOccurrenceIndex,
                                onLongPress = onLongPress,
                            )
                        }
                    }
                }

                is MarkdownSegment.CodeBlock -> {
                    Box(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            if (
                                highlightQuery.isNotBlank() &&
                                localActiveOccurrenceIndex != null &&
                                onActiveSearchTargetPositioned != null
                            ) {
                                onActiveSearchTargetPositioned(
                                    coordinates.positionInRoot().y + coordinates.size.height / 2f,
                                )
                            }
                        },
                    ) {
                        CodeBlockCard(
                            language = segment.language,
                            code = segment.code,
                            syntaxHighlighter = codeSyntaxHighlighter,
                            highlightQuery = highlightQuery,
                            inactiveHighlightColor = inactiveHighlightColor,
                            activeHighlightColor = activeHighlightColor,
                            activeOccurrenceIndex = localActiveOccurrenceIndex,
                            onLongPress = onLongPress,
                        )
                    }
                }
            }
            consumedMatches += matchCount
        }
    }
}

@Composable
private fun MarkdownTextView(
    markdown: String,
    contentColor: Color,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    highlightQuery: String = "",
    inactiveHighlightColor: Int,
    activeHighlightColor: Int,
    activeOccurrenceIndex: Int? = null,
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
                applyHighlightSpans(
                    textView = this,
                    query = highlightQuery,
                    inactiveHighlightColor = inactiveHighlightColor,
                    activeHighlightColor = activeHighlightColor,
                    activeOccurrenceIndex = activeOccurrenceIndex,
                )
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
            applyHighlightSpans(
                textView = textView,
                query = highlightQuery,
                inactiveHighlightColor = inactiveHighlightColor,
                activeHighlightColor = activeHighlightColor,
                activeOccurrenceIndex = activeOccurrenceIndex,
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeBlockCard(
    language: String?,
    code: String,
    syntaxHighlighter: Prism4jSyntaxHighlight,
    highlightQuery: String = "",
    inactiveHighlightColor: Int,
    activeHighlightColor: Int,
    activeOccurrenceIndex: Int? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val outlineColor = MaterialTheme.colorScheme.outline
    val headerColor = MaterialTheme.colorScheme.surfaceVariant
    val codeBodyColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurface
    val highlightedCode = remember(code, language) {
        runCatching { syntaxHighlighter.highlight(language, code) }.getOrElse { code }
    }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val copyCode = remember(clipboard, code) {
        {
            clipboard.setText(AnnotatedString(code))
            copied = true
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .noHapticPressGesture(onLongPress = onLongPress),
                ) {
                    Text(
                        text = languageLabel(language),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = copyCode,
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(
                            if (copied) R.string.copied_code else R.string.copy_code,
                        ),
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                    Text(
                        text = stringResource(if (copied) R.string.copied_code else R.string.copy_code),
                        modifier = Modifier.padding(start = 6.dp),
                        color = contentColor,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .noHapticPressGesture(onLongPress = onLongPress)
                    .background(codeBodyColor)
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
                            highlightQuery = highlightQuery,
                            inactiveHighlightColor = inactiveHighlightColor,
                            activeHighlightColor = activeHighlightColor,
                            activeOccurrenceIndex = activeOccurrenceIndex,
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
    highlightQuery: String = "",
    inactiveHighlightColor: Int,
    activeHighlightColor: Int,
    activeOccurrenceIndex: Int? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

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
            applyHighlightSpans(
                textView = textView,
                query = highlightQuery,
                inactiveHighlightColor = inactiveHighlightColor,
                activeHighlightColor = activeHighlightColor,
                activeOccurrenceIndex = activeOccurrenceIndex,
            )
        },
    )
}

private fun applyHighlightSpans(
    textView: AppCompatTextView,
    query: String,
    inactiveHighlightColor: Int,
    activeHighlightColor: Int,
    activeOccurrenceIndex: Int?,
) {
    val originalText = textView.text ?: return
    val spannable = when (originalText) {
        is Spannable -> SpannableString(originalText)
        else -> SpannableString.valueOf(originalText)
    }

    spannable.getSpans(0, spannable.length, SearchHighlightSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }

    val matches = findHighlightMatches(
        text = spannable.toString(),
        query = query,
    )
    matches.forEachIndexed { index, match ->
        spannable.setSpan(
            SearchHighlightSpan(
                backgroundColor = if (index == activeOccurrenceIndex) {
                    activeHighlightColor
                } else {
                    inactiveHighlightColor
                },
            ),
            match.start,
            match.endExclusive,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    textView.text = spannable
}

private class SearchHighlightSpan(
    backgroundColor: Int,
) : BackgroundColorSpan(backgroundColor)

private data class HighlightMatch(
    val start: Int,
    val endExclusive: Int,
)

private fun findHighlightMatches(
    text: String,
    query: String,
): List<HighlightMatch> {
    if (query.isBlank()) return emptyList()

    val matches = mutableListOf<HighlightMatch>()
    var startIndex = 0
    while (startIndex < text.length) {
        val matchIndex = text.indexOf(
            string = query,
            startIndex = startIndex,
            ignoreCase = true,
        )
        if (matchIndex < 0) break
        matches += HighlightMatch(
            start = matchIndex,
            endExclusive = matchIndex + query.length,
        )
        startIndex = matchIndex + query.length
    }
    return matches
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
    val normalized = normalizeMarkdownSyntax(text)
    if (normalized.isNotBlank()) {
        segments += MarkdownSegment.Text(normalized)
    }
}

private fun normalizeMarkdownSyntax(markdown: String): String {
    val normalizedTables = normalizeAsciiTables(markdown)
    val normalizedBlocks = blockLatexRegex.replace(normalizedTables) { match ->
        val content = match.groupValues[1].trim('\n')
        "\n${'$'}${'$'}\n$content\n${'$'}${'$'}\n"
    }

    val normalizedInlineBlocks = inlineLatexRegex.replace(normalizedBlocks) { match ->
        "${'$'}${'$'}${match.groupValues[1]}${'$'}${'$'}"
    }

    return singleDollarLatexRegex.replace(normalizedInlineBlocks) { match ->
        val content = match.groupValues[1].trim()
        if (!looksLikeLatexContent(content)) {
            match.value
        } else {
            "${'$'}${'$'}$content${'$'}${'$'}"
        }
    }
}

private fun languageLabel(language: String?): String =
    language
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
        ?: "CODE"

private fun normalizeAsciiTables(markdown: String): String {
    val lines = markdown.split('\n')
    if (lines.size < 3) return markdown

    val normalized = mutableListOf<String>()
    var index = 0

    while (index < lines.size) {
        val table = parseAsciiTable(lines, index)
        if (table != null) {
            normalized += table.toMarkdownLines()
            index += table.consumedLineCount
            continue
        }

        normalized += lines[index]
        index += 1
    }

    return normalized.joinToString("\n")
}

private data class AsciiTable(
    val rows: List<List<String>>,
    val consumedLineCount: Int,
)

private fun parseAsciiTable(
    lines: List<String>,
    startIndex: Int,
): AsciiTable? {
    if (!lines[startIndex].trim().matches(asciiTableBorderRegex)) return null

    val rows = mutableListOf<List<String>>()
    val columnCount = lines[startIndex].count { it == '+' } - 1
    if (columnCount < 2) return null

    var index = startIndex
    while (index + 2 < lines.size) {
        val borderLine = lines[index].trim()
        val rowLine = lines[index + 1]
        val nextBorderLine = lines[index + 2].trim()

        if (!borderLine.matches(asciiTableBorderRegex) || !nextBorderLine.matches(asciiTableBorderRegex)) {
            break
        }

        val row = parseAsciiTableRow(rowLine, columnCount) ?: break
        rows += row
        index += 2
    }

    if (rows.size < 2) return null
    if (!lines.getOrNull(index)?.trim().orEmpty().matches(asciiTableBorderRegex)) return null

    return AsciiTable(
        rows = rows,
        consumedLineCount = index - startIndex + 1,
    )
}

private fun parseAsciiTableRow(
    line: String,
    expectedColumns: Int,
): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) return null

    val columns = trimmed
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { it.trim().replace("|", "\\|") }

    return columns.takeIf { it.size == expectedColumns }
}

private fun AsciiTable.toMarkdownLines(): List<String> {
    val header = rows.first()
    val body = rows.drop(1)
    val separator = List(header.size) { "---" }

    return buildList {
        add(markdownTableRow(header))
        add(markdownTableRow(separator))
        body.forEach { add(markdownTableRow(it)) }
    }
}

private fun markdownTableRow(columns: List<String>): String =
    columns.joinToString(prefix = "| ", separator = " | ", postfix = " |")

private fun looksLikeLatexContent(content: String): Boolean {
    if (content.isBlank()) return false
    if (latexCommandRegex.containsMatchIn(content)) return true
    if (latexMathOperatorRegex.containsMatchIn(content)) return true

    val compact = content.filterNot(Char::isWhitespace)
    return compact.length <= 3 && compact.all { it.isLetter() }
}

internal fun containsLikelyLatex(markdown: String): Boolean {
    if (inlineLatexRegex.containsMatchIn(markdown) || blockLatexRegex.containsMatchIn(markdown)) {
        return true
    }

    if (doubleDollarLatexRegex.containsMatchIn(markdown)) {
        return true
    }

    return singleDollarLatexRegex.findAll(markdown).any { match ->
        looksLikeLatexContent(match.groupValues[1].trim())
    }
}
