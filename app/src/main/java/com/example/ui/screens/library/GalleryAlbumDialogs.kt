package com.example.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.ArcImageLoader
import com.example.data.CacheBucket
import com.example.data.FileItem
import com.example.data.LibrarySection
import com.example.data.PhotoAlbum
import com.example.ui.components.common.ArcModalCard
import com.example.ui.components.common.ArcUnderlineTextField
import com.example.ui.components.common.ConfigureEdgeToEdgeDialogWindow
import java.io.File

@Composable
fun CreateOrRenameAlbumDialog(
    initialName: String = "",
    title: String,
    subtitle: String,
    confirmLabel: String,
    existingAlbumNames: Set<String> = emptySet(),
    initialItemCount: Int = 0,
    mediaSection: LibrarySection = LibrarySection.IMAGES,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val itemSingular = if (mediaSection == LibrarySection.MOVIES) "video" else "photo"
    val itemPlural = if (mediaSection == LibrarySection.MOVIES) "videos" else "photos"
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val duplicate = trimmed.isNotEmpty() &&
        !trimmed.equals(initialName.trim(), ignoreCase = true) &&
        existingAlbumNames.any { it.equals(trimmed, ignoreCase = true) }
    val isValid = trimmed.isNotEmpty() && !duplicate

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcModalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clickable(enabled = false, onClick = {})
                    .testTag("album_name_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (initialItemCount > 0) {
                            "$subtitle · $initialItemCount selected ${if (initialItemCount == 1) itemSingular else itemPlural} will be added"
                        } else {
                            subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ArcUnderlineTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Album Name",
                        placeholder = "e.g. Favorites, Travel, Receipts",
                        isError = duplicate,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(trimmed) }),
                        testTag = "album_name_input"
                    )
                    if (duplicate) {
                        Text(
                            text = "An album with this name already exists.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.testTag("album_dialog_cancel")) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { if (isValid) onConfirm(trimmed) },
                            enabled = isValid,
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.testTag("album_dialog_confirm")
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddToAlbumDialog(
    targetImages: List<FileItem>,
    customAlbums: List<PhotoAlbum>,
    mediaSection: LibrarySection = LibrarySection.IMAGES,
    onDismiss: () -> Unit,
    onCreateNewAlbumWithTargets: () -> Unit,
    onConfirmAlbumIds: (Set<String>) -> Unit
) {
    var selectedAlbumIds by remember(customAlbums) { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current
    val isMovieMode = mediaSection == LibrarySection.MOVIES
    val cacheBucket = if (isMovieMode) CacheBucket.MOVIE_VIDEOS else CacheBucket.GALLERY_IMAGES
    val imageLoader = remember(context, cacheBucket) { ArcImageLoader.getForBucket(context, cacheBucket) }
    val itemSingular = if (isMovieMode) "video" else "photo"
    val itemPlural = if (isMovieMode) "videos" else "photos"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcModalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .clickable(enabled = false, onClick = {})
                    .testTag("add_to_album_dialog")
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add to Album",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${targetImages.size} ${if (targetImages.size == 1) itemSingular else itemPlural} selected · Original files stay untouched",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inline "+ Create New Album" shortcut row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCreateNewAlbumWithTargets)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                            .testTag("add_to_album_create_new"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Album",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Create an album with these $itemPlural",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )

                    if (customAlbums.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No custom albums yet. Tap New Album above to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            items(customAlbums, key = { it.id }) { album ->
                                val checked = album.id in selectedAlbumIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedAlbumIds = if (checked) {
                                                selectedAlbumIds - album.id
                                            } else {
                                                selectedAlbumIds + album.id
                                            }
                                        }
                                        .padding(horizontal = 18.dp, vertical = 9.dp)
                                        .testTag("add_to_album_row_${album.name}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (album.coverPathOrUri != null) {
                                            val req = remember(album.coverPathOrUri, album.coverIsSaf) {
                                                ImageRequest.Builder(context)
                                                    .data(if (album.coverIsSaf) album.coverPathOrUri else File(album.coverPathOrUri))
                                                    .size(160, 160)
                                                    .precision(Precision.INEXACT)
                                                    .diskCachePolicy(CachePolicy.ENABLED)
                                                    .crossfade(false)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = req,
                                                imageLoader = imageLoader,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isMovieMode) Icons.Outlined.Movie else Icons.Outlined.PhotoLibrary,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = album.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${album.itemCount} ${if (album.itemCount == 1) itemSingular else itemPlural}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { onConfirmAlbumIds(selectedAlbumIds) },
                            enabled = selectedAlbumIds.isNotEmpty(),
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.testTag("add_to_album_confirm")
                        ) {
                            Text("Add (${selectedAlbumIds.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PickImagesForAlbumDialog(
    albumName: String,
    allImages: List<FileItem>,
    alreadyInAlbumKeys: Set<String>,
    mediaSection: LibrarySection = LibrarySection.IMAGES,
    onDismiss: () -> Unit,
    onConfirmSelection: (List<FileItem>) -> Unit
) {
    val availableImages = remember(allImages, alreadyInAlbumKeys) {
        allImages.filter { LibraryDomainUtils.normalizedKey(it) !in alreadyInAlbumKeys }
    }
    var pickedIds by remember(albumName) { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current
    val isMovieMode = mediaSection == LibrarySection.MOVIES
    val cacheBucket = if (isMovieMode) CacheBucket.MOVIE_VIDEOS else CacheBucket.GALLERY_IMAGES
    val imageLoader = remember(context, cacheBucket) { ArcImageLoader.getForBucket(context, cacheBucket) }
    val titlePrefix = if (isMovieMode) "Add Videos to" else "Add Photos to"
    val itemPlural = if (isMovieMode) "videos" else "photos"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ConfigureEdgeToEdgeDialogWindow()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            ArcModalCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clickable(enabled = false, onClick = {})
                    .testTag("pick_album_images_dialog")
            ) {
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$titlePrefix $albumName",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (pickedIds.isEmpty()) "Tap $itemPlural to select" else "${pickedIds.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableImages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All indexed device $itemPlural are already inside this album.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 380.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(availableImages, key = { it.id }) { img ->
                                val selected = img.id in pickedIds
                                val req = remember(img.pathOrUri, img.lastModifiedMillis, img.isSafUri, cacheBucket) {
                                    ImageRequest.Builder(context)
                                        .data(if (img.isSafUri) img.pathOrUri else File(img.pathOrUri))
                                        .size(220, 220)
                                        .precision(Precision.INEXACT)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .crossfade(false)
                                        .build()
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isMovieMode) Color(0xFF141821) else MaterialTheme.colorScheme.surfaceContainer)
                                        .border(
                                            width = if (selected) 2.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            pickedIds = if (selected) pickedIds - img.id else pickedIds + img.id
                                        }
                                        .testTag("pick_image_tile_${img.name}")
                                ) {
                                    AsyncImage(
                                        model = req,
                                        imageLoader = imageLoader,
                                        contentDescription = img.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isMovieMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.50f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else Color.Black.copy(alpha = 0.35f)
                                            )
                                            .border(
                                                width = 1.2.dp,
                                                color = Color.White.copy(alpha = 0.9f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val chosen = availableImages.filter { it.id in pickedIds }
                                onConfirmSelection(chosen)
                            },
                            enabled = pickedIds.isNotEmpty(),
                            shape = RoundedCornerShape(99.dp),
                            modifier = Modifier.testTag("pick_album_images_confirm")
                        ) {
                            Text("Add (${pickedIds.size})")
                        }
                    }
                }
            }
        }
    }
}
