package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.engine.ExplorerTab
import com.example.engine.FileCategory
import com.example.engine.FileNode
import com.example.engine.FileSystemEngine
import com.example.engine.TabKind
import com.example.engine.ViewLayout
import com.example.engine.ViewerMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

@Composable
fun FilePaneView(
    tab: ExplorerTab,
    viewLayout: ViewLayout,
    isBookmarked: Boolean,
    hasAllFilesAccess: Boolean,
    isDualPane: Boolean,
    isFocusedPane: Boolean,
    paneLabel: String,
    onFocusPane: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenGoToPath: () -> Unit,
    onCopyPath: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenFile: (FileNode, ViewerMode?) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onInspectProperties: (FileNode) -> Unit,
    onRenameSingle: (FileNode) -> Unit,
    onCopySingle: (FileNode) -> Unit,
    onCutSingle: (FileNode) -> Unit,
    onDeleteSingle: (FileNode) -> Unit,
    onExtractArchive: (FileNode) -> Unit,
    onShareSingle: (FileNode) -> Unit,
    onOpenExternal: (FileNode) -> Unit,
    onLockInVault: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedEntries = remember(tab.entries, tab.filterQuery) {
        if (tab.filterQuery.isBlank()) tab.entries
        else tab.entries.filter { it.name.contains(tab.filterQuery, ignoreCase = true) }
    }

    var showInlineFilter by remember(tab.id) { mutableStateOf(false) }

    val borderStroke = if (isDualPane) {
        BorderStroke(
            width = if (isFocusedPane) 1.5.dp else 0.75.dp,
            color = if (isFocusedPane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    } else null

    Surface(
        border = borderStroke,
        shape = if (isDualPane) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .combinedClickableCompat(onClick = onFocusPane)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isDualPane) {
                Surface(
                    color = if (isFocusedPane) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PANE $paneLabel • ${tab.title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFocusedPane) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${displayedEntries.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (tab.kind == TabKind.DIRECTORY) {
                BreadcrumbPathBar(
                    tab = tab,
                    isBookmarked = isBookmarked,
                    onNavigateToPath = {
                        onFocusPane()
                        onNavigateToPath(it)
                    },
                    onNavigateUp = {
                        onFocusPane()
                        onNavigateUp()
                    },
                    onNavigateBack = {
                        onFocusPane()
                        onNavigateBack()
                    },
                    onNavigateForward = {
                        onFocusPane()
                        onNavigateForward()
                    },
                    onOpenGoToPath = {
                        onFocusPane()
                        onOpenGoToPath()
                    },
                    onCopyPath = { onCopyPath(tab.path) },
                    onToggleBookmark = onToggleBookmark
                )
            } else if (tab.kind == TabKind.SEARCH) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${tab.title} in ${tab.path} (${displayedEntries.size} matches)",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Status / Quick Filter Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val folderCount = displayedEntries.count { it.isDirectory }
                val fileCount = displayedEntries.size - folderCount
                Text(
                    text = "$folderCount folders · $fileCount files",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showInlineFilter = !showInlineFilter },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Quick filter",
                        tint = if (showInlineFilter || tab.filterQuery.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh directory",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showInlineFilter) {
                OutlinedTextField(
                    value = tab.filterQuery,
                    onValueChange = onFilterChange,
                    placeholder = { Text("Quick filter current view...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // All Files Access Permission Banner for /sdcard & /sdcard/.Trash
            if (!hasAllFilesAccess && tab.kind == TabKind.DIRECTORY) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Grant All Files Access",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Required for browsing /sdcard and saving deleted files to /sdcard/.Trash/<epoch>.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onRequestAllFilesAccess,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("grant_storage_button")
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            when {
                tab.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                tab.errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = tab.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!hasAllFilesAccess) {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onRequestAllFilesAccess) {
                                    Text("Grant All Files Access")
                                }
                            }
                        }
                    }
                }
                displayedEntries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (tab.filterQuery.isNotEmpty()) "No items match \"${tab.filterQuery}\""
                                else if (tab.kind == TabKind.SEARCH) "No search results found"
                                else "Folder is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Use + to create a file or folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                viewLayout == ViewLayout.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedEntries, key = { it.path }) { node ->
                            val isSelected = node.path in tab.selectedPaths
                            FileGridTile(
                                node = node,
                                isSelected = isSelected,
                                selectionActive = tab.selectedPaths.isNotEmpty(),
                                onClick = {
                                    onFocusPane()
                                    if (tab.selectedPaths.isNotEmpty()) {
                                        onToggleSelect(node.path)
                                    } else if (node.isDirectory) {
                                        onNavigateToPath(node.path)
                                    } else {
                                        onOpenFile(node, null)
                                    }
                                },
                                onLongClick = {
                                    onFocusPane()
                                    onToggleSelect(node.path)
                                }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedEntries, key = { it.path }) { node ->
                            val isSelected = node.path in tab.selectedPaths
                            FileListRow(
                                node = node,
                                compactDetails = viewLayout == ViewLayout.COMPACT,
                                isSelected = isSelected,
                                showParentPath = tab.kind == TabKind.SEARCH,
                                onClick = {
                                    onFocusPane()
                                    if (tab.selectedPaths.isNotEmpty()) {
                                        onToggleSelect(node.path)
                                    } else if (node.isDirectory) {
                                        onNavigateToPath(node.path)
                                    } else {
                                        onOpenFile(node, null)
                                    }
                                },
                                onLongClick = {
                                    onFocusPane()
                                    onToggleSelect(node.path)
                                },
                                onToggleSelect = {
                                    onFocusPane()
                                    onToggleSelect(node.path)
                                },
                                onOpenInNewTab = { onOpenInNewTab(node.path) },
                                onOpenAsText = { onOpenFile(node, ViewerMode.TEXT_EDITOR) },
                                onOpenAsHex = { onOpenFile(node, ViewerMode.HEX_VIEWER) },
                                onInspectProperties = { onInspectProperties(node) },
                                onRename = { onRenameSingle(node) },
                                onCopy = { onCopySingle(node) },
                                onCut = { onCutSingle(node) },
                                onDelete = { onDeleteSingle(node) },
                                onExtractArchive = { onExtractArchive(node) },
                                onShare = { onShareSingle(node) },
                                onOpenExternal = { onOpenExternal(node) },
                                onLockInVault = { onLockInVault(node) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = null,
        indication = null,
        onClick = onClick
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(
    node: FileNode,
    compactDetails: Boolean,
    isSelected: Boolean,
    showParentPath: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenAsText: () -> Unit,
    onOpenAsHex: () -> Unit,
    onInspectProperties: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onExtractArchive: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onLockInVault: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = 12.dp,
                vertical = if (compactDetails) 6.dp else 10.dp
            )
    ) {
        // Leading thumbnail / category badge (tappable to toggle selection quickly!)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(if (compactDetails) 36.dp else 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else colorForCategory(node.category).copy(alpha = 0.14f)
                )
                .combinedClickable(onClick = onToggleSelect, onLongClick = onToggleSelect)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else if (node.category == FileCategory.IMAGE && node.isReadable) {
                AsyncImage(
                    model = File(node.path),
                    contentDescription = node.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = iconForCategory(node.category),
                    contentDescription = node.category.label,
                    tint = colorForCategory(node.category),
                    modifier = Modifier.size(if (compactDetails) 20.dp else 24.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = node.name,
                    style = if (compactDetails) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (node.isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (node.isSymlink) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LINK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (showParentPath) {
                Text(
                    text = File(node.path).parent ?: "",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (compactDetails) {
                    Text(
                        text = node.permissions,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = if (node.isDirectory) {
                        val cnt = node.childCount
                        if (cnt != null) "$cnt items" else "Folder"
                    } else {
                        FileSystemEngine.formatSize(node.sizeBytes)
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FileSystemEngine.formatDate(node.lastModified),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "File options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (node.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Open in New Tab") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        onClick = { menuExpanded = false; onOpenInNewTab() }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Edit as Text / Code") },
                        leadingIcon = { Icon(Icons.Outlined.Code, null) },
                        onClick = { menuExpanded = false; onOpenAsText() }
                    )
                    DropdownMenuItem(
                        text = { Text("Inspect in Hex Viewer") },
                        leadingIcon = { Icon(Icons.Outlined.Memory, null) },
                        onClick = { menuExpanded = false; onOpenAsHex() }
                    )
                    if (node.extension in setOf("zip", "jar", "apk", "cbz")) {
                        DropdownMenuItem(
                            text = { Text("Extract Archive Here") },
                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                            onClick = { menuExpanded = false; onExtractArchive() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Open with External App") },
                        leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                        onClick = { menuExpanded = false; onOpenExternal() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Encrypt to Vault") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        onClick = { menuExpanded = false; onLockInVault() }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = { menuExpanded = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text("Cut") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCut, null) },
                    onClick = { menuExpanded = false; onCut() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Properties & Hashes") },
                    leadingIcon = { Icon(Icons.Outlined.Info, null) },
                    onClick = { menuExpanded = false; onInspectProperties() }
                )
                DropdownMenuItem(
                    text = { Text("Move to .Trash", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridTile(
    node: FileNode,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorForCategory(node.category).copy(alpha = 0.14f))
            ) {
                if (node.category == FileCategory.IMAGE && node.isReadable) {
                    AsyncImage(
                        model = File(node.path),
                        contentDescription = node.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = iconForCategory(node.category),
                        contentDescription = node.category.label,
                        tint = colorForCategory(node.category),
                        modifier = Modifier.size(30.dp)
                    )
                }
                if (isSelected || selectionActive) {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (node.isDirectory) "${node.childCount ?: 0} items"
                else FileSystemEngine.formatSize(node.sizeBytes),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
