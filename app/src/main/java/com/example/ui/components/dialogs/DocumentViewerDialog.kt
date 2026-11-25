package com.example.ui.components.dialogs

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.components.common.HtmlPreviewPanel
import com.example.ui.components.common.MarkdownPreviewPanel
import com.example.ui.theme.RobotoMonoFontFamily

private enum class ViewerMode {
    PREVIEW,
    SOURCE
}

@Composable
fun DocumentViewerDialog(
    item: FileItem,
    textContent: String?,
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    val supportsPreview = item.supportsRichPreview
    var editableText by remember(item.id, textContent) { mutableStateOf(textContent ?: "") }
    var activeMode by remember(item.id, supportsPreview) {
        mutableStateOf(if (supportsPreview) ViewerMode.PREVIEW else ViewerMode.SOURCE)
    }
    var isEditing by remember(item.id) { mutableStateOf(false) }

    val kindLabel = remember(item) {
        when {
            item.isMarkdown -> "Markdown"
            item.isHtml -> "HTML"
            item.extension.isNotBlank() -> item.extension.uppercase()
            else -> "Text"
        }
    }
    val subtitle = remember(item.id, item.sizeBytes, kindLabel) {
        if (item.sizeBytes > 0L) "$kindLabel · ${FormatUtils.formatBytes(item.sizeBytes)}" else kindLabel
    }

    Dialog(
        onDismissRequest = {
            if (isEditing) {
                editableText = textContent ?: ""
                isEditing = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow(matchParentSize = true)
        BackHandler(enabled = isEditing) {
            editableText = textContent ?: ""
            isEditing = false
        }

        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("document_viewer_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                // Clean modern header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                editableText = textContent ?: ""
                                isEditing = false
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("viewer_close_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = if (isEditing) "Cancel editing" else "Close viewer",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isEditing) {
                        Button(
                            onClick = {
                                onSaveText(editableText)
                                isEditing = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("viewer_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        if (item.isTextEditable) {
                            IconButton(
                                onClick = {
                                    activeMode = ViewerMode.SOURCE
                                    isEditing = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("viewer_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit source",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenWith,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_open_with_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open with app",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onShare,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("viewer_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share file",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Segmented Preview / Source switcher for Markdown and HTML (Preview First)
                if (supportsPreview && !isEditing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PreviewSourceSegmentedBar(
                            activeMode = activeMode,
                            onSelectMode = { activeMode = it }
                        )
                    }
                }

                // Main Reader / Editor Canvas (extends edge-to-edge behind navigation gesture bar)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = navBottom + 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        isEditing || !supportsPreview || activeMode == ViewerMode.SOURCE -> {
                            SourceCodePanel(
                                text = editableText,
                                isEditing = isEditing,
                                onTextChange = { editableText = it },
                                modifier = Modifier
                                    .widthIn(max = 860.dp)
                                    .fillMaxSize()
                            )
                        }

                        item.isMarkdown -> {
                            MarkdownPreviewPanel(
                                markdown = editableText,
                                modifier = Modifier
                                    .widthIn(max = 820.dp)
                                    .fillMaxSize()
                            )
                        }

                        else -> {
                            HtmlPreviewPanel(
                                html = editableText,
                                modifier = Modifier
                                    .widthIn(max = 860.dp)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSourceSegmentedBar(
    activeMode: ViewerMode,
    onSelectMode: (ViewerMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .widthIn(max = 272.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ViewerSegmentPill(
                label = "Preview",
                selected = activeMode == ViewerMode.PREVIEW,
                onClick = { onSelectMode(ViewerMode.PREVIEW) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("viewer_tab_preview")
            )
            ViewerSegmentPill(
                label = "Source",
                selected = activeMode == ViewerMode.SOURCE,
                onClick = { onSelectMode(ViewerMode.SOURCE) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("viewer_tab_source")
            )
        }
    }
}

@Composable
private fun ViewerSegmentPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SourceCodePanel(
    text: String,
    isEditing: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        val monoStyle = TextStyle(
            fontFamily = RobotoMonoFontFamily,
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            if (isEditing) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = monoStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viewer_source_editor")
                ) { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Start writing…",
                            style = monoStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        style = monoStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("viewer_source_content")
                    )
                }
            }
        }
    }
}

