package com.example.plugins.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.FileItem

enum class EditorHighlightMode(val label: String) {
    AUTO("Auto"),
    PLAIN_TEXT("Text"),
    MARKDOWN("Markdown"),
    JSON("JSON"),
    XML("XML"),
    HTML("HTML"),
    KOTLIN("Kotlin"),
    CODE("Code");

    fun resolveForName(fileName: String): EditorHighlightMode {
        if (this != AUTO) return this
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "md", "markdown" -> MARKDOWN
            "json" -> JSON
            "xml" -> XML
            "html", "htm" -> HTML
            "kt", "kts" -> KOTLIN
            "java", "js", "ts", "py", "css", "sh", "yaml", "yml", "ini", "conf" -> CODE
            else -> PLAIN_TEXT
        }
    }
}

enum class EditorCharsetOption(val charsetName: String, val displayLabel: String) {
    UTF_8("UTF-8", "UTF-8"),
    UTF_16("UTF-16", "UTF-16"),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1"),
    US_ASCII("US-ASCII", "US-ASCII");

    companion object {
        fun fromName(name: String): EditorCharsetOption =
            entries.firstOrNull { it.charsetName.equals(name, ignoreCase = true) } ?: UTF_8
    }
}

enum class EditorThemePreset(val label: String, val subtitle: String) {
    CLASSIC_LIGHT("Classic Blue Light", "Crisp white paper with cobalt editor toolbar"),
    MATCH_APP("Match App Theme", "Follows Arc Files Material 3 light or dark palette"),
    NIGHT_DARK("Night Slate Dark", "High-contrast midnight canvas for low light"),
    MONOKAI("Monokai Dark", "Warm charcoal surface with vivid code tokens"),
    SEPIA("Sepia Paper", "Warm book-paper tone for comfortable long reading");

    companion object {
        fun fromName(name: String?): EditorThemePreset =
            entries.firstOrNull { it.name == name } ?: CLASSIC_LIGHT
    }
}

