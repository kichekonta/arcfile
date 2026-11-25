package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderLayoutDao {

    @Query("SELECT * FROM folder_layouts ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<FolderLayoutEntity>>

    @Query("SELECT * FROM folder_layouts WHERE folderKey = :folderKey LIMIT 1")
    fun observeByKey(folderKey: String): Flow<FolderLayoutEntity?>

    @Query("SELECT * FROM folder_layouts WHERE folderKey = :folderKey LIMIT 1")
    suspend fun getByKey(folderKey: String): FolderLayoutEntity?

    @Query("SELECT folderKey FROM folder_layouts")
    suspend fun getAllKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FolderLayoutEntity)

    @Query("DELETE FROM folder_layouts WHERE folderKey = :folderKey")
    suspend fun deleteByKey(folderKey: String): Int

    @Query("DELETE FROM folder_layouts WHERE folderKey IN (:folderKeys)")
    suspend fun deleteByKeys(folderKeys: List<String>): Int

    @Query("DELETE FROM folder_layouts")
    suspend fun clearAll(): Int
}
