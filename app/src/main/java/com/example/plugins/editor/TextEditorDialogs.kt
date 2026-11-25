package com.example.plugins.editor

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.FormatUtils
import com.example.ui.components.common.HtmlPreviewPanel
import com.example.ui.components.common.MarkdownPreviewPanel
import com.example.ui.theme.RobotoMonoFontFamily
import java.text.DateFormat
import java.util.Date

@Composable
fun EditorSaveAsDialog(
    initialFileName: String,
    initialParentPathOrUri: String,
    initialIsSaf: Boolean,
    onDismiss: () -> Unit,
    onConfirmSaveAs: (fileName: String, parentPathOrUri: String, isSaf: Boolean) -> Unit
) {
    var fileName by remember(initialFileName) { mutableStateOf(initialFileName) }
    var selectedParent by remember(initialParentPathOrUri) { mutableStateOf(initialParentPathOrUri) }
    var selectedIsSaf by remember(initialIsSaf) { mutableStateOf(initialIsSaf) }

    val quickFolders = remember(initialParentPathOrUri, initialIsSaf) {
        buildList {
            if (initialParentPathOrUri.isNotBlank()) {
                val label = if (initialIsSaf) "Current Mounted Folder" else "Current Folder"
                add(Triple(label, initialParentPathOrUri, initialIsSaf))
            }
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            @Suppress("DEPRECATION")
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            if (downloads != initialParentPathOrUri) {
                add(Triple("Downloads", downloads, false))
            }
            if (docs != initialParentPathOrUri) {
                add(Triple("Documents", docs, false))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_save_as_dialog"),
        title = { Text("Save As", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_save_as_filename_input")
                )
                Text(
                    text = "Destination folder",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickFolders.forEach { (label, path, isSaf) ->
                        FilterChip(
                            selected = selectedParent == path,
                            onClick = {
                                selectedParent = path
                                selectedIsSaf = isSaf
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
                Text(
                    text = selectedParent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSaveAs(fileName.trim(), selectedParent, selectedIsSaf) },
                enabled = fileName.isNotBlank(),
                modifier = Modifier.testTag("editor_save_as_confirm")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditorLastOpenedDialog(
    entries: List<EditorRecentEntry>,
    onDismiss: () -> Unit,
    onSelectEntry: (EditorRecentEntry) -> Unit,
    onClearHistory: () -> Unit
) {
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_last_opened_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Last Opened", style = MaterialTheme.typography.titleLarge)
                if (entries.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_clear_recent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear history"
                        )
                    }
                }
            }
        },
        text = {
            if (entries.isEmpty()) {
                Text(
                    text = "No recently opened files recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.pathOrUri }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectEntry(entry) }
                                .testTag("editor_recent_${entry.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.pathOrUri,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.lastOpenedMillis > 0L) {
                                        Text(
                                            text = "${entry.charsetName} · ${dateFmt.format(Date(entry.lastOpenedMillis))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditorJumpToLineDialog(
    totalLines: Int,
    currentLine: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var lineInput by remember(currentLine) { mutableStateOf(currentLine.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_jump_line_dialog"),
        title = { Text("Jump to Line", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a line number between 1 and $totalLines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { raw -> lineInput = raw.filter { it.isDigit() } },
                    label = { Text("Line number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_jump_line_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = lineInput.toIntOrNull()?.coerceIn(1, totalLines.coerceAtLeast(1)) ?: 1
                    onJump(target)
                },
                enabled = lineInput.isNotEmpty(),
                modifier = Modifier.testTag("editor_jump_line_confirm")
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditorDocumentInfoDialog(
    tab: EditorDocumentTab,
    onDismiss: () -> Unit
) {
    val text = tab.value.text
    val lines = remember(text) { if (text.isEmpty()) 0 else text.count { it == '\n' } + 1 }
    val words = remember(text) { text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } }
    val cursor = tab.value.selection.start.coerceIn(0, text.length)
    val prefix = remember(text, cursor) { text.substring(0, cursor) }
    val cursorLine = remember(prefix) { prefix.count { it == '\n' } + 1 }
    val cursorCol = remember(prefix) { cursor - (prefix.lastIndexOf('\n').takeIf { it >= 0 }?.plus(1) ?: 0) + 1 }
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_doc_info_dialog"),
        title = { Text("Document Info", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoLine("File name", tab.title)
                InfoLine("Location", tab.pathDisplay)
                InfoLine("Encoding", tab.charsetName)
                InfoLine("Highlight", tab.effectiveHighlightMode.label)
                InfoLine("Mode", if (tab.readOnly) "Read-Only" else "Editable")
                InfoLine("Modified", if (tab.isDirty) "Unsaved changes" else "Saved")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                InfoLine("Cursor position", "Line $cursorLine, Column $cursorCol")
                InfoLine("Lines", lines.toString())
                InfoLine("Words", words.toString())
                InfoLine("Characters", text.length.toString())
                tab.fileItem?.let { item ->
                    if (item.sizeBytes > 0L) {
                        InfoLine("File size", FormatUtils.formatBytes(item.sizeBytes))
                    }
                    if (item.lastModifiedMillis > 0L) {
                        InfoLine("Last modified", dateFmt.format(Date(item.lastModifiedMillis)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("editor_doc_info_close")) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EditorThemePickerDialog(
    selectedPreset: EditorThemePreset,
    onDismiss: () -> Unit,
    onSelect: (EditorThemePreset) -> Unit
) {
    val appColors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_theme_dialog"),
        title = { Text("Change Theme", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorThemePreset.entries.forEach { preset ->
                    val swatch = EditorThemePalette.resolve(preset, appColors)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (preset == selectedPreset) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(preset) }
                            .testTag("editor_theme_option_${preset.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatch.canvasBg),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .background(swatch.toolbarBg)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    preset.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(selected = preset == selectedPreset, onClick = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditorHighlightPickerDialog(
    selectedMode: EditorHighlightMode,
    onDismiss: () -> Unit,
    onSelect: (EditorHighlightMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_highlight_dialog"),
        title = { Text("Highlight", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                EditorHighlightMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 8.dp)
                            .testTag("editor_highlight_option_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(selected = mode == selectedMode, onClick = null)
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditorEncodingPickerDialog(
    selectedCharset: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("editor_encoding_dialog"),
        title = { Text("Encoding", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                EditorCharsetOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.charsetName) }
                            .padding(horizontal = 8.dp)
                            .testTag("editor_encoding_option_${option.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selectedCharset.equals(option.charsetName, ignoreCase = true),
                            onClick = null
                        )
                        Text(option.displayLabel, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditorRunOutputDialog(
    output: EditorRunOutput,
    canModifySource: Boolean,
    onApplyFormattedJson: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .testTag("editor_run_output_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = output.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (output.formattedText != null && canModifySource) {
                        Button(
                            onClick = onApplyFormattedJson,
                            modifier = Modifier.testTag("editor_apply_json_format_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Format JSON")
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close output")
                    }
                }
                output.statusBanner?.let { banner ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    when (output.kind) {
                        RunOutputKind.MARKDOWN_PREVIEW -> {
                            MarkdownPreviewPanel(
                                markdown = output.content,
                                modifier = Modifier
                                    .widthIn(max = 820.dp)
                                    .fillMaxSize()
                            )
                        }

                        RunOutputKind.HTML_PREVIEW -> {
                            HtmlPreviewPanel(
                                html = output.content,
                                modifier = Modifier
                                    .widthIn(max = 860.dp)
                                    .fillMaxSize()
                            )
                        }

                        RunOutputKind.JSON_VALIDATOR, RunOutputKind.PLAIN_INSPECTOR -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = output.content,
                                    style = TextStyle(
                                        fontFamily = RobotoMonoFontFamily,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditorUnsavedConfirmDialog(
    title: String,
    message: String,
    onSaveAndProceed: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("editor_unsaved_dialog"),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = onSaveAndProceed, modifier = Modifier.testTag("editor_unsaved_save")) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.testTag("editor_unsaved_discard")
                ) {
                    Text("Discard")
                }
            }
        }
    )
}