@Immutable
data class EditorThemePalette(
    val preset: EditorThemePreset,
    val toolbarBg: Color,
    val toolbarContent: Color,
    val subBarBg: Color,
    val subBarContent: Color,
    val canvasBg: Color,
    val canvasText: Color,
    val gutterBg: Color,
    val gutterText: Color,
    val gutterActiveText: Color,
    val gutterDivider: Color,
    val cursorColor: Color,
    val drawerHeaderGradientStart: Color,
    val drawerHeaderGradientEnd: Color,
    val drawerBg: Color,
    val drawerActiveRowBg: Color,
    val drawerActiveAccent: Color,
    val drawerText: Color,
    val drawerSubtext: Color,
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    val syntaxTag: Color,
    val syntaxHeading: Color,
    val findMatchBg: Color,
    val findActiveMatchBg: Color
) {
    companion object {
        fun resolve(preset: EditorThemePreset, appColors: ColorScheme): EditorThemePalette = when (preset) {
            EditorThemePreset.CLASSIC_LIGHT -> EditorThemePalette(
                preset = preset,
                toolbarBg = Color(0xFF1967D2),
                toolbarContent = Color.White,
                subBarBg = Color(0xFF1557B0),
                subBarContent = Color(0xFFE3EEFF),
                canvasBg = Color(0xFFFFFFFF),
                canvasText = Color(0xFF1F2328),
                gutterBg = Color(0xFFF6F8FA),
                gutterText = Color(0xFF8C959F),
                gutterActiveText = Color(0xFF1967D2),
                gutterDivider = Color(0xFFE5E9EF),
                cursorColor = Color(0xFF1967D2),
                drawerHeaderGradientStart = Color(0xFF1652B8),
                drawerHeaderGradientEnd = Color(0xFF2896E8),
                drawerBg = Color(0xFFF7F8FA),
                drawerActiveRowBg = Color(0xFFE6EEFA),
                drawerActiveAccent = Color(0xFF1967D2),
                drawerText = Color(0xFF2B2F36),
                drawerSubtext = Color(0xFF6B7280),
                syntaxKeyword = Color(0xFF1557B0),
                syntaxString = Color(0xFF0B7A4B),
                syntaxNumber = Color(0xFFAD5700),
                syntaxComment = Color(0xFF7D8590),
                syntaxTag = Color(0xFF9C27B0),
                syntaxHeading = Color(0xFF0D47A1),
                findMatchBg = Color(0x66FFE082),
                findActiveMatchBg = Color(0xAAFF9800)
            )

            EditorThemePreset.MATCH_APP -> EditorThemePalette(
                preset = preset,
                toolbarBg = appColors.primary,
                toolbarContent = appColors.onPrimary,
                subBarBg = appColors.primaryContainer,
                subBarContent = appColors.onPrimaryContainer,
                canvasBg = appColors.surface,
                canvasText = appColors.onSurface,
                gutterBg = appColors.surfaceContainerLow,
                gutterText = appColors.onSurfaceVariant.copy(alpha = 0.7f),
                gutterActiveText = appColors.primary,
                gutterDivider = appColors.outlineVariant.copy(alpha = 0.45f),
                cursorColor = appColors.primary,
                drawerHeaderGradientStart = appColors.primary,
                drawerHeaderGradientEnd = appColors.tertiary,
                drawerBg = appColors.surfaceContainerLow,
                drawerActiveRowBg = appColors.primaryContainer.copy(alpha = 0.55f),
                drawerActiveAccent = appColors.primary,
                drawerText = appColors.onSurface,
                drawerSubtext = appColors.onSurfaceVariant,
                syntaxKeyword = appColors.primary,
                syntaxString = Color(0xFF2E7D32),
                syntaxNumber = appColors.tertiary,
                syntaxComment = appColors.onSurfaceVariant.copy(alpha = 0.72f),
                syntaxTag = appColors.secondary,
                syntaxHeading = appColors.primary,
                findMatchBg = appColors.tertiaryContainer.copy(alpha = 0.65f),
                findActiveMatchBg = appColors.primary.copy(alpha = 0.38f)
            )

            EditorThemePreset.NIGHT_DARK -> EditorThemePalette(
                preset = preset,
                toolbarBg = Color(0xFF182235),
                toolbarContent = Color(0xFFF1F5F9),
                subBarBg = Color(0xFF111927),
                subBarContent = Color(0xFF93C5FD),
                canvasBg = Color(0xFF0D1117),
                canvasText = Color(0xFFE6EDF3),
                gutterBg = Color(0xFF121720),
                gutterText = Color(0xFF6E7681),
                gutterActiveText = Color(0xFF60A5FA),
                gutterDivider = Color(0xFF263040),
                cursorColor = Color(0xFF60A5FA),
                drawerHeaderGradientStart = Color(0xFF132238),
                drawerHeaderGradientEnd = Color(0xFF1D4ED8),
                drawerBg = Color(0xFF131922),
                drawerActiveRowBg = Color(0xFF1E2B3D),
                drawerActiveAccent = Color(0xFF60A5FA),
                drawerText = Color(0xFFE2E8F0),
                drawerSubtext = Color(0xFF94A3B8),
                syntaxKeyword = Color(0xFF79C0FF),
                syntaxString = Color(0xFFA5D6FF),
                syntaxNumber = Color(0xFFFFA657),
                syntaxComment = Color(0xFF8B949E),
                syntaxTag = Color(0xFFD2A8FF),
                syntaxHeading = Color(0xFF58A6FF),
                findMatchBg = Color(0x55F59E0B),
                findActiveMatchBg = Color(0xAAEA580C)
            )

            EditorThemePreset.MONOKAI -> EditorThemePalette(
                preset = preset,
                toolbarBg = Color(0xFF1E1F1C),
                toolbarContent = Color(0xFFF8F8F2),
                subBarBg = Color(0xFF161714),
                subBarContent = Color(0xFFA6E22E),
                canvasBg = Color(0xFF272822),
                canvasText = Color(0xFFF8F8F2),
                gutterBg = Color(0xFF23241E),
                gutterText = Color(0xFF75715E),
                gutterActiveText = Color(0xFFE6DB74),
                gutterDivider = Color(0xFF3A3B32),
                cursorColor = Color(0xFFF8F8F0),
                drawerHeaderGradientStart = Color(0xFF272822),
                drawerHeaderGradientEnd = Color(0xFF49483E),
                drawerBg = Color(0xFF1E1F1C),
                drawerActiveRowBg = Color(0xFF33352D),
                drawerActiveAccent = Color(0xFFA6E22E),
                drawerText = Color(0xFFF8F8F2),
                drawerSubtext = Color(0xFFA59F85),
                syntaxKeyword = Color(0xFFF92672),
                syntaxString = Color(0xFFE6DB74),
                syntaxNumber = Color(0xFFAE81FF),
                syntaxComment = Color(0xFF75715E),
                syntaxTag = Color(0xFF66D9EF),
                syntaxHeading = Color(0xFFA6E22E),
                findMatchBg = Color(0x55E6DB74),
                findActiveMatchBg = Color(0xAAFD971F)
            )

            EditorThemePreset.SEPIA -> EditorThemePalette(
                preset = preset,
                toolbarBg = Color(0xFF5D4037),
                toolbarContent = Color(0xFFFFF8F0),
                subBarBg = Color(0xFF4E342E),
                subBarContent = Color(0xFFFFE0B2),
                canvasBg = Color(0xFFF8F1E3),
                canvasText = Color(0xFF3E2723),
                gutterBg = Color(0xFFF0E6D2),
                gutterText = Color(0xFF8D7B6E),
                gutterActiveText = Color(0xFF5D4037),
                gutterDivider = Color(0xFFDCD0B8),
                cursorColor = Color(0xFF5D4037),
                drawerHeaderGradientStart = Color(0xFF4E342E),
                drawerHeaderGradientEnd = Color(0xFF8D6E63),
                drawerBg = Color(0xFFF3EBDC),
                drawerActiveRowBg = Color(0xFFE6D7BE),
                drawerActiveAccent = Color(0xFF6D4C41),
                drawerText = Color(0xFF3E2723),
                drawerSubtext = Color(0xFF795548),
                syntaxKeyword = Color(0xFF883900),
                syntaxString = Color(0xFF2E6B3E),
                syntaxNumber = Color(0xFF9A3412),
                syntaxComment = Color(0xFF8D7B6E),
                syntaxTag = Color(0xFF6D28D9),
                syntaxHeading = Color(0xFF5D4037),
                findMatchBg = Color(0x66FBBF24),
                findActiveMatchBg = Color(0xAAD97706)
            )
        }
    }
}

