package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.engine.ClipboardOp
import com.example.engine.ExplorerTab
import com.example.engine.FileNode
import com.example.engine.FileSystemEngine
import com.example.engine.TabKind
import com.example.ui.components.BatchRenameDialog
import com.example.ui.components.BrowserTabStrip
import com.example.ui.components.CompressZipDialog
import com.example.ui.components.CreateNewItemDialog
import com.example.ui.components.CreateWorkspaceDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.ExplorerDrawerContent
import com.example.ui.components.FilePaneView
import com.example.ui.components.GoToPathDialog
import com.example.ui.components.PowerSearchDialog
import com.example.ui.components.PropertiesAndHashSheet
import com.example.ui.components.RenameSingleDialog
import com.example.ui.components.SortAndViewBottomSheet
import com.example.ui.theme.ThemeMode
import com.example.ui.tools.AppInspectorTabView
import com.example.ui.tools.EncryptedVaultTabView
import com.example.ui.tools.LanHttpServerTabView
import com.example.ui.tools.StorageAnalyzerTabView
import com.example.ui.tools.TrashTabView
import com.example.ui.viewers.BuiltInViewerOverlay
import com.example.viewmodel.ExplorerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(viewModel: ExplorerViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.vaultEntries.collectAsStateWithLifecycle()
    val httpServerState by viewModel.httpServerState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog / Sheet states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showGoToPathDialog by remember { mutableStateOf(false) }
    var showPowerSearchDialog by remember { mutableStateOf(false) }
    var showCreateWorkspaceDialog by remember { mutableStateOf(false) }
    var renamingNode by remember { mutableStateOf<Pair<String, FileNode>?>(null) }
    var batchRenamingTabId by remember { mutableStateOf<String?>(null) }
    var deletingRequest by remember { mutableStateOf<Pair<String?, List<FileNode>>?>(null) }
    var compressingTabId by remember { mutableStateOf<String?>(null) }

    // Refresh storage permission when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStoragePermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val focusedTabId = viewModel.getFocusedTabId()
    val focusedTab = uiState.tabs.firstOrNull { it.id == focusedTabId } ?: uiState.tabs.firstOrNull()
    val primaryTab = uiState.tabs.firstOrNull { it.id == uiState.activeTabId } ?: uiState.tabs.firstOrNull()
    val secondaryTab = uiState.tabs.firstOrNull { it.id == uiState.secondaryTabId } ?: primaryTab

    val isDarkEffective = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    fun copyToSystemClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("path", text))
        viewModel.emitMessage("Copied to clipboard")
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    // Back navigation priority: Close Drawer -> Clear Selection -> Back/Up in Focused Tab
    BackHandler(enabled = drawerState.isOpen || (focusedTab != null && (focusedTab.selectedPaths.isNotEmpty() || focusedTab.backStack.isNotEmpty()))) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            focusedTab != null && focusedTab.selectedPaths.isNotEmpty() -> viewModel.clearSelection(focusedTab.id)
            focusedTab != null && focusedTab.backStack.isNotEmpty() -> viewModel.navigateBack(focusedTab.id)
        }
    }

    // Full-screen Built-in Viewer Overlay
    val activeViewer = uiState.activeViewer
    if (activeViewer != null) {
        BuiltInViewerOverlay(
            session = activeViewer,
            onSwitchMode = { viewModel.switchViewerMode(it) },
            onClose = { viewModel.closeViewer() },
            onOpenExternal = { viewModel.openExternally(it) },
            onExtractArchive = { zip, dest -> viewModel.extractArchive(zip, dest) },
            onEmitMessage = { viewModel.emitMessage(it) }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ExplorerDrawerContent(
                preferences = prefs,
                storageVolumes = uiState.storageVolumes,
                workspaces = workspaces,
                bookmarks = bookmarks,
                history = history,
                trashCount = uiState.trashItems.size,
                httpServerRunning = httpServerState.isRunning,
                onNavigatePath = { path ->
                    scope.launch { drawerState.close() }
                    val targetTab = focusedTab
                    if (targetTab != null && targetTab.kind == TabKind.DIRECTORY) {
                        viewModel.navigateInTab(targetTab.id, path)
                    } else {
                        viewModel.openNewTab(path = path, kind = TabKind.DIRECTORY)
                    }
                },
                onOpenToolTab = { kind ->
                    scope.launch { drawerState.close() }
                    viewModel.openNewTab(kind = kind)
                },
                onSwitchWorkspace = { ws ->
                    scope.launch { drawerState.close() }
                    viewModel.switchWorkspace(ws)
                },
                onOpenCreateWorkspace = {
                    scope.launch { drawerState.close() }
                    showCreateWorkspaceDialog = true
                },
                onDeleteWorkspace = { viewModel.deleteWorkspace(it) },
                onRemoveBookmark = { viewModel.removeBookmark(it) },
                onClearHistory = { viewModel.clearHistory() },
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onToggleShowHidden = { viewModel.setShowHiddenFiles(it) },
                onToggleDynamicColor = { viewModel.setDynamicColor(it) }
            )
        }
    ) {
        val selectedCount = focusedTab?.selectedPaths?.size ?: 0
        var selectionOverflowExpanded by remember { mutableStateOf(false) }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (selectedCount > 0 && focusedTab != null) {
                    val selectedNodes = focusedTab.entries.filter { it.path in focusedTab.selectedPaths }
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection(focusedTab.id) }) {
                                Icon(Icons.Outlined.Close, "Clear selection")
                            }
                        },
                        title = {
                            Text(
                                text = "$selectedCount selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        actions = {
                            IconButton(onClick = { viewModel.copyPaths(focusedTab.selectedPaths.toList(), focusedTab.id) }) {
                                Icon(Icons.Outlined.ContentCopy, "Copy selected")
                            }
                            IconButton(onClick = { viewModel.cutPaths(focusedTab.selectedPaths.toList(), focusedTab.id) }) {
                                Icon(Icons.Outlined.ContentCut, "Cut selected")
                            }
                            IconButton(onClick = { deletingRequest = focusedTab.id to selectedNodes }) {
                                Icon(Icons.Outlined.DeleteOutline, "Move to Trash")
                            }
                            Box {
                                IconButton(onClick = { selectionOverflowExpanded = true }) {
                                    Icon(Icons.Outlined.MoreVert, "More selection actions")
                                }
                                DropdownMenu(
                                    expanded = selectionOverflowExpanded,
                                    onDismissRequest = { selectionOverflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                                        onClick = { selectionOverflowExpanded = false; viewModel.selectAll(focusedTab.id) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Invert Selection") },
                                        onClick = { selectionOverflowExpanded = false; viewModel.invertSelection(focusedTab.id) }
                                    )
                                    if (prefs.dualPaneEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("Copy to Other Pane") },
                                            leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                            onClick = { selectionOverflowExpanded = false; viewModel.transferToOtherPane(focusedTab.id, false) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move to Other Pane") },
                                            leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                            onClick = { selectionOverflowExpanded = false; viewModel.transferToOtherPane(focusedTab.id, true) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (selectedNodes.size == 1) "Rename" else "Batch Rename (Regex)") },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            if (selectedNodes.size == 1) {
                                                renamingNode = focusedTab.id to selectedNodes.first()
                                            } else {
                                                batchRenamingTabId = focusedTab.id
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compress to ZIP") },
                                        leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                        onClick = { selectionOverflowExpanded = false; compressingTabId = focusedTab.id }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Encrypt to Vault") },
                                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            viewModel.lockFilesIntoVault(focusedTab.id, focusedTab.selectedPaths.toList())
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Files") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            viewModel.sharePaths(focusedTab.selectedPaths.toList())
                                        }
                                    )
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(Icons.Outlined.Menu, stringResource(R.string.cd_open_drawer))
                            }
                        },
                        title = {
                            Text(
                                text = "Tabbed File Explorer",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showPowerSearchDialog = true },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(Icons.Outlined.Search, stringResource(R.string.cd_search))
                            }
                            IconButton(
                                onClick = { viewModel.toggleDualPane() },
                                modifier = Modifier.testTag("toggle_dual_pane_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Splitscreen,
                                    contentDescription = stringResource(R.string.cd_toggle_split),
                                    tint = if (prefs.dualPaneEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showSortSheet = true },
                                modifier = Modifier.testTag("sort_layout_button")
                            ) {
                                Icon(Icons.Outlined.Sort, stringResource(R.string.cd_sort_options))
                            }
                            IconButton(
                                onClick = { viewModel.toggleLightDarkMode() },
                                modifier = Modifier.testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkEffective) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = stringResource(R.string.cd_toggle_theme),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (focusedTab?.kind == TabKind.DIRECTORY && selectedCount == 0 && uiState.clipboard == null) {
                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("create_new_fab")
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.cd_create_new))
                    }
                }
            },
            bottomBar = {
                val clip = uiState.clipboard
                AnimatedVisibility(visible = clip != null) {
                    if (clip != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Outlined.ContentPaste, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "${if (clip.operation == ClipboardOp.CUT) "Move" else "Copy"} ${clip.paths.size} item(s)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearClipboard() }) { Text("Cancel") }
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = { focusedTab?.let { viewModel.pasteIntoTab(it.id) } },
                                    enabled = focusedTab?.kind == TabKind.DIRECTORY,
                                    modifier = Modifier.testTag("paste_button")
                                ) { Text("Paste Here") }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.busyMessage != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = uiState.busyMessage!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Multi-Tab Strip
                if (uiState.tabs.isNotEmpty()) {
                    BrowserTabStrip(
                        tabs = uiState.tabs,
                        activeTabId = uiState.activeTabId,
                        secondaryTabId = uiState.secondaryTabId,
                        dualPaneEnabled = prefs.dualPaneEnabled,
                        focusedPane = uiState.focusedPane,
                        onSelectTab = { id -> viewModel.selectTab(id, uiState.focusedPane) },
                        onCloseTab = { id -> viewModel.closeTab(id) },
                        onNewTab = {
                            val seedPath = focusedTab?.takeIf { it.kind == TabKind.DIRECTORY }?.path
                                ?: FileSystemEngine.getDefaultRootPath(context)
                            viewModel.openNewTab(path = seedPath, kind = TabKind.DIRECTORY)
                        },
                        onDuplicateTab = { viewModel.duplicateTab(it) },
                        onMoveTab = { id, delta -> viewModel.moveTab(id, delta) },
                        onCloseOtherTabs = { viewModel.closeOtherTabs(it) },
                        onAssignToOtherPane = { id ->
                            val otherPane = if (uiState.focusedPane == 0) 1 else 0
                            viewModel.selectTab(id, otherPane)
                        }
                    )
                }

                // Content Area: Single Pane or Adaptive Dual Pane
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val isWideScreen = maxWidth >= 600.dp

                    @Composable
                    fun RenderTabContent(
                        tab: ExplorerTab,
                        paneIndex: Int,
                        paneLabel: String,
                        modifier: Modifier
                    ) {
                        when (tab.kind) {
                            TabKind.DIRECTORY, TabKind.SEARCH -> {
                                FilePaneView(
                                    tab = tab,
                                    viewLayout = prefs.viewLayout,
                                    isBookmarked = bookmarks.any { it.path == tab.path },
                                    hasAllFilesAccess = uiState.hasAllFilesAccess,
                                    isDualPane = prefs.dualPaneEnabled,
                                    isFocusedPane = uiState.focusedPane == paneIndex,
                                    paneLabel = paneLabel,
                                    onFocusPane = { viewModel.setFocusedPane(paneIndex) },
                                    onRequestAllFilesAccess = { requestAllFilesAccess() },
                                    onNavigateToPath = { viewModel.navigateInTab(tab.id, it) },
                                    onNavigateUp = { viewModel.navigateUp(tab.id) },
                                    onNavigateBack = { viewModel.navigateBack(tab.id) },
                                    onNavigateForward = { viewModel.navigateForward(tab.id) },
                                    onRefresh = { viewModel.refreshTab(tab.id) },
                                    onOpenGoToPath = {
                                        viewModel.setFocusedPane(paneIndex)
                                        showGoToPathDialog = true
                                    },
                                    onCopyPath = { copyToSystemClipboard(it) },
                                    onToggleBookmark = { viewModel.toggleBookmark(tab.path) },
                                    onFilterChange = { viewModel.setQuickFilter(tab.id, it) },
                                    onOpenFile = { node, mode -> viewModel.openFileSmart(node, mode) },
                                    onOpenInNewTab = { viewModel.openNewTab(path = it) },
                                    onToggleSelect = { viewModel.toggleSelect(tab.id, it) },
                                    onInspectProperties = { viewModel.openProperties(it) },
                                    onRenameSingle = { renamingNode = tab.id to it },
                                    onCopySingle = { viewModel.copyPaths(listOf(it.path)) },
                                    onCutSingle = { viewModel.cutPaths(listOf(it.path)) },
                                    onDeleteSingle = { deletingRequest = tab.id to listOf(it) },
                                    onExtractArchive = { node ->
                                        val dest = File(File(node.path).parentFile, File(node.path).nameWithoutExtension).absolutePath
                                        viewModel.extractArchive(node.path, dest)
                                    },
                                    onShareSingle = { viewModel.sharePaths(listOf(it.path)) },
                                    onOpenExternal = { viewModel.openExternally(it.file) },
                                    onLockInVault = { viewModel.lockFilesIntoVault(tab.id, listOf(it.path)) },
                                    modifier = modifier
                                )
                            }
                            TabKind.TRASH -> Box(modifier) {
                                TrashTabView(
                                    items = uiState.trashItems,
                                    activeTrashPath = uiState.activeTrashPath,
                                    isUsingSdcardTrash = uiState.isUsingSdcardTrash,
                                    isLoading = uiState.isTrashLoading,
                                    onRefresh = { viewModel.refreshTrash() },
                                    onRequestAllFilesAccess = { requestAllFilesAccess() },
                                    onRestore = { viewModel.restoreTrashItem(it) },
                                    onDeleteForever = { viewModel.permanentlyDeleteTrashItem(it) },
                                    onEmptyTrash = { viewModel.emptyTrash() },
                                    onBrowseTrashFolder = { viewModel.openNewTab(path = it) }
                                )
                            }
                            TabKind.STORAGE_ANALYZER -> Box(modifier) {
                                StorageAnalyzerTabView(
                                    report = uiState.storageReport,
                                    isAnalyzing = uiState.isAnalyzingStorage,
                                    onRunAnalysis = { mb ->
                                        viewModel.runStorageAnalysis(largeFileThresholdMb = mb)
                                    },
                                    onJumpToFolder = { viewModel.openNewTab(path = it) },
                                    onTrashPaths = { paths -> viewModel.deletePaths(null, paths, permanent = false) }
                                )
                            }
                            TabKind.HTTP_SERVER -> Box(modifier) {
                                LanHttpServerTabView(
                                    state = httpServerState,
                                    defaultRootPath = FileSystemEngine.getDefaultRootPath(context),
                                    onStart = { root, port, ro -> viewModel.startHttpServer(root, port, ro) },
                                    onStop = { viewModel.stopHttpServer() },
                                    onCopyUrl = { copyToSystemClipboard(it) }
                                )
                            }
                            TabKind.VAULT -> Box(modifier) {
                                EncryptedVaultTabView(
                                    hasPinConfigured = viewModel.hasVaultPin(),
                                    isUnlocked = uiState.unlockedVaultPin != null,
                                    entries = vaultEntries,
                                    onUnlockOrCreate = { viewModel.setupOrUnlockVault(it) },
                                    onLockVault = { viewModel.lockVault() },
                                    onRestoreEntry = { viewModel.restoreFromVault(it) },
                                    onDeleteEntry = { viewModel.deleteVaultEntry(it) }
                                )
                            }
                            TabKind.APPS -> Box(modifier) {
                                AppInspectorTabView(
                                    apps = uiState.installedApps,
                                    isLoading = uiState.isLoadingApps,
                                    onRefresh = { viewModel.loadInstalledApps() },
                                    onBackupApk = { viewModel.backupAppApk(it) },
                                    onLaunchApp = { viewModel.launchApp(it) },
                                    onOpenAppInfo = { viewModel.openAppDetails(it) }
                                )
                            }
                        }
                    }

                    if (prefs.dualPaneEnabled && primaryTab != null && secondaryTab != null) {
                        if (isWideScreen) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            ) {
                                RenderTabContent(primaryTab, 0, "A", Modifier.weight(1f).fillMaxHeight())
                                RenderTabContent(secondaryTab, 1, "B", Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            ) {
                                RenderTabContent(primaryTab, 0, "A", Modifier.weight(1f).fillMaxWidth())
                                RenderTabContent(secondaryTab, 1, "B", Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    } else if (primaryTab != null) {
                        RenderTabContent(primaryTab, 0, "A", Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    // --- Modals & Bottom Sheets ---

    if (showCreateDialog && focusedTab != null) {
        CreateNewItemDialog(
            onDismiss = { showCreateDialog = false },
            onCreateFolder = { viewModel.createFolder(focusedTab.id, it) },
            onCreateFile = { viewModel.createFile(focusedTab.id, it) }
        )
    }

    renamingNode?.let { (tabId, node) ->
        RenameSingleDialog(
            node = node,
            onDismiss = { renamingNode = null },
            onConfirm = { newName -> viewModel.renameFile(tabId, node.path, newName) }
        )
    }

    batchRenamingTabId?.let { tabId ->
        val tab = uiState.tabs.firstOrNull { it.id == tabId }
        val nodes = tab?.entries?.filter { it.path in tab.selectedPaths } ?: emptyList()
        if (nodes.isNotEmpty()) {
            BatchRenameDialog(
                nodes = nodes,
                onDismiss = { batchRenamingTabId = null },
                onConfirm = { pairs -> viewModel.batchRename(tabId, pairs) }
            )
        } else {
            batchRenamingTabId = null
        }
    }

    deletingRequest?.let { (tabId, nodes) ->
        if (nodes.isNotEmpty()) {
            DeleteConfirmDialog(
                count = nodes.size,
                sampleName = nodes.first().name,
                activeTrashPath = uiState.activeTrashPath,
                onDismiss = { deletingRequest = null },
                onConfirm = { permanent ->
                    viewModel.deletePaths(tabId, nodes.map { it.path }, permanent)
                }
            )
        } else {
            deletingRequest = null
        }
    }

    compressingTabId?.let { tabId ->
        val tab = uiState.tabs.firstOrNull { it.id == tabId }
        val paths = tab?.selectedPaths?.toList() ?: emptyList()
        if (paths.isNotEmpty()) {
            val defaultName = if (paths.size == 1) File(paths.first()).nameWithoutExtension else "archive_${System.currentTimeMillis() / 1000}"
            CompressZipDialog(
                itemCount = paths.size,
                defaultArchiveName = defaultName,
                onDismiss = { compressingTabId = null },
                onConfirm = { name, level, stripMac ->
                    viewModel.compressToZip(tabId, paths, name, level, stripMac)
                }
            )
        } else {
            compressingTabId = null
        }
    }

    if (showGoToPathDialog) {
        GoToPathDialog(
            currentPath = focusedTab?.path ?: "/sdcard",
            activeTrashPath = uiState.activeTrashPath,
            onDismiss = { showGoToPathDialog = false },
            onNavigate = { target ->
                val tab = focusedTab
                if (tab != null && tab.kind == TabKind.DIRECTORY) {
                    viewModel.navigateInTab(tab.id, target)
                } else {
                    viewModel.openNewTab(path = target)
                }
            }
        )
    }

    if (showPowerSearchDialog) {
        val scopeRoot = focusedTab?.takeIf { it.kind == TabKind.DIRECTORY }?.path
            ?: FileSystemEngine.getDefaultRootPath(context)
        PowerSearchDialog(
            rootPath = scopeRoot,
            onDismiss = { showPowerSearchDialog = false },
            onSearch = { filter -> viewModel.runPowerSearch(scopeRoot, filter) }
        )
    }

    if (showCreateWorkspaceDialog) {
        CreateWorkspaceDialog(
            onDismiss = { showCreateWorkspaceDialog = false },
            onCreate = { viewModel.createWorkspace(it) }
        )
    }

    if (showSortSheet) {
        SortAndViewBottomSheet(
            currentLayout = prefs.viewLayout,
            currentSort = prefs.sortOption,
            ascending = prefs.sortAscending,
            foldersFirst = prefs.foldersFirst,
            onSelectLayout = { viewModel.setViewLayout(it) },
            onSelectSort = { opt, asc, ff -> viewModel.setSort(opt, asc, ff) },
            onDismiss = { showSortSheet = false }
        )
    }

    uiState.inspectingProperties?.let { props ->
        PropertiesAndHashSheet(
            properties = props,
            hashes = uiState.inspectingHashes,
            isComputingHashes = uiState.isComputingHashes,
            onComputeHashes = { viewModel.computePropertiesHashes() },
            onCopyText = { copyToSystemClipboard(it) },
            onDismiss = { viewModel.closeProperties() }
        )
    }
}
