package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

object FileSystemEngine {

    const val SDCARD_ROOT = "/sdcard"

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getDefaultRootPath(context: Context): String {
        val sdcard = File(SDCARD_ROOT)
        if (hasAllFilesAccess() && sdcard.exists() && sdcard.canRead()) {
            return sdcard.absolutePath
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists() && ext.canRead() && ext.listFiles() != null) {
            return ext.absolutePath
        }
        val appExternal = context.getExternalFilesDir(null) ?: context.filesDir
        seedWelcomeSamplesIfEmpty(appExternal)
        return appExternal.absolutePath
    }

    /**
     * When running inside a fresh sandbox without full /sdcard access yet,
     * creates a small set of real developer/power-user sample files inside
     * the app's own external directory so every built-in viewer (Code, JSON,
     * CSV, Markdown, Hex, Archive) can be explored immediately.
     */
    private fun seedWelcomeSamplesIfEmpty(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        val existing = dir.listFiles()
        if (existing != null && existing.isNotEmpty()) return

        runCatching {
            File(dir, "README.md").writeText(
                """
                # Tabbed File Explorer — Sandbox Workspace
                
                Welcome to **Tabbed File Explorer**. Grant **All Files Access** from the top banner to browse `/sdcard` directly and move deleted files into `/sdcard/.Trash/<epoch_timestamp>/`.
                
                ## Power Features
                - Multi-tabbed browsing & persistent named Workspaces
                - Dual-Pane Split View with 1-tap Copy/Move across panes
                - Epoch-timestamped Trash (`/sdcard/.Trash/<epoch_ms>/`)
                - Built-in Code Editor, Binary Hex Viewer, JSON Tree, CSV Grid, SQLite Inspector & ZIP Explorer
                - SHA-256 Duplicate Finder, AES-256-GCM Encrypted Vault & LAN HTTP File Server
                """.trimIndent()
            )
            File(dir, "config.json").writeText(
                """
                {
                  "app": "Tabbed File Explorer",
                  "trashRoot": "/sdcard/.Trash",
                  "trashScheme": "epoch_timestamp_folders",
                  "viewers": ["Text/Code", "Hex", "JSON Tree", "CSV Table", "SQLite DB", "Markdown", "ZIP Archive"],
                  "powerTools": {
                    "dualPane": true,
                    "batchRenameRegex": true,
                    "checksums": ["MD5", "SHA-1", "SHA-256", "SHA-512"],
                    "lanHttpServer": true,
                    "aes256Vault": true
                  }
                }
                """.trimIndent()
            )
            File(dir, "benchmarks.csv").writeText(
                """
                Operation,Engine,Throughput_MBps,Notes
                Directory Scan,Kotlin IO,420,Cached metadata & POSIX flags
                SHA-256 Checksum,JCA MessageDigest,315,64KB buffered stream
                ZIP Compress,java.util.zip,180,Strips macOS .DS_Store
                AES-256-GCM Vault,javax.crypto,260,PBKDF2 100k iterations
                """.trimIndent()
            )
            val notesDir = File(dir, "Projects")
            notesDir.mkdirs()
            File(notesDir, "script.kt").writeText(
                """
                fun main() {
                    println("Tabbed File Explorer — Power User Mode Active")
                }
                """.trimIndent()
            )
        }
    }

    fun getStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val list = mutableListOf<StorageVolumeInfo>()
        val primaryPath = if (File(SDCARD_ROOT).exists()) SDCARD_ROOT
        else Environment.getExternalStorageDirectory()?.absolutePath ?: context.filesDir.absolutePath
        statVolume("Internal Storage", primaryPath)?.let { list.add(it) }

        val appExt = context.getExternalFilesDir(null)
        if (appExt != null && appExt.absolutePath != primaryPath) {
            statVolume("App Workspace", appExt.absolutePath)?.let { list.add(it) }
        }

