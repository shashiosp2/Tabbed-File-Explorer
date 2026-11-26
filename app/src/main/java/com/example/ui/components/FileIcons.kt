package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.engine.FileCategory
import com.example.engine.TabKind
import com.example.ui.theme.FileTypeColors

fun iconForCategory(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Rounded.Folder
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.VIDEO -> Icons.Outlined.VideoFile
    FileCategory.AUDIO -> Icons.Outlined.AudioFile
    FileCategory.DOCUMENT -> Icons.Outlined.Description
    FileCategory.CODE -> Icons.Outlined.Code
    FileCategory.ARCHIVE -> Icons.Outlined.FolderZip
    FileCategory.DATABASE -> Icons.Outlined.Storage
    FileCategory.APK -> Icons.Outlined.Android
    FileCategory.BINARY -> Icons.Outlined.InsertDriveFile
}

fun colorForCategory(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> FileTypeColors.Folder
    FileCategory.IMAGE -> FileTypeColors.Image
    FileCategory.VIDEO -> FileTypeColors.Video
    FileCategory.AUDIO -> FileTypeColors.Audio
    FileCategory.DOCUMENT -> FileTypeColors.Document
    FileCategory.CODE -> FileTypeColors.Code
    FileCategory.ARCHIVE -> FileTypeColors.Archive
    FileCategory.DATABASE -> FileTypeColors.Database
    FileCategory.APK -> FileTypeColors.Apk
    FileCategory.BINARY -> FileTypeColors.Binary
}

fun iconForTabKind(kind: TabKind): ImageVector = when (kind) {
    TabKind.DIRECTORY -> Icons.Outlined.Folder
    TabKind.TRASH -> Icons.Outlined.DeleteOutline
    TabKind.SEARCH -> Icons.Outlined.Search
    TabKind.STORAGE_ANALYZER -> Icons.Outlined.PieChart
    TabKind.HTTP_SERVER -> Icons.Outlined.WifiTethering
    TabKind.VAULT -> Icons.Outlined.Lock
    TabKind.APPS -> Icons.Rounded.Apps
}
