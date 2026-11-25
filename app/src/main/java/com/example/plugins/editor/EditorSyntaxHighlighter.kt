package com.example.plugins.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Lightweight syntax highlighter and Find-match VisualTransformation tuned for responsive
 * editing on Android. Gracefully caps full regex scanning above 120K characters so large
 * log/Base64 files stay smooth.
 */
object EditorSyntaxHighlighter {

    private const val MAX_HIGHLIGHT_CHARS = 120_000

    private val kotlinAndCodeKeywords = Regex(
        "\\b(package|import|class|interface|object|enum|sealed|data|fun|val|var|if|else|when|for|while|do|return|break|continue|try|catch|finally|throw|private|public|protected|internal|override|open|abstract|suspend|inline|operator|companion|null|true|false|this|super|def|function|const|let|async|await|export|from|select|where|echo)\\b"
    )
    private val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val numberRegex = Regex("\\b\\d+(\\.\\d+)?([fFlL])?\\b")
    private val lineCommentRegex = Regex("//.*$|#(?![#\\s]).*$", RegexOption.MULTILINE)
    private val jsonKeyRegex = Regex("\"([^\"]+)\"\\s*:")
    private val jsonLiteralRegex = Regex("\\b(true|false|null)\\b")
    private val xmlTagRegex = Regex("</?[A-Za-z0-9:_-]+(\\s[^>]*)?>")
    private val mdHeadingRegex = Regex("^#{1,6}\\s+.*$", RegexOption.MULTILINE)
    private val mdQuoteRegex = Regex("^>.*$", RegexOption.MULTILINE)
    private val mdCodeInlineRegex = Regex("`[^`\\n]+`")
    private val mdBoldRegex = Regex("\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__")

    fun createVisualTransformation(
        mode: EditorHighlightMode,
        palette: EditorThemePalette,
        findRanges: List<IntRange>,
        activeFindIndex: Int
    ): VisualTransformation = VisualTransformation { text ->
        val highlighted = buildHighlightedString(
            raw = text.text,
            mode = mode,
            palette = palette,
            findRanges = findRanges,
            activeFindIndex = activeFindIndex
        )
        TransformedText(highlighted, OffsetMapping.Identity)
    }

    fun buildHighlightedString(
        raw: String,
        mode: EditorHighlightMode,
        palette: EditorThemePalette,
        findRanges: List<IntRange> = emptyList(),
        activeFindIndex: Int = -1
    ): AnnotatedString {
        if (raw.isEmpty()) return AnnotatedString("")
        val builder = AnnotatedString.Builder(raw)

        if (raw.length <= MAX_HIGHLIGHT_CHARS) {
            when (mode) {
                EditorHighlightMode.JSON -> highlightJson(raw, builder, palette)
                EditorHighlightMode.XML, EditorHighlightMode.HTML -> highlightMarkup(raw, builder, palette)
                EditorHighlightMode.MARKDOWN -> highlightMarkdown(raw, builder, palette)
                EditorHighlightMode.KOTLIN, EditorHighlightMode.CODE -> highlightCode(raw, builder, palette)
                EditorHighlightMode.PLAIN_TEXT, EditorHighlightMode.AUTO -> Unit
            }
        }

        findRanges.forEachIndexed { idx, range ->
            val start = range.first.coerceIn(0, raw.length)
            val end = (range.last + 1).coerceIn(start, raw.length)
            if (start < end) {
                val bg = if (idx == activeFindIndex) palette.findActiveMatchBg else palette.findMatchBg
                builder.addStyle(SpanStyle(background = bg, fontWeight = FontWeight.SemiBold), start, end)
            }
        }

        return builder.toAnnotatedString()
    }

    private fun highlightJson(raw: String, builder: AnnotatedString.Builder, palette: EditorThemePalette) {
        numberRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxNumber), match.range.first, match.range.last + 1)
        }
        jsonLiteralRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium), match.range.first, match.range.last + 1)
        }
        stringRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxString), match.range.first, match.range.last + 1)
        }
        jsonKeyRegex.findAll(raw).forEach { match ->
            val keyGroup = match.groups[1] ?: return@forEach
            builder.addStyle(
                SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium),
                (keyGroup.range.first - 1).coerceAtLeast(0),
                (keyGroup.range.last + 2).coerceAtMost(raw.length)
            )
        }
    }

    private fun highlightMarkup(raw: String, builder: AnnotatedString.Builder, palette: EditorThemePalette) {
        xmlTagRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxTag), match.range.first, match.range.last + 1)
        }
        stringRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxString), match.range.first, match.range.last + 1)
        }
    }

    private fun highlightMarkdown(raw: String, builder: AnnotatedString.Builder, palette: EditorThemePalette) {
        mdHeadingRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = palette.syntaxHeading, fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }
        mdQuoteRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }
        mdBoldRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
        }
        mdCodeInlineRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxString), match.range.first, match.range.last + 1)
        }
    }

    private fun highlightCode(raw: String, builder: AnnotatedString.Builder, palette: EditorThemePalette) {
        numberRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxNumber), match.range.first, match.range.last + 1)
        }
        kotlinAndCodeKeywords.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = palette.syntaxKeyword, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1
            )
        }
        stringRegex.findAll(raw).forEach { match ->
            builder.addStyle(SpanStyle(color = palette.syntaxString), match.range.first, match.range.last + 1)
        }
        lineCommentRegex.findAll(raw).forEach { match ->
            builder.addStyle(
                SpanStyle(color = palette.syntaxComment, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}
