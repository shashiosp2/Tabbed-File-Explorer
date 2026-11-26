package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BookmarkEntity
import com.example.data.ExplorerPreferences
import com.example.data.HistoryEntity
import com.example.data.WorkspaceEntity
import com.example.engine.FileSystemEngine
import com.example.engine.StorageVolumeInfo
import com.example.engine.TabKind
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode
import java.io.File

@Composable
fun ExplorerDrawerContent(
    preferences: ExplorerPreferences,
    storageVolumes: List<StorageVolumeInfo>,
    workspaces: List<WorkspaceEntity>,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    trashCount: Int,
    httpServerRunning: Boolean,
    onNavigatePath: (String) -> Unit,
    onOpenToolTab: (TabKind) -> Unit,
    onSwitchWorkspace: (WorkspaceEntity) -> Unit,
    onOpenCreateWorkspace: () -> Unit,
    onDeleteWorkspace: (WorkspaceEntity) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onToggleShowHidden: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(316.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            // Header Brand
            Text(
                text = "Tabbed File Explorer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Text(
                text = "Power User File Suite",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Theme Switcher (Light / Dark / System)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) {
                val modes = listOf(
                    Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
                    Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
                    Triple(ThemeMode.SYSTEM, "Auto", Icons.Outlined.BrightnessAuto)
                )
                modes.forEachIndexed { idx, (mode, label, icon) ->
                    SegmentedButton(
                        selected = preferences.themeMode == mode,
                        onClick = { onSetThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                        icon = {},
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            // Storage Volumes
            DrawerSectionHeader("STORAGE VOLUMES")
            storageVolumes.forEach { vol ->
                Surface(
                    onClick = { onNavigatePath(vol.path) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.SdStorage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = vol.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(vol.usedFraction * 100).toInt()}%",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { vol.usedFraction },
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${FileSystemEngine.formatSize(vol.usedBytes)} used / ${FileSystemEngine.formatSize(vol.freeBytes)} free",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quick Places
            DrawerSectionHeader("QUICK ACCESS")
            val quickPlaces = listOf(
                Triple("Internal (/sdcard)", "/sdcard", Icons.Outlined.Folder),
                Triple("Downloads", "/sdcard/Download", Icons.Outlined.Download),
                Triple("Documents", "/sdcard/Documents", Icons.Outlined.Folder),
                Triple("DCIM / Photos", "/sdcard/DCIM", Icons.Outlined.PhotoLibrary)
            )
            quickPlaces.forEach { (label, path, icon) ->
                NavigationDrawerItem(
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = false,
                    onClick = { onNavigatePath(path) },
                    icon = { Icon(icon, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
            NavigationDrawerItem(
                label = { Text("Trash (/sdcard/.Trash)") },
                badge = {
                    if (trashCount > 0) {
                        Text(
                            text = "$trashCount",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                selected = false,
                onClick = { onOpenToolTab(TabKind.TRASH) },
                icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_trash_item")
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Power Tools
            DrawerSectionHeader("POWER TOOLS")
            NavigationDrawerItem(
                label = { Text("Storage Analyzer & Duplicates") },
                selected = false,
                onClick = { onOpenToolTab(TabKind.STORAGE_ANALYZER) },
                icon = { Icon(Icons.Outlined.PieChart, null) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_analyzer_item")
            )
            NavigationDrawerItem(
                label = { Text("Wireless LAN HTTP Server") },
                badge = {
                    if (httpServerRunning) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                selected = false,
                onClick = { onOpenToolTab(TabKind.HTTP_SERVER) },
                icon = { Icon(Icons.Outlined.WifiTethering, null) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_http_server_item")
            )
            NavigationDrawerItem(
                label = { Text("Encrypted Vault (AES-256)") },
                selected = false,
                onClick = { onOpenToolTab(TabKind.VAULT) },
                icon = { Icon(Icons.Outlined.Lock, null) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_vault_item")
            )
            NavigationDrawerItem(
                label = { Text("App Inspector & APK Backup") },
                selected = false,
                onClick = { onOpenToolTab(TabKind.APPS) },
                icon = { Icon(Icons.Rounded.Apps, null) },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .testTag("drawer_apps_item")
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Workspaces
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "WORKSPACES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onOpenCreateWorkspace,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.Add, "New Workspace", modifier = Modifier.size(18.dp))
                }
            }
            workspaces.forEach { ws ->
                val active = ws.id == preferences.activeWorkspaceId
                NavigationDrawerItem(
                    label = { Text(ws.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = active,
                    onClick = { onSwitchWorkspace(ws) },
                    icon = { Icon(Icons.Outlined.Layers, null) },
                    badge = {
                        if (workspaces.size > 1) {
                            IconButton(
                                onClick = { onDeleteWorkspace(ws) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Rounded.Close, "Delete workspace", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            if (bookmarks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                DrawerSectionHeader("BOOKMARKS")
                bookmarks.forEach { bm ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(bm.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    bm.path,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        selected = false,
                        onClick = { onNavigatePath(bm.path) },
                        icon = { Icon(Icons.Outlined.Bookmark, null) },
                        badge = {
                            IconButton(
                                onClick = { onRemoveBookmark(bm.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Rounded.Close, "Remove bookmark", modifier = Modifier.size(14.dp))
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "RECENT HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
                history.take(8).forEach { h ->
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text(h.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    h.path,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            if (File(h.path).exists()) onNavigatePath(h.path)
                        },
                        icon = { Icon(Icons.Outlined.History, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Power Toggles
            DrawerSectionHeader("PREFERENCES")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Show Hidden (.dotfiles)", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = preferences.showHiddenFiles,
                    onCheckedChange = onToggleShowHidden
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Material You Dynamic Color", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = preferences.dynamicColor,
                    onCheckedChange = onToggleDynamicColor
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
