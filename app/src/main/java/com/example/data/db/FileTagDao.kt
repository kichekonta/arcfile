package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTagDao {

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM file_tags ORDER BY taggedAtMillis DESC")
    fun observeAllFileTagRefs(): Flow<List<FileTagCrossRefEntity>>

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun countTags(): Int

    @Query("SELECT MAX(sortOrder) FROM tags")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)

    @Query("DELETE FROM file_tags WHERE tagId = :tagId")
    suspend fun deleteRefsForTag(tagId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileTagRef(ref: FileTagCrossRefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileTagRefs(refs: List<FileTagCrossRefEntity>)

    @Query("DELETE FROM file_tags WHERE filePathOrUri = :filePathOrUri AND tagId = :tagId")
    suspend fun deleteFileTagRef(filePathOrUri: String, tagId: String)

    @Query("DELETE FROM file_tags WHERE filePathOrUri = :filePathOrUri")
    suspend fun deleteAllTagsForFile(filePathOrUri: String)

    @Query("SELECT * FROM file_tags WHERE filePathOrUri = :filePathOrUri")
    suspend fun getRefsForFile(filePathOrUri: String): List<FileTagCrossRefEntity>

    @Query("DELETE FROM file_tags")
    suspend fun clearAllRefs(): Int

    @Query("DELETE FROM tags WHERE isSystemDefault = 0")
    suspend fun deleteCustomTags(): Int
}