@Immutable
data class TextSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

@Immutable
data class EditorDocumentTab(
    val tabId: String,
    val fileItem: FileItem?,
    val title: String,
    val pathDisplay: String,
    val savedText: String,
    val value: TextFieldValue,
    val readOnly: Boolean = false,
    val charsetName: String = "UTF-8",
    val highlightMode: EditorHighlightMode = EditorHighlightMode.AUTO,
    val undoStack: List<TextSnapshot> = emptyList(),
    val redoStack: List<TextSnapshot> = emptyList(),
    val locationHistory: List<Int> = listOf(0),
    val locationIndex: Int = 0,
    val isLoading: Boolean = false,
    val lastSavedMillis: Long = 0L
) {
    val isUntitled: Boolean
        get() = fileItem == null

    val isDirty: Boolean
        get() = if (fileItem == null) value.text.isNotEmpty() else value.text != savedText

    val canUndo: Boolean
        get() = !readOnly && undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = !readOnly && redoStack.isNotEmpty()

    val canJumpPrevLocation: Boolean
        get() = locationIndex > 0

    val canJumpNextLocation: Boolean
        get() = locationIndex < locationHistory.lastIndex

    val effectiveHighlightMode: EditorHighlightMode
        get() = highlightMode.resolveForName(title)

    val supportsRichPreview: Boolean
        get() {
            val mode = effectiveHighlightMode
            return mode == EditorHighlightMode.MARKDOWN || mode == EditorHighlightMode.HTML
        }
}

@Immutable
data class EditorRecentEntry(
    val pathOrUri: String,
    val isSafUri: Boolean,
    val name: String,
    val parentPathOrUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastOpenedMillis: Long,
    val charsetName: String = "UTF-8"
)

@Immutable
data class FindReplaceState(
    val isVisible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val matchRanges: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1
)

enum class RunOutputKind {
    MARKDOWN_PREVIEW,
    HTML_PREVIEW,
    JSON_VALIDATOR,
    PLAIN_INSPECTOR
}

@Immutable
data class EditorRunOutput(
    val title: String,
    val kind: RunOutputKind,
    val content: String,
    val statusBanner: String? = null,
    val formattedText: String? = null
)

@Immutable
data class EditorUiState(
    val tabs: List<EditorDocumentTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val themePreset: EditorThemePreset = EditorThemePreset.CLASSIC_LIGHT,
    val wordWrap: Boolean = true,
    val recentEntries: List<EditorRecentEntry> = emptyList(),
    val findReplace: FindReplaceState = FindReplaceState(),
    val isPreviewTabVisible: Boolean = false,
    val showDocumentInfoDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val showHighlightDialog: Boolean = false,
    val showEncodingDialog: Boolean = false,
    val showJumpToLineDialog: Boolean = false,
    val showLastOpenedDialog: Boolean = false,
    val showSaveAsDialog: Boolean = false,
    val pendingSaveAsTabId: String? = null,
    val pendingCloseTabId: String? = null,
    val showExitUnsavedPrompt: Boolean = false,
    val runOutput: EditorRunOutput? = null,
    val statusMessage: String? = null
) {
    val activeTab: EditorDocumentTab?
        get() = tabs.getOrNull(activeTabIndex)

    val anyDirty: Boolean
        get() = tabs.any { it.isDirty }
}
