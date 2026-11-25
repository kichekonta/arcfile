package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val sortOrder: Int,
    val isSystemDefault: Boolean
)

@Entity(
    tableName = "file_tags",
    primaryKeys = ["filePathOrUri", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class FileTagCrossRefEntity(
    val filePathOrUri: String,
    val tagId: String,
    val fileName: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val extension: String,
    val isSafUri: Boolean,
    val parentPathOrUri: String,
    val taggedAtMillis: Long = System.currentTimeMillis()
)
