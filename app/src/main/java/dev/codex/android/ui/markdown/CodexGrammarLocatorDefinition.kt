package dev.codex.android.ui.markdown

import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    include = [
        "c",
        "clike",
        "cpp",
        "csharp",
        "css",
        "go",
        "java",
        "javascript",
        "json",
        "kotlin",
        "markup",
        "python",
        "sql",
        "swift",
        "yaml",
    ],
    grammarLocatorClassName = "dev.codex.android.ui.markdown.CodexGrammarLocator",
)
class CodexGrammarLocatorDefinition
