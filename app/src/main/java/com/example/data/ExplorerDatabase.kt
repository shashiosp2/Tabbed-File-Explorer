package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tabPathsSerialized: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val path: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val path: String,
    val label: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_records")
data class TrashRecordEntity(
    @PrimaryKey val epochTimestamp: Long,
    val originalPath: String,
    val originalName: String,
    val trashedPath: String,
    val epochFolderPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val deletedAt: Long = epochTimestamp
)

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val originalPath: String,
    val vaultFileName: String,
    val sizeBytes: Long,
    val encryptedAt: Long = System.currentTimeMillis()
)

@Dao
interface ExplorerDao {
    // Workspaces
    @Query("SELECT * FROM workspaces ORDER BY id ASC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: Long): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: WorkspaceEntity): Long

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspace(id: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteBookmarkByPath(path: String)

    // History
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Trash Records
    @Query("SELECT * FROM trash_records ORDER BY epochTimestamp DESC")
    fun observeTrashRecords(): Flow<List<TrashRecordEntity>>

    @Query("SELECT * FROM trash_records ORDER BY epochTimestamp DESC")
    suspend fun getAllTrashRecords(): List<TrashRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashRecord(record: TrashRecordEntity)

    @Query("DELETE FROM trash_records WHERE epochTimestamp = :epoch")
    suspend fun deleteTrashRecord(epoch: Long)

    @Query("DELETE FROM trash_records")
    suspend fun clearTrashRecords()

    // Vault Entries
    @Query("SELECT * FROM vault_entries ORDER BY encryptedAt DESC")
    fun observeVaultEntries(): Flow<List<VaultEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntryEntity): Long

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteVaultEntry(id: Long)
}

@Database(
    entities = [
        WorkspaceEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        TrashRecordEntity::class,
        VaultEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ExplorerDatabase : RoomDatabase() {
    abstract fun explorerDao(): ExplorerDao

    companion object {
        @Volatile
        private var INSTANCE: ExplorerDatabase? = null

        fun getInstance(context: Context): ExplorerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExplorerDatabase::class.java,
                    "tabbed_file_explorer.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
