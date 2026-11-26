package com.example.engine

import android.content.Context
import com.example.data.ExplorerDao
import com.example.data.TrashRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Manages the Recycle Bin / Trash system.
 *
 * Saves deleted files and folders into `/sdcard/.Trash/<epoch_timestamp>/`
 * with a `.trash_meta.json` sidecar storing original path metadata for 1-tap restoration.
 * If `/sdcard` is not yet writable (before All Files Access is granted on Android 11+),
 * falls back cleanly to `<appExternalFilesDir>/.Trash/<epoch_timestamp>/` and surfaces
 * the exact active trash path in the UI.
 */
class TrashManager(
    private val context: Context,
    private val dao: ExplorerDao
) {
    companion object {
        const val PRIMARY_SDCARD_TRASH = "/sdcard/.Trash"
        private const val META_FILE_NAME = ".trash_meta.json"
        private val EPOCH_DIR_REGEX = Regex("^\\d{10,16}$")
    }

    fun resolveActiveTrashRoot(): Pair<File, Boolean> {
        val sdcardTrash = File(PRIMARY_SDCARD_TRASH)
        val sdcardRoot = File("/sdcard")
        if (FileSystemEngine.hasAllFilesAccess() && sdcardRoot.exists()) {
            if (!sdcardTrash.exists()) {
                sdcardTrash.mkdirs()
            }
            if (sdcardTrash.exists() && sdcardTrash.canWrite()) {
                return sdcardTrash to true
            }
        }
        val fallbackBase = context.getExternalFilesDir(null) ?: context.filesDir
        val fallbackTrash = File(fallbackBase, ".Trash")
        if (!fallbackTrash.exists()) fallbackTrash.mkdirs()
        return fallbackTrash to false
    }

    fun getActiveTrashPath(): String = resolveActiveTrashRoot().first.absolutePath

    fun isUsingPrimarySdcardTrash(): Boolean = resolveActiveTrashRoot().second

    suspend fun moveToTrash(paths: List<String>): Result<List<TrashItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val (trashRoot, _) = resolveActiveTrashRoot()
            if (!trashRoot.exists() && !trashRoot.mkdirs()) {
                error("Unable to create Trash directory at ${trashRoot.absolutePath}")
            }

            val trashedItems = mutableListOf<TrashItem>()
            var lastEpoch = System.currentTimeMillis()

            for (path in paths) {
                val src = File(path)
                if (!src.exists()) continue

                // Prevent trashing the .Trash folder itself
                if (src.canonicalPath == trashRoot.canonicalPath) continue

                // Guarantee unique epoch-timestamped folder name per deleted item
                var epoch = System.currentTimeMillis()
                if (epoch <= lastEpoch) epoch = lastEpoch + 1
                lastEpoch = epoch

                var epochDir = File(trashRoot, epoch.toString())
                while (epochDir.exists()) {
                    epoch += 1
                    epochDir = File(trashRoot, epoch.toString())
                }
                if (!epochDir.mkdirs()) {
                    error("Failed to create epoch trash folder: ${epochDir.absolutePath}")
                }

                val isDir = src.isDirectory
                val sizeBytes = computeSizeQuick(src)
                val dest = File(epochDir, src.name)

                val moved = src.renameTo(dest)
                if (!moved) {
                    FileSystemEngine.copyRecursive(src, dest)
                    FileSystemEngine.deleteRecursive(src)
                }

                // Write metadata sidecar inside /sdcard/.Trash/<epoch>/
                val metaJson = JSONObject().apply {
                    put("epochTimestamp", epoch)
                    put("originalPath", src.absolutePath)
                    put("originalName", src.name)
                    put("trashedPath", dest.absolutePath)
                    put("sizeBytes", sizeBytes)
                    put("isDirectory", isDir)
                    put("deletedAt", epoch)
                }
                runCatching {
                    File(epochDir, META_FILE_NAME).writeText(metaJson.toString(2))
                }

                val entity = TrashRecordEntity(
                    epochTimestamp = epoch,
                    originalPath = src.absolutePath,
                    originalName = src.name,
                    trashedPath = dest.absolutePath,
                    epochFolderPath = epochDir.absolutePath,
                    sizeBytes = sizeBytes,
                    isDirectory = isDir,
                    deletedAt = epoch
                )
                dao.insertTrashRecord(entity)

                trashedItems.add(
                    TrashItem(
                        epochTimestamp = epoch,
                        originalPath = src.absolutePath,
                        originalName = src.name,
                        trashedPath = dest.absolutePath,
                        epochFolderPath = epochDir.absolutePath,
                        sizeBytes = sizeBytes,
                        isDirectory = isDir,
                        deletedAt = epoch
                    )
                )
            }
            trashedItems
        }
    }

    /**
     * Scans actual epoch-timestamped folders in `/sdcard/.Trash` (and fallback trash if present)
     * and reconciles them with `.trash_meta.json` and Room records.
     */
    suspend fun listTrashItems(): List<TrashItem> = withContext(Dispatchers.IO) {
        val dbRecords = dao.getAllTrashRecords().associateBy { it.epochTimestamp }
        val discovered = linkedMapOf<Long, TrashItem>()

        val rootsToScan = buildList {
            val primary = File(PRIMARY_SDCARD_TRASH)
            if (primary.exists() && primary.canRead()) add(primary)
            val (active, isPrimary) = resolveActiveTrashRoot()
            if (!isPrimary && active.exists() && active.canRead()) add(active)
        }

        for (trashRoot in rootsToScan) {
            val epochDirs = trashRoot.listFiles { f ->
                f.isDirectory && EPOCH_DIR_REGEX.matches(f.name)
            } ?: continue

            for (epochDir in epochDirs) {
                val epoch = epochDir.name.toLongOrNull() ?: continue
                val metaFile = File(epochDir, META_FILE_NAME)
                val payloadFiles = epochDir.listFiles { f -> f.name != META_FILE_NAME } ?: emptyArray()
                if (payloadFiles.isEmpty()) continue

                val payload = payloadFiles.first()
                val parsedMeta = if (metaFile.exists()) {
                    runCatching { JSONObject(metaFile.readText()) }.getOrNull()
                } else null
                val dbRec = dbRecords[epoch]

                val originalName = parsedMeta?.optString("originalName")?.takeIf { it.isNotEmpty() }
                    ?: dbRec?.originalName
                    ?: payload.name
                val originalPath = parsedMeta?.optString("originalPath")?.takeIf { it.isNotEmpty() }
                    ?: dbRec?.originalPath
                    ?: "/sdcard/$originalName"
                val sizeBytes = parsedMeta?.optLong("sizeBytes", -1L)?.takeIf { it >= 0L }
                    ?: dbRec?.sizeBytes
                    ?: computeSizeQuick(payload)

                discovered[epoch] = TrashItem(
                    epochTimestamp = epoch,
                    originalPath = originalPath,
                    originalName = originalName,
                    trashedPath = payload.absolutePath,
                    epochFolderPath = epochDir.absolutePath,
                    sizeBytes = sizeBytes,
                    isDirectory = payload.isDirectory,
                    deletedAt = epoch
                )
            }
        }

        discovered.values.sortedByDescending { it.epochTimestamp }
    }

    suspend fun restoreItem(item: TrashItem): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val trashedFile = File(item.trashedPath)
            require(trashedFile.exists()) { "Trashed item no longer exists on disk" }

            val originalFile = File(item.originalPath)
            val parentDir = originalFile.parentFile ?: File("/sdcard")
            if (!parentDir.exists()) parentDir.mkdirs()

            val target = if (originalFile.exists()) {
                FileSystemEngine.resolveUniqueTarget(parentDir, originalFile.name)
            } else {
                originalFile
            }

            val moved = trashedFile.renameTo(target)
            if (!moved) {
                FileSystemEngine.copyRecursive(trashedFile, target)
                FileSystemEngine.deleteRecursive(trashedFile)
            }

            // Clean up epoch folder and sidecar metadata
            val epochDir = File(item.epochFolderPath)
            FileSystemEngine.deleteRecursive(epochDir)
            dao.deleteTrashRecord(item.epochTimestamp)
            target
        }
    }

    suspend fun permanentlyDeleteItem(item: TrashItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val epochDir = File(item.epochFolderPath)
            if (epochDir.exists()) {
                FileSystemEngine.deleteRecursive(epochDir)
            } else {
                File(item.trashedPath).takeIf { it.exists() }?.let {
                    FileSystemEngine.deleteRecursive(it)
                }
            }
            dao.deleteTrashRecord(item.epochTimestamp)
        }
    }

    suspend fun emptyTrash(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val items = listTrashItems()
            var removed = 0
            for (item in items) {
                val epochDir = File(item.epochFolderPath)
                if (FileSystemEngine.deleteRecursive(epochDir)) {
                    removed++
                }
            }
            dao.clearTrashRecords()
            removed
        }
    }

    private fun computeSizeQuick(file: File): Long {
        if (!file.isDirectory) return file.length()
        var sum = 0L
        val stack = ArrayDeque<File>()
        stack.add(file)
        var visited = 0
        while (stack.isNotEmpty() && visited < 1000) {
            val cur = stack.removeLast()
            visited++
            cur.listFiles()?.forEach { child ->
                if (child.isDirectory) stack.add(child) else sum += child.length()
            }
        }
        return sum
    }
}
