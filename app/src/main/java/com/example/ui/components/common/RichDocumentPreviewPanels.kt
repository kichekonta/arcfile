package com.example.ui.components.common

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.example.ui.theme.RobotoMonoFontFamily

@Composable
fun HtmlPreviewPanel(
    html: String,
    modifier: Modifier = Modifier,
    testTag: String = "viewer_html_preview"
) {
    val context = LocalContext.current
    val preparedHtml = remember(html) { wrapHtmlForCleanPreview(html) }
    val webViewHolder = remember(context) {
        runCatching { createConfiguredPreviewWebView(context) }.getOrNull()
    }

    if (webViewHolder != null) {
        DisposableEffect(webViewHolder) {
            onDispose {
                runCatching {
                    webViewHolder.stopLoading()
                    webViewHolder.loadUrl("about:blank")
                    webViewHolder.destroy()
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = modifier.testTag(testTag)
        ) {
            AndroidView(
                factory = { webViewHolder },
                update = { view ->
                    if (view.tag != preparedHtml) {
                        view.tag = preparedHtml
                        view.loadDataWithBaseURL(
                            "https://arcfiles.local/",
                            preparedHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
        }
    } else {
        val fallbackSpanned = remember(html) {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = modifier.testTag(testTag)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = fallbackSpanned,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredPreviewWebView(context: Context): WebView {
    return WebView(context).apply {
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            defaultTextEncodingName = "UTF-8"
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = true
        }
    }
}

private fun wrapHtmlForCleanPreview(rawHtml: String): String {
    val trimmed = rawHtml.trim()
    val hasDocRoot = trimmed.contains("<html", ignoreCase = true) ||
        trimmed.contains("<!doctype", ignoreCase = true)
    if (hasDocRoot) return trimmed
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    color: #1C1C1E;
                    background: #FFFFFF;
                    line-height: 1.6;
                    padding: 18px;
                    margin: 0;
                    word-wrap: break-word;
                }
                pre, code {
                    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                    background: #F2F2F7;
                    border-radius: 6px;
                    padding: 2px 5px;
                }
                pre { padding: 12px; overflow-x: auto; }
                img, table { max-width: 100%; height: auto; }
            </style>
        </head>
        <body>$trimmed</body>
        </html>
    """.trimIndent()
}

@Composable
fun MarkdownPreviewPanel(
    markdown: String,
    modifier: Modifier = Modifier,
    testTag: String = "viewer_markdown_preview"
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier.testTag(testTag)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                blocks.forEach { block ->
                    when (block) {
                        is MdBlock.Heading -> {
                            val style = when (block.level) {
                                1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                                3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = buildInlineMarkdown(block.text, primaryColor, onSurface, codeBg),
                                    style = style,
                                    color = onSurface
                                )
                                if (block.level <= 2) {
                                    HorizontalDivider(
                                        thickness = 0.7.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }

                        is MdBlock.Paragraph -> {
                            Text(
                                text = buildInlineMarkdown(block.text, primaryColor, onSurface, codeBg),
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                color = onSurface
                            )
                        }

                        is MdBlock.Blockquote -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = buildInlineMarkdown(block.text, primaryColor, onSurface, codeBg),
                                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                        color = onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        is MdBlock.CodeBlock -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (block.language.isNotBlank()) {
                                        Text(
                                            text = block.language.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = block.code,
                                            style = TextStyle(
                                                fontFamily = RobotoMonoFontFamily,
                                                fontSize = 12.5.sp,
                                                lineHeight = 19.sp,
                                                color = onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        is MdBlock.ListItem -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                when {
                                    block.checked != null -> {
                                        Icon(
                                            imageVector = if (block.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                            contentDescription = if (block.checked) "Checked" else "Unchecked",
                                            tint = if (block.checked) primaryColor else onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(18.dp)
                                        )
                                    }

                                    block.ordered -> {
                                        Text(
                                            text = "${block.number}.",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = primaryColor
                                        )
                                    }

                                    else -> {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = primaryColor
                                        )
                                    }
                                }
                                Text(
                                    text = buildInlineMarkdown(block.text, primaryColor, onSurface, codeBg),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                                    color = onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        is MdBlock.Table -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        block.headers.forEach { cell ->
                                            Text(
                                                text = buildInlineMarkdown(cell, primaryColor, onSurface, codeBg),
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = onSurface,
                                                modifier = Modifier.widthIn(min = 84.dp, max = 220.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.7.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    block.rows.forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            block.headers.indices.forEach { idx ->
                                                Text(
                                                    text = buildInlineMarkdown(row.getOrElse(idx) { "" }, primaryColor, onSurface, codeBg),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = onSurface,
                                                    modifier = Modifier.widthIn(min = 84.dp, max = 220.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MdBlock.Divider -> {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Blockquote(val text: String) : MdBlock
    data class CodeBlock(val language: String, val code: String) : MdBlock
    data class ListItem(
        val ordered: Boolean,
        val number: Int,
        val checked: Boolean?,
        val text: String
    ) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            if (i < lines.size) i++
            blocks += MdBlock.CodeBlock(lang, codeLines.joinToString("\n"))
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim().trimEnd('#').trim()
            blocks += MdBlock.Heading(level, text)
            i++
            continue
        }

        if (Regex("^([-*_])\\1{2,}$").matches(trimmed.replace(" ", ""))) {
            flushParagraph()
            blocks += MdBlock.Divider
            i++
            continue
        }

        if (trimmed.contains('|') && i + 1 < lines.size && isMarkdownTableSeparator(lines[i + 1])) {
            flushParagraph()
            val headers = splitMarkdownTableRow(trimmed)
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().contains('|') && lines[i].trim().isNotEmpty()) {
                rows += splitMarkdownTableRow(lines[i].trim())
                i++
            }
            blocks += MdBlock.Table(headers, rows)
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines += lines[i].trim().removePrefix(">").trimStart()
                i++
            }
            blocks += MdBlock.Blockquote(quoteLines.joinToString("\n"))
            continue
        }

        val taskMatch = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.*)$").find(trimmed)
        if (taskMatch != null) {
            flushParagraph()
            val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
            blocks += MdBlock.ListItem(ordered = false, number = 0, checked = checked, text = taskMatch.groupValues[2])
            i++
            continue
        }

        val bulletMatch = Regex("^[-*+]\\s+(.*)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks += MdBlock.ListItem(ordered = false, number = 0, checked = null, text = bulletMatch.groupValues[1])
            i++
            continue
        }

        val orderedMatch = Regex("^(\\d+)[.)]\\s+(.*)$").find(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            blocks += MdBlock.ListItem(ordered = true, number = num, checked = null, text = orderedMatch.groupValues[2])
            i++
            continue
        }

        paragraph += trimmed
        i++
    }
    flushParagraph()
    return blocks
}

private fun isMarkdownTableSeparator(line: String): Boolean {
    val trimmed = line.trim().trim('|')
    if (!trimmed.contains('-')) return false
    return trimmed.split('|').all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' }
    }
}

private fun splitMarkdownTableRow(row: String): List<String> =
    row.trim().trim('|').split('|').map { it.trim() }

private fun buildInlineMarkdown(
    text: String,
    linkColor: Color,
    textColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length
    while (i < len) {
        when {
            text[i] == '`' -> {
                val close = text.indexOf('`', i + 1)
                if (close > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = RobotoMonoFontFamily,
                            fontSize = 13.sp,
                            background = codeBg,
                            color = textColor
                        )
                    ) {
                        append(" ${text.substring(i + 1, close)} ")
                    }
                    i = close + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val delim = text.substring(i, i + 2)
                val close = text.indexOf(delim, i + 2)
                if (close > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(buildInlineMarkdown(text.substring(i + 2, close), linkColor, textColor, codeBg))
                    }
                    i = close + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val close = text.indexOf("~~", i + 2)
                if (close > i + 1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(buildInlineMarkdown(text.substring(i + 2, close), linkColor, textColor, codeBg))
                    }
                    i = close + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '*' || text[i] == '_' -> {
                val delim = text[i]
                val close = text.indexOf(delim, i + 1)
                if (close > i && !text.substring(i + 1, close).contains('\n')) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(buildInlineMarkdown(text.substring(i + 1, close), linkColor, textColor, codeBg))
                    }
                    i = close + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 1) {
                        val label = text.substring(i + 1, closeBracket)
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(label)
                        }
                        i = closeParen + 1
                        continue
                    }
                }
                append(text[i])
                i++
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
