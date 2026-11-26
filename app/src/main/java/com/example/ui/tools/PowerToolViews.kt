package com.example.ui.tools

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.VaultEntryEntity
import com.example.engine.FileSystemEngine
import com.example.engine.HttpServerState
import com.example.engine.InstalledAppInfo
import com.example.engine.StorageAnalysisReport
import com.example.engine.TrashItem
import com.example.ui.components.colorForCategory
import com.example.ui.components.iconForCategory
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File

@Composable
fun TrashTabView(
    items: List<TrashItem>,
    activeTrashPath: String,
    isUsingSdcardTrash: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRestore: (TrashItem) -> Unit,
    onDeleteForever: (TrashItem) -> Unit,
    onEmptyTrash: () -> Unit,
    onBrowseTrashFolder: (String) -> Unit
) {
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Epoch-Timestamped Trash",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$activeTrashPath/<epoch_ms>/",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, "Refresh trash")
                    }
                    if (items.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showEmptyConfirm = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            Text("Empty", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalSize = items.sumOf { it.sizeBytes }
                    Text(
                        text = "${items.size} items · ${FileSystemEngine.formatSize(totalSize)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onBrowseTrashFolder(activeTrashPath) }) {
                        Text("Inspect Raw Folder", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!isUsingSdcardTrash) {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Outlined.Shield, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Grant All Files Access to store deleted items directly in /sdcard/.Trash",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRequestAllFilesAccess) { Text("Grant") }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Trash is empty", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Deleted files are preserved in $activeTrashPath/<epoch>/",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.epochTimestamp }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.originalName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Epoch Folder: ${item.epochTimestamp}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = FileSystemEngine.formatSize(item.sizeBytes),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "From: ${item.originalPath}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Deleted: ${FileSystemEngine.formatDate(item.deletedAt)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRestore(item) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.RestoreFromTrash, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Restore", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { onDeleteForever(item) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.DeleteForever, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shred", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Empty Trash Permanently?") },
            text = { Text("Permanently destroy all ${items.size} epoch-timestamped item(s) inside $activeTrashPath?") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyConfirm = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Empty Trash") }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StorageAnalyzerTabView(
    report: StorageAnalysisReport?,
    isAnalyzing: Boolean,
    onRunAnalysis: (Long) -> Unit,
    onJumpToFolder: (String) -> Unit,
    onTrashPaths: (List<String>) -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var largeThresholdMb by remember { mutableStateOf(5L) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Storage Analyzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = report?.let { "${it.totalScannedFiles} files · ${FileSystemEngine.formatSize(it.totalScannedBytes)} scanned" }
                                ?: "Analyze disk usage, large files & SHA-256 duplicates",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onRunAnalysis(largeThresholdMb) },
                        enabled = !isAnalyzing
                    ) {
                        Text(if (isAnalyzing) "Scanning..." else "Scan")
                    }
                }

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val tabs = listOf("Overview", "Large", "Duplicates", "Empty")
                    tabs.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = selectedSection == idx,
                            onClick = { selectedSection = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, tabs.size)
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        HorizontalDivider()

        when {
            isAnalyzing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text("Scanning directory tree & hashing duplicate candidates...", style = MaterialTheme.typography.bodySmall)
                }
            }
            report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { onRunAnalysis(largeThresholdMb) }) { Text("Start Storage Scan") }
            }
            selectedSection == 0 -> {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(report.volumeInfo.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { report.volumeInfo.usedFraction },
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${FileSystemEngine.formatSize(report.volumeInfo.usedBytes)} used of ${FileSystemEngine.formatSize(report.volumeInfo.totalBytes)} (${FileSystemEngine.formatSize(report.volumeInfo.freeBytes)} free)",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    items(report.categories) { cat ->
                        val fraction = if (report.totalScannedBytes > 0) {
                            (cat.totalBytes.toFloat() / report.totalScannedBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = iconForCategory(cat.category),
                                        contentDescription = null,
                                        tint = colorForCategory(cat.category),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat.category.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${cat.fileCount} files · ${FileSystemEngine.formatSize(cat.totalBytes)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    color = colorForCategory(cat.category),
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                )
                            }
                        }
                    }
                }
            }
            selectedSection == 1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        listOf(1L, 5L, 25L, 100L).forEach { mb ->
                            FilterChip(
                                selected = largeThresholdMb == mb,
                                onClick = {
                                    largeThresholdMb = mb
                                    onRunAnalysis(mb)
                                },
                                label = { Text(">${mb}MB") }
                            )
                        }
                    }
                    if (report.largeFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No files larger than ${largeThresholdMb} MB found")
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(report.largeFiles, key = { it.path }) { node ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(node.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(node.path, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(FileSystemEngine.formatSize(node.sizeBytes), fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onJumpToFolder(File(node.path).parent ?: report.rootPath) }) {
                                            Icon(Icons.Outlined.FolderOpen, "Jump to folder")
                                        }
                                        IconButton(onClick = { onTrashPaths(listOf(node.path)) }) {
                                            Icon(Icons.Outlined.DeleteOutline, "Move to Trash", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            selectedSection == 2 -> {
                if (report.duplicateGroups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No exact SHA-256 duplicate files found")
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(report.duplicateGroups, key = { it.sha256 }) { grp ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${grp.files.size} identical files · ${FileSystemEngine.formatSize(grp.sizeBytes)} each",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            onTrashPaths(grp.files.drop(1).map { it.path })
                                        }) {
                                            Text("Trash Extras", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(
                                        text = "SHA-256: ${grp.sha256.take(18)}...",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    grp.files.forEach { f ->
                                        Text(
                                            text = "• ${f.path}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (report.emptyFolders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No empty directories detected")
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("${report.emptyFolders.size} empty folders", modifier = Modifier.weight(1f))
                            Button(
                                onClick = { onTrashPaths(report.emptyFolders.map { it.path }) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Move All to .Trash") }
                        }
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(report.emptyFolders, key = { it.path }) { folder ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Text(
                                            text = folder.path,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(onClick = { onTrashPaths(listOf(folder.path)) }) {
                                            Icon(Icons.Outlined.DeleteOutline, "Trash folder", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanHttpServerTabView(
    state: HttpServerState,
    defaultRootPath: String,
    onStart: (rootPath: String, port: Int, readOnly: Boolean) -> Unit,
    onStop: () -> Unit,
    onCopyUrl: (String) -> Unit
) {
    var rootPath by remember(defaultRootPath) { mutableStateOf(if (state.rootPath.isNotEmpty()) state.rootPath else defaultRootPath) }
    var portText by remember { mutableStateOf(state.port.toString()) }
    var readOnly by remember { mutableStateOf(state.readOnly) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isRunning) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WifiTethering,
                            contentDescription = null,
                            tint = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isRunning) "LAN HTTP Server Active" else "Wireless PC File Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.isRunning) "Open this URL in any browser on your Wi-Fi network"
                                else "Browse, download & upload files wirelessly over HTTP",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (state.isRunning && state.url.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = state.url,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onCopyUrl(state.url) }) {
                                    Icon(Icons.Outlined.ContentCopy, "Copy URL")
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            if (state.isRunning) onStop()
                            else onStart(rootPath.trim(), portText.toIntOrNull() ?: 8080, readOnly)
                        },
                        colors = if (state.isRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else ButtonDefaults.buttonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_http_server_button")
                    ) {
                        Text(if (state.isRunning) "Stop Server" else "Start Wireless Server")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Server Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = rootPath,
                        onValueChange = { rootPath = it },
                        label = { Text("Served Root Directory") },
                        enabled = !state.isRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port (1024 - 65535)") },
                        enabled = !state.isRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Read-Only Mode (disable browser uploads)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = readOnly, onCheckedChange = { readOnly = it }, enabled = !state.isRunning)
                    }
                }
            }
        }

        item {
            Text("Live Access Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }

        if (state.logs.isEmpty()) {
            item {
                Text(
                    "No requests yet.",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.logs) { logLine ->
                Text(
                    text = logLine,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EncryptedVaultTabView(
    hasPinConfigured: Boolean,
    isUnlocked: Boolean,
    entries: List<VaultEntryEntity>,
    onUnlockOrCreate: (String) -> Boolean,
    onLockVault: () -> Unit,
    onRestoreEntry: (VaultEntryEntity) -> Unit,
    onDeleteEntry: (VaultEntryEntity) -> Unit
) {
    if (!isUnlocked) {
        var pinInput by remember { mutableStateOf("") }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = if (hasPinConfigured) "Unlock Hidden Cabinet" else "Create AES-256-GCM Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasPinConfigured) "Enter your Vault PIN or passphrase to decrypt files."
                        else "Set a 4+ character PIN/passphrase. Files locked here are encrypted with AES-256-GCM + PBKDF2 and hidden from gallery apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Vault PIN / Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (onUnlockOrCreate(pinInput)) pinInput = ""
                        },
                        enabled = pinInput.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasPinConfigured) "Unlock Vault" else "Initialize & Unlock")
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                Icon(Icons.Outlined.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Encrypted Vault (Unlocked)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${entries.size} encrypted container(s) · AES-256-GCM",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onLockVault) { Text("Lock") }
            }
        }
        HorizontalDivider()

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No encrypted files yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open any file's ⋮ menu in a directory tab and choose \"Encrypt to Vault\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entry.originalName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${FileSystemEngine.formatSize(entry.sizeBytes)} · Encrypted ${FileSystemEngine.formatDate(entry.encryptedAt)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Original: ${entry.originalPath}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRestoreEntry(entry) }, modifier = Modifier.weight(1f)) {
                                    Text("Decrypt & Restore")
                                }
                                OutlinedButton(
                                    onClick = { onDeleteEntry(entry) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Destroy")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppInspectorTabView(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBackupApk: (InstalledAppInfo) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val filtered = remember(apps, search, showSystem) {
        apps.filter { app ->
            (showSystem || !app.isSystemApp) &&
                (search.isBlank() ||
                    app.label.contains(search, ignoreCase = true) ||
                    app.packageName.contains(search, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "App Inspector (${filtered.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = showSystem,
                        onClick = { showSystem = !showSystem },
                        label = { Text("System Apps") }
                    )
                    IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh apps") }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter by app name or package ID...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        HorizontalDivider()

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = app.packageName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "v${app.versionName} · ${FileSystemEngine.formatSize(app.apkSizeBytes)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onBackupApk(app) }) {
                                Icon(Icons.Outlined.Download, "Backup APK")
                            }
                            IconButton(onClick = { onOpenAppInfo(app.packageName) }) {
                                Icon(Icons.Outlined.Info, "App Details")
                            }
                            IconButton(onClick = { onLaunchApp(app.packageName) }) {
                                Icon(Icons.Outlined.OpenInNew, "Launch App")
                            }
                        }
                    }
                }
            }
        }
    }
}
