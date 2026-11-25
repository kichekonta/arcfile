package com.example.ui.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DriveInfo
import com.example.data.ExplorerCategory
import com.example.ui.components.common.ArcCard
import com.example.ui.registry.BinaryCategoryIcon
import com.example.ui.registry.BinaryDriveIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTabSheet(
    drives: List<DriveInfo>,
    monochromeBinaryIcons: Boolean,
    onDismiss: () -> Unit,
    onOpenDriveInNewTab: (DriveInfo) -> Unit,
    onOpenCategory: (ExplorerCategory) -> Unit,
    onMountFolder: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = navBottom + 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Open in New Tab", style = MaterialTheme.typography.titleLarge)
            Text("Drives", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            drives.filter { it.type != com.example.data.DriveType.WORKSPACE }.forEach { drive ->
                ArcCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = { onOpenDriveInNewTab(drive) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BinaryDriveIcon(type = drive.type, monochrome = monochromeBinaryIcons, size = 24.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(drive.name, style = MaterialTheme.typography.titleSmall)
                            Text(drive.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            ArcCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                onClick = {
                    onDismiss()
                    onMountFolder()
                }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.mount_folder), style = MaterialTheme.typography.titleSmall)
                }
            }

            Text("Library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            ExplorerCategory.entries.chunked(3).forEach { rowCats ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCats.forEach { cat ->
                        ArcCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 14.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onOpenCategory(cat) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BinaryCategoryIcon(category = cat, monochrome = monochromeBinaryIcons, size = 22.dp)
                                Text(cat.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    repeat(3 - rowCats.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}
