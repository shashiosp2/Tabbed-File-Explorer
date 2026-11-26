package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.BatchRenameRule
import com.example.engine.FileNode
import com.example.engine.FileProperties
import com.example.engine.FileSystemEngine
import com.example.engine.HashResult
import com.example.engine.RenameCaseMode
import com.example.engine.SearchFilter
import com.example.engine.SortOption
import com.example.engine.ViewLayout
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.zip.Deflater

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateNewItemDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit
) {
    var isFolder by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFolder) "New Folder" else "New File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isFolder,
                        onClick = { isFolder = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Folder") }
                    SegmentedButton(
                        selected = !isFolder,
                        onClick = { isFolder = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("File") }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isFolder) "Folder name (supports a/b/c)" else "File name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_item_input")
                )

                if (!isFolder) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("notes.md", "data.json", "sheet.csv", "script.sh", "readme.txt").forEach { preset ->
                            FilterChip(
                                selected = name == preset,
                                onClick = { name = preset },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (isFolder) onCreateFolder(name) else onCreateFile(name)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_button")
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameSingleDialog(
    node: FileNode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(node.path) { mutableStateOf(node.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank() && newName != node.name) {
                        onConfirm(newName)
                    }
                    onDismiss()
                },
                enabled = newName.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchRenameDialog(
    nodes: List<FileNode>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<FileNode, String>>) -> Unit
) {
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var addSequence by remember { mutableStateOf(false) }
    var caseMode by remember { mutableStateOf(RenameCaseMode.NONE) }

    val rule = BatchRenameRule(
        findText = findText,
        replaceText = replaceText,
        useRegex = useRegex,
        prefix = prefix,
        suffix = suffix,
        addSequence = addSequence,
        caseMode = caseMode
    )
    val preview = remember(nodes, rule) { FileSystemEngine.previewBatchRename(nodes, rule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Rename (${nodes.size} items)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Prefix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text("Suffix") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                        Text("Regex", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addSequence, onCheckedChange = { addSequence = it })
                        Text("Number (_001)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RenameCaseMode.entries.forEach { mode ->
                        FilterChip(
                            selected = caseMode == mode,
                            onClick = { caseMode = mode },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()
                Text("Live Preview", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(preview.take(20)) { (node, target) ->
                            Text(
                                text = "${node.name}  ->  $target",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (node.name != target) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(preview)
                onDismiss()
            }) { Text("Apply Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    sampleName: String,
    activeTrashPath: String,
    onDismiss: () -> Unit,
    onConfirm: (permanent: Boolean) -> Unit
) {
    var permanent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (permanent) "Permanently Delete?" else "Move to Trash?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (count == 1) "\"$sampleName\"" else "$count selected items",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!permanent) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Saved in epoch-timestamped folder:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$activeTrashPath/<epoch_ms>/",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "This bypasses .Trash and cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = permanent, onCheckedChange = { permanent = it })
                    Text("Bypass Trash & delete permanently", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(permanent)
                    onDismiss()
                },
                colors = if (permanent) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(if (permanent) "Delete Forever" else "Move to .Trash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CompressZipDialog(
    itemCount: Int,
    defaultArchiveName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, level: Int, stripMac: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(defaultArchiveName) }
    var level by remember { mutableIntStateOf(Deflater.DEFAULT_COMPRESSION) }
    var stripMac by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FolderZip, null) },
        title = { Text("Compress $itemCount item(s) to ZIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Archive name (.zip)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Compression Level", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val levels = listOf(
                        Deflater.NO_COMPRESSION to "Store",
                        Deflater.BEST_SPEED to "Fast",
                        Deflater.DEFAULT_COMPRESSION to "Normal",
                        Deflater.BEST_COMPRESSION to "Max"
                    )
                    levels.forEachIndexed { idx, (lvl, lbl) ->
                        SegmentedButton(
                            selected = level == lvl,
                            onClick = { level = lvl },
                            shape = SegmentedButtonDefaults.itemShape(idx, levels.size)
                        ) { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Strip macOS metadata (.DS_Store)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = stripMac, onCheckedChange = { stripMac = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), level, stripMac)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoToPathDialog(
    currentPath: String,
    activeTrashPath: String,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go To Path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Absolute directory path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_path_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "/sdcard",
                        "/sdcard/Download",
                        "/sdcard/Documents",
                        activeTrashPath,
                        "/"
                    ).distinct().forEach { shortcut ->
                        FilterChip(
                            selected = path == shortcut,
                            onClick = { path = shortcut },
                            label = {
                                Text(
                                    shortcut,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (path.isNotBlank()) {
                        onNavigate(path.trim())
                        onDismiss()
                    }
                }
            ) { Text("Go") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PowerSearchDialog(
    rootPath: String,
    onDismiss: () -> Unit,
    onSearch: (SearchFilter) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("") }
    var minSizeKb by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var searchContent by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var includeHidden by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Search, null) },
        title = { Text("Power Recursive Search") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Scope: $rootPath",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filename or text pattern") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_search_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = extension,
                        onValueChange = { extension = it },
                        label = { Text("Ext (e.g. kt, pdf)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minSizeKb,
                        onValueChange = { minSizeKb = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Min KB") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                    Text("Regular Expression (Regex)", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = searchContent, onCheckedChange = { searchContent = it })
                    Text("Content Grep (search inside text files)", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = caseSensitive, onCheckedChange = { caseSensitive = it })
                    Text("Case Sensitive", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeHidden, onCheckedChange = { includeHidden = it })
                    Text("Include Hidden (.dotfiles)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSearch(
                        SearchFilter(
                            query = query.trim(),
                            useRegex = useRegex,
                            caseSensitive = caseSensitive,
                            searchContent = searchContent,
                            extensionFilter = extension.trim(),
                            minSizeKb = minSizeKb.toLongOrNull() ?: 0L,
                            includeHidden = includeHidden
                        )
                    )
                    onDismiss()
                },
                enabled = query.isNotBlank() || extension.isNotBlank() || (minSizeKb.toLongOrNull() ?: 0L) > 0L
            ) { Text("Search in New Tab") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CreateWorkspaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Current Tabs as Workspace") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Workspace name (e.g. Dev, Media)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortAndViewBottomSheet(
    currentLayout: ViewLayout,
    currentSort: SortOption,
    ascending: Boolean,
    foldersFirst: Boolean,
    onSelectLayout: (ViewLayout) -> Unit,
    onSelectSort: (SortOption, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Layout & Sorting", style = MaterialTheme.typography.titleLarge)

            Text("View Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ViewLayout.entries.forEachIndexed { idx, layout ->
                    SegmentedButton(
                        selected = currentLayout == layout,
                        onClick = { onSelectLayout(layout) },
                        shape = SegmentedButtonDefaults.itemShape(idx, ViewLayout.entries.size)
                    ) { Text(layout.label) }
                }
            }

            Text("Sort By", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SortOption.entries.forEachIndexed { idx, opt ->
                    SegmentedButton(
                        selected = currentSort == opt,
                        onClick = { onSelectSort(opt, ascending, foldersFirst) },
                        shape = SegmentedButtonDefaults.itemShape(idx, SortOption.entries.size)
                    ) { Text(opt.label, style = MaterialTheme.typography.labelSmall) }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ascending Order", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = ascending,
                    onCheckedChange = { onSelectSort(currentSort, it, foldersFirst) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Always Sort Folders First", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = foldersFirst,
                    onCheckedChange = { onSelectSort(currentSort, ascending, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesAndHashSheet(
    properties: FileProperties,
    hashes: HashResult?,
    isComputingHashes: Boolean,
    onComputeHashes: () -> Unit,
    onCopyText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var compareInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = properties.node.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            PropertyRow("Path", properties.node.path, onCopy = { onCopyText(properties.node.path) })
            if (properties.symlinkTarget != null) {
                PropertyRow("Symlink Target", properties.symlinkTarget, onCopy = { onCopyText(properties.symlinkTarget) })
            }
            PropertyRow("MIME Type", properties.mimeType)
            PropertyRow(
                "Size",
                "${FileSystemEngine.formatSize(properties.recursiveSizeBytes)} (${properties.recursiveSizeBytes} bytes)"
            )
            if (properties.node.isDirectory) {
                PropertyRow("Contents", "${properties.folderCount} folders, ${properties.fileCount} files")
            }
            PropertyRow("Permissions", properties.ownerPermissions)
            PropertyRow(
                "Modified",
                "${FileSystemEngine.formatDate(properties.node.lastModified)} (epoch: ${properties.node.lastModified})"
            )

            if (!properties.node.isDirectory) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cryptographic Checksums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (hashes == null && !isComputingHashes) {
                        OutlinedButton(onClick = onComputeHashes) {
                            Icon(Icons.Outlined.Fingerprint, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Calculate")
                        }
                    }
                }

                if (isComputingHashes) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Computing MD5 / SHA-1 / SHA-256 / SHA-512...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (hashes != null) {
                    PropertyRow("MD5", hashes.md5, onCopy = { onCopyText(hashes.md5) })
                    PropertyRow("SHA-1", hashes.sha1, onCopy = { onCopyText(hashes.sha1) })
                    PropertyRow("SHA-256", hashes.sha256, onCopy = { onCopyText(hashes.sha256) })
                    PropertyRow("SHA-512", hashes.sha512, onCopy = { onCopyText(hashes.sha512) })

                    OutlinedTextField(
                        value = compareInput,
                        onValueChange = { compareInput = it.trim() },
                        label = { Text("Verify / Compare Hash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (compareInput.isNotEmpty()) {
                        val cleaned = compareInput.lowercase()
                        val matchedAlgo = when (cleaned) {
                            hashes.md5 -> "MD5"
                            hashes.sha1 -> "SHA-1"
                            hashes.sha256 -> "SHA-256"
                            hashes.sha512 -> "SHA-512"
                            else -> null
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (matchedAlgo != null) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (matchedAlgo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (matchedAlgo != null) "Exact $matchedAlgo checksum match!" else "Hash does not match any digest",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (matchedAlgo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copy $label", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
