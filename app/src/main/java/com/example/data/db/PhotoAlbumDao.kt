package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoAlbumDao {

    @Query("SELECT * FROM photo_albums ORDER BY sortOrder ASC, updatedAtMillis DESC, name COLLATE NOCASE ASC")
    fun observeAlbums(): Flow<List<PhotoAlbumEntity>>

    @Query("SELECT * FROM photo_albums")
    suspend fun getAllAlbumsOnce(): List<PhotoAlbumEntity>

    @Query("SELECT * FROM album_images ORDER BY addedAtMillis DESC")
    fun observeAllAlbumImages(): Flow<List<AlbumImageCrossRefEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: PhotoAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbumImages(items: List<AlbumImageCrossRefEntity>)

    @Query("UPDATE photo_albums SET name = :name, updatedAtMillis = :updatedAt WHERE id = :albumId")
    suspend fun renameAlbum(albumId: String, name: String, updatedAt: Long)

    @Query("UPDATE photo_albums SET coverPathOrUri = :coverPath, coverIsSaf = :coverIsSaf, updatedAtMillis = :updatedAt WHERE id = :albumId")
    suspend fun updateCover(albumId: String, coverPath: String?, coverIsSaf: Boolean, updatedAt: Long)

    @Query("UPDATE photo_albums SET updatedAtMillis = :updatedAt WHERE id = :albumId")
    suspend fun touchAlbum(albumId: String, updatedAt: Long)

    @Query("DELETE FROM album_images WHERE albumId = :albumId AND imagePathOrUri IN (:keys)")
    suspend fun deleteImagesFromAlbum(albumId: String, keys: List<String>)

    @Query("DELETE FROM album_images WHERE imagePathOrUri IN (:keys)")
    suspend fun deleteImageAcrossAlbums(keys: List<String>)

    @Query("DELETE FROM album_images WHERE albumId = :albumId")
    suspend fun deleteAllImagesForAlbum(albumId: String)

    @Query("DELETE FROM photo_albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: String)

    @Transaction
    suspend fun deleteAlbumAndMappings(albumId: String) {
        deleteAllImagesForAlbum(albumId)
        deleteAlbumById(albumId)
    }

    @Query("DELETE FROM album_images WHERE albumId IN (SELECT id FROM photo_albums WHERE UPPER(mediaSection) = UPPER(:mediaSection))")
    suspend fun deleteAllImagesForSection(mediaSection: String): Int

    @Query("DELETE FROM photo_albums WHERE UPPER(mediaSection) = UPPER(:mediaSection)")
    suspend fun deleteAlbumsBySection(mediaSection: String): Int

    @Transaction
    suspend fun clearSectionAlbums(mediaSection: String): Int {
        deleteAllImagesForSection(mediaSection)
        return deleteAlbumsBySection(mediaSection)
    }
}
