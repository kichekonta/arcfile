package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderLayoutEntity::class,
        TagEntity::class,
        FileTagCrossRefEntity::class,
        PhotoAlbumEntity::class,
        AlbumImageCrossRefEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ArcDatabase : RoomDatabase() {

    abstract fun folderLayoutDao(): FolderLayoutDao
    abstract fun fileTagDao(): FileTagDao
    abstract fun photoAlbumDao(): PhotoAlbumDao

    companion object {
        @Volatile
        private var INSTANCE: ArcDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isSystemDefault` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_tags` (
                        `filePathOrUri` TEXT NOT NULL,
                        `tagId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `isDirectory` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `lastModifiedMillis` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `extension` TEXT NOT NULL,
                        `isSafUri` INTEGER NOT NULL,
                        `parentPathOrUri` TEXT NOT NULL,
                        `taggedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`filePathOrUri`, `tagId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_tags_tagId` ON `file_tags` (`tagId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `folder_layouts` ADD COLUMN `customOrderKeys` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_albums` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `coverPathOrUri` TEXT,
                        `coverIsSaf` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `album_images` (
                        `albumId` TEXT NOT NULL,
                        `imagePathOrUri` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `lastModifiedMillis` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `extension` TEXT NOT NULL,
                        `isSafUri` INTEGER NOT NULL,
                        `parentPathOrUri` TEXT NOT NULL,
                        `addedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`albumId`, `imagePathOrUri`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_images_albumId` ON `album_images` (`albumId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photo_albums` ADD COLUMN `mediaSection` TEXT NOT NULL DEFAULT 'IMAGES'")
            }
        }

        fun getInstance(context: Context): ArcDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArcDatabase::class.java,
                    "arc_files_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
