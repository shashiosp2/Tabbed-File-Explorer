package com.example.engine

import java.io.File

enum class TabKind {
    DIRECTORY,
    TRASH,
    SEARCH,
    STORAGE_ANALYZER,
    HTTP_SERVER,
    VAULT,
    APPS
}

enum class SortOption(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Modified"),
    EXTENSION("Type")
}

enum class ViewLayout(val label: String) {
    LIST("List"),
    COMPACT("Details"),
    GRID("Grid")
}

enum class FileCategory(val label: String) {
    FOLDER("Folder"),
    IMAGE("Image"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENT("Document"),
    CODE("Code & Text"),
    ARCHIVE("Archive"),
    DATABASE("Database"),
    APK("Android Package"),
    BINARY("Binary / Other")
}

enum class ClipboardOp {
    COPY, CUT
}

data class ClipboardState(
    val operation: ClipboardOp,
    val paths: List<String>
)

data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val category: FileCategory,
    val permissions: String,
    val isHidden: Boolean,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val childCount: Int? = null,
    val isSymlink: Boolean = false
) {
    val file: File get() = File(path)
}

data class ExplorerTab(
    val id: String,
    val kind: TabKind = TabKind.DIRECTORY,
    val title: String,
    val path: String,
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val entries: List<FileNode> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val filterQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class FileProperties(
    val node: FileNode,
    val canonicalPath: String,
    val mimeType: String,
    val recursiveSizeBytes: Long,
    val fileCount: Int,
    val folderCount: Int,
    val ownerPermissions: String,
    val symlinkTarget: String?
)

data class HashResult(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val sha512: String
)

data class SearchFilter(
    val query: String,
    val useRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val searchContent: Boolean = false,
    val extensionFilter: String = "",
    val minSizeKb: Long = 0L,
    val includeHidden: Boolean = false
)

enum class RenameCaseMode(val label: String) {
    NONE("Keep Case"),
    LOWER("lowercase"),
    UPPER("UPPERCASE"),
    TITLE("Title Case")
}

data class BatchRenameRule(
    val findText: String = "",
    val replaceText: String = "",
    val useRegex: Boolean = false,
    val prefix: String = "",
    val suffix: String = "",
    val addSequence: Boolean = false,
    val sequenceStart: Int = 1,
    val sequencePadding: Int = 3,
    val caseMode: RenameCaseMode = RenameCaseMode.NONE
)

data class TrashItem(
    val epochTimestamp: Long,
    val originalPath: String,
    val originalName: String,
    val trashedPath: String,
    val epochFolderPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val deletedAt: Long
)

data class StorageVolumeInfo(
    val label: String,
    val path: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
) {
    val usedFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class CategoryBreakdown(
    val category: FileCategory,
    val totalBytes: Long,
    val fileCount: Int
)

data class DuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val files: List<FileNode>
)

data class StorageAnalysisReport(
    val rootPath: String,
    val volumeInfo: StorageVolumeInfo,
    val categories: List<CategoryBreakdown> = emptyList(),
    val largeFiles: List<FileNode> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val emptyFolders: List<FileNode> = emptyList(),
    val totalScannedFiles: Int = 0,
    val totalScannedBytes: Long = 0L
)

data class ArchiveEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val lastModified: Long
)

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean
)

enum class ViewerMode {
    TEXT_EDITOR,
    HEX_VIEWER,
    JSON_TREE,
    CSV_TABLE,
    SQLITE_DB,
    MARKDOWN,
    IMAGE,
    ARCHIVE
}

data class ActiveViewerSession(
    val filePath: String,
    val fileName: String,
    val mode: ViewerMode
)
