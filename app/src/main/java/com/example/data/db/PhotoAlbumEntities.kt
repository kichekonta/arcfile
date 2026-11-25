package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.FileItem

@Entity(tableName = "photo_albums")
data class PhotoAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverPathOrUri: String? = null,
    val coverIsSaf: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val mediaSection: String = "IMAGES"
)

@Entity(
    tableName = "album_images",
    primaryKeys = ["albumId", "imagePathOrUri"],
    indices = [Index(value = ["albumId"])]
)
data class AlbumImageCrossRefEntity(
    val albumId: String,
    val imagePathOrUri: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val extension: String,
    val isSafUri: Boolean,
    val parentPathOrUri: String,
    val addedAtMillis: Long = System.currentTimeMillis()
)

fun AlbumImageCrossRefEntity.toFileItem(): FileItem = FileItem(
    id = imagePathOrUri,
    name = fileName,
    pathOrUri = imagePathOrUri,
    isDirectory = false,
    sizeBytes = sizeBytes,
    lastModifiedMillis = lastModifiedMillis,
    mimeType = mimeType,
    extension = extension,
    isSafUri = isSafUri,
    parentPathOrUri = parentPathOrUri,
    childCount = null
)
