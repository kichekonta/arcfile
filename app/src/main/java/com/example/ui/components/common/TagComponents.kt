package com.example.ui.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.FileItem
import com.example.data.FileTag
import com.example.data.IosTagPalette
import com.example.data.normalizeTagPath
import com.example.ui.theme.ArcTheme

val ItemTagsCountSemanticsKey = SemanticsPropertyKey<Int>("ItemTagsCount")

@Composable
fun TagDot(
    colorHex: Long,
    size: Dp = 10.dp,
    ringColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorHex))
            .border(width = 0.8.dp, color = ringColor.copy(alpha = 0.9f), shape = CircleShape)
    )
}

@Composable
fun TagDotsCluster(
    tags: List<FileTag>,
    dotSize: Dp = 8.dp,
    maxVisible: Int = 3,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val visible = remember(tags, maxVisible) { tags.take(maxVisible) }
    Row(
        modifier = modifier.semantics { set(ItemTagsCountSemanticsKey, tags.size) },
        horizontalArrangement = Arrangement.spacedBy((-2).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { tag ->
            TagDot(colorHex = tag.colorHex, size = dotSize)
        }
    }
}

@Composable
fun TagHeaderBar(
    activeTag: FileTag?,
    allTags: List<FileTag>,
    itemCount: Int,
    onSelectTag: (String) -> Unit
) {
    val d = ArcTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenHorizontalPadding, vertical = 4.dp)
            .testTag("tag_header_bar"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activeTag != null) {
                TagDot(colorHex = activeTag.colorHex, size = 16.dp)
            }
            Text(
                text = activeTag?.name ?: "Tag",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (allTags.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allTags.forEach { tag ->
                    val selected = tag.id == activeTag?.id
                    Surface(
                        onClick = { onSelectTag(tag.id) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) {
                            Color(tag.colorHex).copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (selected) 1.2.dp else 0.5.dp,
                            color = if (selected) Color(tag.colorHex) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("tag_header_chip_${tag.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TagDot(colorHex = tag.colorHex, size = 9.dp)
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsPickerSheet(
    targets: List<FileItem>,
    allTags: List<FileTag>,
    itemTagsByPath: Map<String, List<FileTag>>,
    taggedCountsByTagId: Map<String, Int>,
    onToggleTag: (String) -> Unit,
    onCreateTag: (String, Long) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateForm by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(IosTagPalette.BLUE) }

    val targetPaths = remember(targets) {
        targets.map { normalizeTagPath(it.pathOrUri, it.isSafUri) }
    }
    val subtitle = remember(targets) {
        if (targets.size == 1) targets.first().name else "${targets.size} items selected"
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = Modifier.testTag("tag_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = navBottom + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("tag_picker_done_button")
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }

            // Inline iOS-style "+ Add New Tag..." creator
            Surface(
                onClick = { showCreateForm = !showCreateForm },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_custom_tag_toggle")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add New Tag...",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = showCreateForm) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ArcUnderlineTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = "Tag name",
                            placeholder = "Work, Receipts…",
                            testTag = "custom_tag_name_input",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IosTagPalette.presetColors.forEachIndexed { idx, hex ->
                                val isPicked = selectedColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (isPicked) 2.5.dp else 1.dp,
                                            color = if (isPicked) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.8f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex }
                                        .testTag("custom_tag_color_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPicked) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected color",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showCreateForm = false; customName = "" }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val clean = customName.trim()
                                    if (clean.isNotEmpty()) {
                                        onCreateTag(clean, selectedColorHex)
                                        customName = ""
                                        showCreateForm = false
                                    }
                                },
                                enabled = customName.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("custom_tag_create_button")
                            ) {
                                Text("Create & Apply")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allTags.forEach { tag ->
                    val assignedCountOnTargets = targetPaths.count { path ->
                        itemTagsByPath[path]?.any { it.id == tag.id } == true
                    }
                    val allChecked = assignedCountOnTargets == targetPaths.size && targetPaths.isNotEmpty()
                    val partialChecked = assignedCountOnTargets in 1 until targetPaths.size
                    val totalTagged = taggedCountsByTagId[tag.id] ?: 0

                    Surface(
                        onClick = { onToggleTag(tag.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (allChecked) {
                            Color(tag.colorHex).copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (allChecked) 1.dp else 0.6.dp,
                            color = if (allChecked) {
                                Color(tag.colorHex).copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("tag_row_${tag.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TagDot(colorHex = tag.colorHex, size = 14.dp)

                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (totalTagged > 0) {
                                Text(
                                    text = "$totalTagged",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!tag.isSystemDefault) {
                                IconButton(
                                    onClick = { onDeleteTag(tag.id) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("delete_custom_tag_${tag.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Delete ${tag.name}",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .then(
                                        when {
                                            allChecked -> Modifier.background(Color(tag.colorHex))
                                            partialChecked -> Modifier.background(Color(tag.colorHex).copy(alpha = 0.55f))
                                            else -> Modifier.border(
                                                width = 1.4.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                                shape = CircleShape
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    allChecked -> Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Tagged",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    partialChecked -> Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "Partially tagged",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