        // Check secondary removable storage roots if mounted
        context.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { idx, dir ->
            val rootPath = dir.absolutePath.substringBefore("/Android/data")
            val candidate = File(rootPath)
            if (candidate.exists() && candidate.canRead()) {
                statVolume("SD Card ${idx + 1}", candidate.absolutePath)?.let { list.add(it) }
            }
        }
        return list
    }

    fun statVolume(label: String, path: String): StorageVolumeInfo? {
        return runCatching {
            val file = File(path)
            val target = if (file.exists()) file else Environment.getDataDirectory()
            val stat = StatFs(target.absolutePath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val used = (total - free).coerceAtLeast(0L)
            StorageVolumeInfo(
                label = label,
                path = path,
                totalBytes = total,
                usedBytes = used,
                freeBytes = free
            )
        }.getOrNull()
    }

    suspend fun listDirectory(
        path: String,
        showHidden: Boolean,
        sortOption: SortOption,
        ascending: Boolean,
        foldersFirst: Boolean
    ): Result<List<FileNode>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(path)
            if (!dir.exists()) error("Directory does not exist: $path")
            if (!dir.isDirectory) error("Path is not a directory: $path")
            val rawFiles = dir.listFiles()
                ?: error("Access denied to $path. Grant All Files Access or pick an accessible directory.")

            val nodes = rawFiles
                .asSequence()
                .filter { showHidden || !it.name.startsWith(".") }
                .map { toFileNode(it) }
                .toList()

            sortNodes(nodes, sortOption, ascending, foldersFirst)
        }
    }

    fun sortNodes(
        nodes: List<FileNode>,
        sortOption: SortOption,
        ascending: Boolean,
        foldersFirst: Boolean
    ): List<FileNode> {
        val comparator = Comparator<FileNode> { a, b ->
            if (foldersFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val cmp = when (sortOption) {
                SortOption.NAME -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                SortOption.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                SortOption.DATE -> a.lastModified.compareTo(b.lastModified)
                SortOption.EXTENSION -> {
                    val extCmp = a.extension.lowercase(Locale.ROOT).compareTo(b.extension.lowercase(Locale.ROOT))
                    if (extCmp != 0) extCmp
                    else a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                }
            }
            if (ascending) cmp else -cmp
        }
        return nodes.sortedWith(comparator)
    }

    fun toFileNode(file: File): FileNode {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
        val isSym = runCatching {
            file.canonicalPath != file.absolutePath
        }.getOrDefault(false)
        val childCount = if (isDir) {
            runCatching { file.list()?.size }.getOrNull()
        } else null
        return FileNode(
            path = file.absolutePath,
            name = file.name.ifEmpty { file.absolutePath },
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            extension = ext,
            category = classifyCategory(isDir, ext),
            permissions = formatPermissions(file),
            isHidden = file.name.startsWith("."),
            isReadable = file.canRead(),
            isWritable = file.canWrite(),
            childCount = childCount,
            isSymlink = isSym
        )
    }

    fun formatPermissions(file: File): String {
        val typeChar = if (file.isDirectory) 'd' else '-'
        val r = if (file.canRead()) 'r' else '-'
        val w = if (file.canWrite()) 'w' else '-'
        val x = if (file.canExecute()) 'x' else '-'
        return "$typeChar$r$w$x"
    }

    fun classifyCategory(isDirectory: Boolean, ext: String): FileCategory {
        if (isDirectory) return FileCategory.FOLDER
        return when (ext.lowercase(Locale.ROOT)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "avif" -> FileCategory.IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "flv", "ts", "3gp", "m4v" -> FileCategory.VIDEO
            "mp3", "wav", "flac", "aac", "ogg", "opus", "m4a", "m4r", "wma", "amr" -> FileCategory.AUDIO
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "jar", "cbz", "cbr" -> FileCategory.ARCHIVE
            "db", "sqlite", "sqlite3", "db3" -> FileCategory.DATABASE
            "apk", "apks", "xapk", "aab" -> FileCategory.APK
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "epub", "hwp", "hwpx" -> FileCategory.DOCUMENT
            "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf",
            "properties", "log", "csv", "tsv", "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx",
            "c", "cpp", "h", "hpp", "rs", "go", "sh", "bash", "zsh", "html", "htm", "css", "scss",
            "sql", "gradle", "env", "gitignore", "dockerfile", "makefile" -> FileCategory.CODE
            else -> FileCategory.BINARY
        }
    }

    fun suggestViewerMode(node: FileNode): ViewerMode? {
        if (node.isDirectory) return null
        return when (node.extension.lowercase(Locale.ROOT)) {
            "json" -> ViewerMode.JSON_TREE
            "csv", "tsv" -> ViewerMode.CSV_TABLE
            "db", "sqlite", "sqlite3", "db3" -> ViewerMode.SQLITE_DB
            "md", "markdown" -> ViewerMode.MARKDOWN
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif" -> ViewerMode.IMAGE
            "zip", "jar", "apk", "cbz" -> ViewerMode.ARCHIVE
            "txt", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "log",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp",
            "rs", "go", "sh", "bash", "html", "htm", "css", "sql", "gradle", "env" -> ViewerMode.TEXT_EDITOR
            else -> if (node.sizeBytes in 0..(512 * 1024) && isLikelyTextFile(node.file)) {
                ViewerMode.TEXT_EDITOR
            } else {
                ViewerMode.HEX_VIEWER
            }
        }
    }

    private fun isLikelyTextFile(file: File): Boolean {
        return runCatching {
            val buf = ByteArray(1024)
            val read = FileInputStream(file).use { it.read(buf) }
            if (read <= 0) return true
            var nonPrintable = 0
            for (i in 0 until read) {
                val b = buf[i].toInt() and 0xFF
                if (b == 0) return false
                if (b < 0x09 || (b in 0x0E..0x1F)) nonPrintable++
            }
            nonPrintable.toFloat() / read < 0.08f
        }.getOrDefault(false)
    }

    suspend fun createFolder(parentPath: String, relativeName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = relativeName.trim().trimStart('/')
            require(sanitized.isNotEmpty()) { "Folder name cannot be empty" }
            val target = File(parentPath, sanitized)
            if (target.exists()) error("Already exists: ${target.name}")
            if (!target.mkdirs()) error("Failed to create folder (check permissions)")
            target
        }
    }

    suspend fun createFile(parentPath: String, fileName: String, initialContent: String = ""): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = fileName.trim().trimStart('/')
                require(sanitized.isNotEmpty()) { "File name cannot be empty" }
                val target = File(parentPath, sanitized)
                target.parentFile?.mkdirs()
                if (target.exists()) error("Already exists: ${target.name}")
                if (!target.createNewFile()) error("Failed to create file (check permissions)")
                if (initialContent.isNotEmpty()) {
                    target.writeText(initialContent)
                }
                target
            }
        }

    suspend fun renameFile(path: String, newName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(path)
            require(source.exists()) { "Source does not exist" }
            val trimmed = newName.trim()
            require(trimmed.isNotEmpty() && !trimmed.contains('/')) { "Invalid filename" }
            val dest = File(source.parentFile, trimmed)
            if (dest.exists()) error("Target already exists: $trimmed")
            if (!source.renameTo(dest)) error("Rename failed")
            dest
        }
    }

    fun previewBatchRename(nodes: List<FileNode>, rule: BatchRenameRule): List<Pair<FileNode, String>> {
        return nodes.mapIndexed { idx, node ->
            val ext = if (node.isDirectory || !node.name.contains('.')) "" else ".${node.extension}"
            val baseOriginal = if (ext.isEmpty()) node.name else node.name.removeSuffix(ext)
            var base = baseOriginal

            if (rule.findText.isNotEmpty()) {
                base = if (rule.useRegex) {
                    runCatching { Regex(rule.findText).replace(base, rule.replaceText) }.getOrDefault(base)
                } else {
                    base.replace(rule.findText, rule.replaceText)
                }
            }

            base = "${rule.prefix}$base${rule.suffix}"

            if (rule.addSequence) {
                val seqNum = (rule.sequenceStart + idx).toString().padStart(rule.sequencePadding.coerceIn(1, 6), '0')
                base = "${base}_$seqNum"
            }

            base = when (rule.caseMode) {
                RenameCaseMode.NONE -> base
                RenameCaseMode.LOWER -> base.lowercase(Locale.ROOT)
                RenameCaseMode.UPPER -> base.uppercase(Locale.ROOT)
                RenameCaseMode.TITLE -> base.split(" ", "_", "-").joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
            }

            val finalName = (base + ext).ifBlank { node.name }
            node to finalName
        }
    }

    suspend fun executeBatchRename(pairs: List<Pair<FileNode, String>>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var count = 0
            for ((node, newName) in pairs) {
                if (node.name == newName) continue
                val src = File(node.path)
                val dest = File(src.parentFile, newName)
                if (!dest.exists() && src.renameTo(dest)) {
                    count++
                }
            }
            count
        }
    }

    suspend fun copyOrMove(
        sourcePaths: List<String>,
        destinationDirPath: String,
        isMove: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = File(destinationDirPath)
            require(destDir.exists() && destDir.isDirectory) { "Invalid destination folder" }
            var completed = 0
            for (srcPath in sourcePaths) {
                coroutineContext.ensureActive()
                val src = File(srcPath)
                if (!src.exists()) continue
                val dest = resolveUniqueTarget(destDir, src.name)
                if (dest.canonicalPath.startsWith(src.canonicalPath + File.separator)) {
                    error("Cannot copy or move folder into itself: ${src.name}")
                }
                if (isMove) {
                    if (src.renameTo(dest)) {
                        completed++
                    } else {
                        copyRecursive(src, dest)
                        deleteRecursive(src)
                        completed++
                    }
                } else {
                    copyRecursive(src, dest)
                    completed++
                }
            }
            completed
        }
    }

    fun resolveUniqueTarget(destDir: File, desiredName: String): File {
        var candidate = File(destDir, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val base = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(destDir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }

    fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            if (!dest.exists()) dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dest, child.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            dest.setLastModified(src.lastModified())
        }
    }

    fun deleteRecursive(target: File): Boolean {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        return target.delete()
    }

    suspend fun computeProperties(node: FileNode): FileProperties = withContext(Dispatchers.IO) {
        val file = node.file
        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val symlinkTarget = if (canonical != file.absolutePath) canonical else null
        var totalSize = 0L
        var files = 0
        var folders = 0

        if (file.isDirectory) {
            val stack = ArrayDeque<File>()
            stack.add(file)
            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val cur = stack.removeLast()
                cur.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        folders++
                        stack.add(child)
                    } else {
                        files++
                        totalSize += child.length()
                    }
                }
            }
        } else {
            totalSize = file.length()
            files = 1
        }

        val mime = if (file.isDirectory) {
            "inode/directory"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(node.extension.lowercase(Locale.ROOT))
                ?: "application/octet-stream"
        }

        FileProperties(
            node = node,
            canonicalPath = canonical,
            mimeType = mime,
            recursiveSizeBytes = totalSize,
            fileCount = files,
            folderCount = folders,
            ownerPermissions = formatPermissions(file),
            symlinkTarget = symlinkTarget
        )
    }

    suspend fun computeHashes(file: File): Result<HashResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists() && file.isFile) { "Hashes can only be computed for regular files" }
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val sha512 = MessageDigest.getInstance("SHA-512")

            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive()
                    md5.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    sha512.update(buffer, 0, read)
                }
            }
            HashResult(
                md5 = md5.digest().toHexString(),
                sha1 = sha1.digest().toHexString(),
                sha256 = sha256.digest().toHexString(),
                sha512 = sha512.digest().toHexString()
            )
        }
    }

    suspend fun searchRecursive(rootPath: String, filter: SearchFilter): List<FileNode> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileNode>()
            val root = File(rootPath)
            if (!root.exists() || !root.isDirectory) return@withContext results

            val regex = if (filter.useRegex && filter.query.isNotEmpty()) {
                runCatching {
                    val opts = if (filter.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    Regex(filter.query, opts)
                }.getOrNull()
            } else null

            val extWanted = filter.extensionFilter.trim().trimStart('.').lowercase(Locale.ROOT)
            val minBytes = filter.minSizeKb * 1024L
            val stack = ArrayDeque<File>()
            stack.add(root)

            while (stack.isNotEmpty() && results.size < 500) {
                coroutineContext.ensureActive()
                val current = stack.removeLast()
                val children = current.listFiles() ?: continue
                for (child in children) {
                    if (!filter.includeHidden && child.name.startsWith(".")) continue
                    if (child.isDirectory) {
                        stack.add(child)
                    }
                    if (extWanted.isNotEmpty() && child.extension.lowercase(Locale.ROOT) != extWanted) {
                        continue
                    }
                    if (!child.isDirectory && child.length() < minBytes) continue

                    val nameMatch = if (filter.query.isEmpty()) {
                        true
                    } else if (regex != null) {
                        regex.containsMatchIn(child.name)
                    } else {
                        child.name.contains(filter.query, ignoreCase = !filter.caseSensitive)
                    }

                    val contentMatch = if (!nameMatch && filter.searchContent && child.isFile &&
                        child.length() in 1..(2 * 1024 * 1024) && isLikelyTextFile(child)
                    ) {
                        runCatching {
                            val text = child.readText()
                            if (regex != null) regex.containsMatchIn(text)
                            else text.contains(filter.query, ignoreCase = !filter.caseSensitive)
                        }.getOrDefault(false)
                    } else false

                    if (nameMatch || contentMatch) {
                        results.add(toFileNode(child))
                        if (results.size >= 500) break
                    }
                }
            }
            results
        }

    suspend fun analyzeStorage(rootPath: String, largeFileThresholdMb: Long = 5L): StorageAnalysisReport =
        withContext(Dispatchers.IO) {
            val volume = statVolume("Storage", rootPath)
                ?: StorageVolumeInfo("Storage", rootPath, 0L, 0L, 0L)

            val root = File(rootPath)
            val categorySizeMap = mutableMapOf<FileCategory, Long>()
            val categoryCountMap = mutableMapOf<FileCategory, Int>()
            val largeFiles = mutableListOf<FileNode>()
            val emptyFolders = mutableListOf<FileNode>()
            val sizeGroups = mutableMapOf<Long, MutableList<FileNode>>()
            var totalFiles = 0
            var totalBytes = 0L
            val largeThresholdBytes = largeFileThresholdMb * 1024L * 1024L

            val stack = ArrayDeque<File>()
            if (root.exists() && root.isDirectory) {
                stack.add(root)
            }

            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.removeLast()
                if (dir.name == ".Trash") continue
                val children = dir.listFiles() ?: continue
                if (children.isEmpty() && dir.absolutePath != root.absolutePath) {
                    emptyFolders.add(toFileNode(dir))
                    continue
                }
                for (child in children) {
                    if (child.isDirectory) {
                        stack.add(child)
                    } else {
                        val node = toFileNode(child)
                        totalFiles++
                        totalBytes += node.sizeBytes
                        categorySizeMap[node.category] = (categorySizeMap[node.category] ?: 0L) + node.sizeBytes
                        categoryCountMap[node.category] = (categoryCountMap[node.category] ?: 0) + 1

                        if (node.sizeBytes >= largeThresholdBytes) {
                            largeFiles.add(node)
                        }
                        if (node.sizeBytes > 0L) {
                            sizeGroups.getOrPut(node.sizeBytes) { mutableListOf() }.add(node)
                        }
                    }
                }
            }

            // Duplicate detection: hash only size-colliding files (up to 400 candidates for responsiveness)
            val duplicates = mutableListOf<DuplicateGroup>()
            var hashedCount = 0
            val candidates = sizeGroups.entries
                .filter { it.value.size >= 2 }
                .sortedByDescending { it.key * it.value.size }

            for ((size, group) in candidates) {
                if (hashedCount > 400) break
                val hashMap = mutableMapOf<String, MutableList<FileNode>>()
                for (node in group) {
                    coroutineContext.ensureActive()
                    hashedCount++
                    val sha = computeQuickSha256(node.file) ?: continue
                    hashMap.getOrPut(sha) { mutableListOf() }.add(node)
                }
                hashMap.forEach { (hash, matched) ->
                    if (matched.size >= 2) {
                        duplicates.add(DuplicateGroup(hash, size, matched))
                    }
                }
            }

            val categories = categorySizeMap.entries
                .map { (cat, bytes) ->
                    CategoryBreakdown(cat, bytes, categoryCountMap[cat] ?: 0)
                }
                .sortedByDescending { it.totalBytes }

            StorageAnalysisReport(
                rootPath = rootPath,
                volumeInfo = volume,
                categories = categories,
                largeFiles = largeFiles.sortedByDescending { it.sizeBytes }.take(150),
                duplicateGroups = duplicates.sortedByDescending { it.sizeBytes * it.files.size }.take(100),
                emptyFolders = emptyFolders.take(200),
                totalScannedFiles = totalFiles,
                totalScannedBytes = totalBytes
            )
        }

    private fun computeQuickSha256(file: File): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(32 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().toHexString()
        }.getOrNull()
    }

    suspend fun listLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        resolved
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                runCatching {
                    val pkgName = info.activityInfo.packageName
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, 0)
                    }
                    val appInfo = pkgInfo.applicationInfo
                    val apkFile = appInfo?.sourceDir?.let { File(it) }
                    InstalledAppInfo(
                        label = info.loadLabel(pm).toString(),
                        packageName = pkgName,
                        versionName = pkgInfo.versionName ?: "1.0",
                        apkPath = apkFile?.absolutePath ?: "",
                        apkSizeBytes = apkFile?.length() ?: 0L,
                        isSystemApp = ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    suspend fun backupApk(app: InstalledAppInfo, destinationDirPath: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(app.apkPath)
                require(src.exists() && src.canRead()) { "APK source is not readable" }
                val destDir = File(destinationDirPath)
                if (!destDir.exists()) destDir.mkdirs()
                val safeLabel = app.label.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val dest = resolveUniqueTarget(destDir, "${safeLabel}_${app.versionName}.apk")
                copyRecursive(src, dest)
                dest
            }
        }

    fun openExternally(context: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
    }

    fun shareFiles(context: Context, files: List<File>): Result<Unit> = runCatching {
        val regularFiles = files.filter { it.exists() && it.isFile }
        require(regularFiles.isNotEmpty()) { "Select at least one regular file to share" }
        val uris = ArrayList(regularFiles.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = if (uris.size == 1) {
            val ext = regularFiles.first().extension.lowercase(Locale.ROOT)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share ${uris.size} file(s)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "--"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
