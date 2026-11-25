package com.example.ui.components.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BatchRenameConfig
import com.example.data.BatchRenameMode
import com.example.data.BatchRenameUtils
import com.example.data.FileItem
import com.example.ui.components.common.ArcModalCard
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import com.example.ui.theme.RobotoFontFamily
import com.example.ui.theme.RobotoMonoFontFamily

@Composable
fun BatchRenameDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit,
    onApply: (List<Pair<FileItem, String>>) -> Unit
) {
    var config by remember(items) {
        mutableStateOf(
            BatchRenameConfig(
                mode = BatchRenameMode.PREFIX_POSTFIX,
                includeNumbering = items.size > 1,
                startIndex = 1,
                paddingDigits = 2
            )
        )
    }
    var startIndexText by remember(items) { mutableStateOf("1") }

    val previewEntries = remember(items, config) {
        BatchRenameUtils.buildPreview(items, config)
    }
    val canApply = remember(previewEntries) {
        BatchRenameUtils.canApply(previewEntries)
    }
    val changedCount = remember(previewEntries) {
        previewEntries.count { it.isChanged && !it.hasConflict }
    }
    val hasAnyFile = remember(items) { items.any { !it.isDirectory } }

    fun updateStartIndex(next: Int) {
        val clamped = next.coerceAtLeast(0)
        startIndexText = clamped.toString()
        config = config.copy(startIndex = clamped)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        ArcModalCard(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .heightIn(max = 580.dp)
                .testTag("batch_rename_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Minimal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "Batch Rename",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${items.size} ${if (items.size == 1) "item" else "items"} selected",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // iOS-style Segmented Mode Control
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        BatchRenameMode.entries.forEach { mode ->
                            val selected = config.mode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        config = config.copy(
                                            mode = mode,
                                            includeNumbering = when (mode) {
                                                BatchRenameMode.NUMBER_ONLY -> true
                                                BatchRenameMode.REPLACE_NAME -> items.size > 1 || config.includeNumbering
                                                BatchRenameMode.PREFIX_POSTFIX -> config.includeNumbering
                                            }
                                        )
                                    }
                                    .testTag("batch_mode_${mode.name.lowercase()}"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) {
                                    BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                                } else null,
                                shadowElevation = if (selected) 1.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = mode.label,
                                        style = TextStyle(
                                            fontFamily = RobotoFontFamily,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            lineHeight = 14.sp
                                        ),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Scrollable Options Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (config.mode) {
                        BatchRenameMode.PREFIX_POSTFIX -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MinimalInputField(
                                    label = "Prefix",
                                    value = config.prefix,
                                    placeholder = "IMG_",
                                    onValueChange = { config = config.copy(prefix = it) },
                                    testTag = "batch_prefix_input",
                                    modifier = Modifier.weight(1f)
                                )
                                MinimalInputField(
                                    label = "Postfix",
                                    value = config.postfix,
                                    placeholder = "_edit",
                                    onValueChange = { config = config.copy(postfix = it) },
                                    testTag = "batch_postfix_input",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            MinimalToggleRow(
                                label = "Append sequence number",
                                checked = config.includeNumbering,
                                onCheckedChange = { config = config.copy(includeNumbering = it) },
                                testTag = "batch_numbering_toggle"
                            )
                        }

                        BatchRenameMode.REPLACE_NAME -> {
                            MinimalInputField(
                                label = "New name",
                                value = config.newBaseName,
                                placeholder = "Vacation",
                                onValueChange = { config = config.copy(newBaseName = it) },
                                testTag = "batch_new_name_input",
                                modifier = Modifier.fillMaxWidth()
                            )
                            MinimalToggleRow(
                                label = "Append sequence number",
                                checked = config.includeNumbering,
                                onCheckedChange = { config = config.copy(includeNumbering = it) },
                                testTag = "batch_numbering_toggle"
                            )
                        }

                        BatchRenameMode.NUMBER_ONLY -> {
                            Text(
                                text = "Replaces original filenames with sequential numbers.",
                                style = TextStyle(
                                    fontFamily = RobotoFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val showNumberControls = config.mode == BatchRenameMode.NUMBER_ONLY || config.includeNumbering
                    if (showNumberControls) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Start index inline row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Start at",
                                        style = TextStyle(
                                            fontFamily = RobotoFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Quick 0 / 1 / 2 pills
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                listOf(0, 1, 2).forEach { quickStart ->
                                                    val selected = config.startIndex == quickStart
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 24.dp, height = 24.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                if (selected) MaterialTheme.colorScheme.primary
                                                                else Color.Transparent
                                                            )
                                                            .clickable { updateStartIndex(quickStart) }
                                                            .testTag("batch_start_$quickStart"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = quickStart.toString(),
                                                            style = TextStyle(
                                                                fontFamily = RobotoMonoFontFamily,
                                                                fontWeight = FontWeight.Medium,
                                                                fontSize = 11.sp
                                                            ),
                                                            color = if (selected) {
                                                                MaterialTheme.colorScheme.onPrimary
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurface
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Unified - [ 1 ] + Stepper
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 26.dp, height = 28.dp)
                                                        .clickable(enabled = config.startIndex > 0) {
                                                            updateStartIndex(config.startIndex - 1)
                                                        }
                                                        .testTag("batch_start_dec"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Remove,
                                                        contentDescription = "Decrease start index",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = if (config.startIndex > 0) {
                                                            MaterialTheme.colorScheme.onSurface
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                        }
                                                    )
                                                }
                                                BasicTextField(
                                                    value = startIndexText,
                                                    onValueChange = { raw ->
                                                        val digits = raw.filter { it.isDigit() }.take(5)
                                                        startIndexText = digits
                                                        val parsed = digits.toIntOrNull()
                                                        if (parsed != null) {
                                                            config = config.copy(startIndex = parsed.coerceAtLeast(0))
                                                        }
                                                    },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        fontFamily = RobotoMonoFontFamily,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 11.5.sp,
                                                        textAlign = TextAlign.Center,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier
                                                        .width(34.dp)
                                                        .testTag("batch_start_input")
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 26.dp, height = 28.dp)
                                                        .clickable { updateStartIndex(config.startIndex + 1) }
                                                        .testTag("batch_start_inc"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Add,
                                                        contentDescription = "Increase start index",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Zero Padding Segmented Strip
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = "Padding",
                                        style = TextStyle(
                                            fontFamily = RobotoFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SegmentedOptionTrack(
                                        options = listOf(
                                            Triple(1, "Off", false),
                                            Triple(2, "00", true),
                                            Triple(3, "000", true),
                                            Triple(4, "0000", true),
                                            Triple(5, "00000", true)
                                        ),
                                        selectedValue = config.paddingDigits,
                                        tagFor = { "batch_pad_$it" },
                                        onSelect = { config = config.copy(paddingDigits = it) }
                                    )
                                }

                                // Separator Segmented Strip
                                if (config.mode != BatchRenameMode.NUMBER_ONLY) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            text = "Separator",
                                            style = TextStyle(
                                                fontFamily = RobotoFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        SegmentedOptionTrack(
                                            options = listOf(
                                                Triple("_", "_", true),
                                                Triple("-", "-", true),
                                                Triple(" ", "Space", false),
                                                Triple("", "None", false)
                                            ),
                                            selectedValue = config.separator,
                                            tagFor = { sep ->
                                                val key = when (sep) {
                                                    "_" -> "underscore"
                                                    "-" -> "dash"
                                                    " " -> "space"
                                                    else -> "none"
                                                }
                                                "batch_sep_$key"
                                            },
                                            onSelect = { config = config.copy(separator = it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (hasAnyFile) {
                        MinimalToggleRow(
                            label = "Keep file extensions",
                            checked = config.keepExtension,
                            onCheckedChange = { config = config.copy(keepExtension = it) },
                            testTag = "batch_keep_ext_toggle"
                        )
                    }
                }

                // Minimal Live Preview
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preview",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$changedCount/${previewEntries.size} changed",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp, max = 128.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("batch_preview_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(previewEntries) { idx, entry ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("batch_preview_row_$idx"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = entry.item.name,
                                        style = TextStyle(
                                            fontFamily = RobotoFontFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (entry.hasConflict) {
                                            Icons.Filled.ErrorOutline
                                        } else {
                                            Icons.AutoMirrored.Filled.ArrowForward
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (entry.hasConflict) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        }
                                    )
                                    Text(
                                        text = entry.conflictReason ?: entry.newName,
                                        style = TextStyle(
                                            fontFamily = RobotoFontFamily,
                                            fontWeight = if (entry.isChanged) FontWeight.Medium else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = when {
                                            entry.hasConflict -> MaterialTheme.colorScheme.error
                                            entry.isChanged -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Minimal Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onApply(previewEntries.map { it.item to it.newName }) },
                        enabled = canApply,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("batch_rename_confirm")
                    ) {
                        Text(
                            text = "Rename ($changedCount)",
                            style = TextStyle(
                                fontFamily = RobotoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalInputField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    ArcUnderlineTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        testTag = testTag,
        modifier = modifier
    )
}

@Composable
private fun <T> SegmentedOptionTrack(
    options: List<Triple<T, String, Boolean>>,
    selectedValue: T,
    tagFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { (value, label, mono) ->
                val selected = selectedValue == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onSelect(value) }
                        .testTag(tagFor(value)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = if (mono) RobotoMonoFontFamily else RobotoFontFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = if (mono) 10.5.sp else 11.5.sp
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(160),
        label = "toggle_track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(160),
        label = "toggle_thumb"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = RobotoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 20.dp)
                .clip(CircleShape)
                .background(trackColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
