package com.example.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.ArchiveCompressionLevel
import com.example.data.ArchiveFormat
import com.example.data.FileItem
import com.example.data.FormatUtils
import com.example.ui.components.common.ArcIosGroupedCard
import com.example.ui.components.common.ArcIosModal
import com.example.ui.components.common.ArcIosPrimaryButton
import com.example.ui.components.common.ArcIosSecondaryButton
import com.example.ui.components.common.ArcIosSectionHeader
import com.example.ui.components.common.ArcIosSegmentedControl
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.theme.ArcTheme
import com.example.ui.theme.BinaryAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompressArchiveDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit,
    onCreate: (
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel,
        password: String?
    ) -> Unit
) {
    val defaultName = remember(items) {
        when {
            items.size == 1 -> {
                val first = items.first()
                if (first.isDirectory) first.name
                else first.name.substringBeforeLast('.', first.name).ifBlank { "archive" }
            }
            items.isNotEmpty() -> {
                val firstBase = items.first().name.substringBeforeLast('.', items.first().name).ifBlank { "Archive" }
                "${firstBase}_plus_${items.size - 1}"
            }
            else -> "archive"
        }
    }

    var archiveName by remember(items) { mutableStateOf(defaultName) }
    var selectedFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var compressionLevel by remember { mutableStateOf(ArchiveCompressionLevel.NORMAL) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showRarNote by remember { mutableStateOf(false) }

    val totalSourceBytes = remember(items) {
        items.sumOf { if (it.isDirectory) 0L else it.sizeBytes.coerceAtLeast(0L) }
    }
    val canConfirm = archiveName.isNotBlank() && (!usePassword || !selectedFormat.supportsEncryptionOnCreate || password.isNotEmpty())
    val summary = if (totalSourceBytes > 0L) {
        "${items.size} selected · ${FormatUtils.formatBytes(totalSourceBytes)}"
    } else {
        "${items.size} selected item(s)"
    }

    ArcIosModal(
        title = "Create Archive",
        subtitle = summary,
        onDismiss = onDismiss,
        closeButtonTag = "compress_close_button",
        modifier = Modifier.testTag("compress_archive_dialog"),
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BinaryAmber.copy(alpha = if (ArcTheme.isDark) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderZip,
                    contentDescription = null,
                    tint = BinaryAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArcIosSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("compress_cancel_button")
                )
                ArcIosPrimaryButton(
                    text = "Create ${selectedFormat.extension.uppercase()}",
                    icon = Icons.Filled.FolderZip,
                    enabled = canConfirm,
                    onClick = {
                        onCreate(
                            archiveName.trim(),
                            selectedFormat,
                            compressionLevel,
                            if (usePassword && selectedFormat.supportsEncryptionOnCreate) password else null
                        )
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .testTag("compress_confirm_button")
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArcIosGroupedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArcUnderlineTextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        label = "Archive name",
                        placeholder = "Enter archive name",
                        suffixText = ".${selectedFormat.extension}",
                        testTag = "compress_name_input",
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Archive Format",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ArchiveOptionPill(
                                label = "ZIP (.zip)",
                                selected = selectedFormat == ArchiveFormat.ZIP,
                                onClick = {
                                    selectedFormat = ArchiveFormat.ZIP
                                    showRarNote = false
                                },
                                modifier = Modifier.testTag("compress_format_zip")
                            )
                            ArchiveOptionPill(
                                label = "7-Zip (.7z)",
                                selected = selectedFormat == ArchiveFormat.SEVEN_Z,
                                onClick = {
                                    selectedFormat = ArchiveFormat.SEVEN_Z
                                    usePassword = false
                                    showRarNote = false
                                },
                                modifier = Modifier.testTag("compress_format_7z")
                            )
                            ArchiveOptionPill(
                                label = "RAR (Extract only)",
                                selected = showRarNote,
                                leadingLock = true,
                                onClick = { showRarNote = !showRarNote },
                                modifier = Modifier.testTag("compress_format_rar_info")
                            )
                        }
                        if (showRarNote) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compress_rar_readonly_note")
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "RAR is extract-only. Use ZIP or 7-Zip to create archives.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Compression Level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ArcIosSegmentedControl(
                            options = ArchiveCompressionLevel.entries,
                            selectedOption = compressionLevel,
                            labelOf = { it.label },
                            onSelect = { compressionLevel = it },
                            tagPrefix = "compress_level"
                        )
                    }
                }
            }

            ArcIosSectionHeader("Security")

            ArcIosGroupedCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedFormat.supportsEncryptionOnCreate) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { usePassword = !usePassword }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (usePassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Protect with Password",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "AES-256 encryption",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = usePassword,
                                onCheckedChange = { usePassword = it },
                                modifier = Modifier.testTag("compress_password_toggle")
                            )
                        }

                        if (usePassword) {
                            ArcUnderlineTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Archive password",
                                placeholder = "Enter password",
                                showClearButton = false,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { showPassword = !showPassword },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showPassword) "Hide password" else "Show password",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                },
                                testTag = "compress_password_input",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "7-Zip compresses with LZMA2/DEFLATE. Switch to ZIP if you need AES-256 password encryption on creation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("compress_7z_encryption_note")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveOptionPill(
    label: String,
    selected: Boolean,
    leadingLock: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.dp else 0.6.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingLock) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
