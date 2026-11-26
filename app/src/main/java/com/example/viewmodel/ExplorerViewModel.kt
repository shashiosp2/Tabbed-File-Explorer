package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkEntity
import com.example.data.ExplorerDatabase
import com.example.data.ExplorerPreferences
import com.example.data.HistoryEntity
import com.example.data.PreferencesRepository
import com.example.data.VaultEntryEntity
import com.example.data.WorkspaceEntity
import com.example.engine.ActiveViewerSession
import com.example.engine.ArchiveManager
import com.example.engine.ClipboardOp
import com.example.engine.ClipboardState
import com.example.engine.EncryptedVaultManager
import com.example.engine.ExplorerTab
import com.example.engine.FileNode
import com.example.engine.FileProperties
import com.example.engine.FileSystemEngine
import com.example.engine.HashResult
import com.example.engine.HttpServerState
import com.example.engine.InstalledAppInfo
import com.example.engine.LanHttpServer
import com.example.engine.SearchFilter
import com.example.engine.SortOption
import com.example.engine.StorageAnalysisReport
import com.example.engine.StorageVolumeInfo
import com.example.engine.TabKind
import com.example.engine.TrashItem
import com.example.engine.TrashManager
import com.example.engine.ViewLayout
import com.example.engine.ViewerMode
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ExplorerUiState(
    val tabs: List<ExplorerTab> = emptyList(),
    val activeTabId: String = "",
    val secondaryTabId: String? = null,
    val focusedPane: Int = 0, // 0 = primary, 1 = secondary (when dual pane is enabled)
    val clipboard: ClipboardState? = null,
    val storageVolumes: List<StorageVolumeInfo> = emptyList(),
    val hasAllFilesAccess: Boolean = false,
    val trashItems: List<TrashItem> = emptyList(),
    val activeTrashPath: String = TrashManager.PRIMARY_SDCARD_TRASH,
    val isUsingSdcardTrash: Boolean = false,
    val isTrashLoading: Boolean = false,
    val storageReport: StorageAnalysisReport? = null,
    val isAnalyzingStorage: Boolean = false,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val activeViewer: ActiveViewerSession? = null,
    val inspectingProperties: FileProperties? = null,
    val inspectingHashes: HashResult? = null,
    val isComputingHashes: Boolean = false,
    val unlockedVaultPin: String? = null,
    val busyMessage: String? = null
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = PreferencesRepository(appContext)
    private val db = ExplorerDatabase.getInstance(appContext)
    private val dao = db.explorerDao()
    private val trashManager = TrashManager(appContext, dao)
    private val vaultManager = EncryptedVaultManager(appContext, dao)
    private val httpServer = LanHttpServer()

    val preferences: StateFlow<ExplorerPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExplorerPreferences())

    val workspaces: StateFlow<List<WorkspaceEntity>> = dao.observeWorkspaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = dao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultEntries: StateFlow<List<VaultEntryEntity>> = dao.observeVaultEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val httpServerState: StateFlow<HttpServerState> = httpServer.state

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init {
        initializeExplorer()
    }

    private fun initializeExplorer() {
        viewModelScope.launch {
            val prefs = prefsRepo.preferencesFlow.first()
            val hasAccess = FileSystemEngine.hasAllFilesAccess()
            val defaultRoot = FileSystemEngine.getDefaultRootPath(appContext)
            val volumes = FileSystemEngine.getStorageVolumes(appContext)

            // Ensure Default workspace exists
            val existingWorkspaces = dao.observeWorkspaces().first()
            val activeWorkspace = if (existingWorkspaces.isEmpty()) {
                val initialTabsJson = serializeTabs(
                    listOf(
                        TabSpec(TabKind.DIRECTORY, labelForPath(defaultRoot), defaultRoot),
                        TabSpec(TabKind.TRASH, "Trash", trashManager.getActiveTrashPath())
                    )
                )
                val id = dao.upsertWorkspace(WorkspaceEntity(name = "Default", tabPathsSerialized = initialTabsJson))
                prefsRepo.setActiveWorkspaceId(id)
                dao.getWorkspaceById(id)
            } else {
                existingWorkspaces.firstOrNull { it.id == prefs.activeWorkspaceId }
                    ?: existingWorkspaces.first()
            }

            val restoredSpecs = activeWorkspace?.let { deserializeTabs(it.tabPathsSerialized) }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(TabSpec(TabKind.DIRECTORY, labelForPath(defaultRoot), defaultRoot))

            val initialTabs = restoredSpecs.map { spec ->
                val resolvedPath = if (spec.kind == TabKind.DIRECTORY && !File(spec.path).exists()) {
                    defaultRoot
                } else spec.path
                ExplorerTab(
                    id = UUID.randomUUID().toString(),
                    kind = spec.kind,
                    title = if (spec.kind == TabKind.DIRECTORY) labelForPath(resolvedPath) else spec.title,
                    path = resolvedPath
                )
            }

            val primaryId = initialTabs.first().id
            val secondaryId = initialTabs.getOrNull(1)?.id ?: primaryId

            _uiState.update {
                it.copy(
                    tabs = initialTabs,
                    activeTabId = primaryId,
                    secondaryTabId = secondaryId,
                    storageVolumes = volumes,
                    hasAllFilesAccess = hasAccess,
                    activeTrashPath = trashManager.getActiveTrashPath(),
                    isUsingSdcardTrash = trashManager.isUsingPrimarySdcardTrash()
                )
            }

            initialTabs.filter { it.kind == TabKind.DIRECTORY }.forEach { refreshTab(it.id) }
            refreshTrash()
        }
    }

    fun refreshStoragePermissionState() {
        val nowHasAccess = FileSystemEngine.hasAllFilesAccess()
        val prevHasAccess = _uiState.value.hasAllFilesAccess
        val volumes = FileSystemEngine.getStorageVolumes(appContext)
        _uiState.update {
            it.copy(
                hasAllFilesAccess = nowHasAccess,
                storageVolumes = volumes,
                activeTrashPath = trashManager.getActiveTrashPath(),
                isUsingSdcardTrash = trashManager.isUsingPrimarySdcardTrash()
            )
        }
        if (nowHasAccess && !prevHasAccess) {
            val sdcard = File(FileSystemEngine.SDCARD_ROOT)
            if (sdcard.exists() && sdcard.canRead()) {
                val activeId = getFocusedTabId()
                val activeTab = _uiState.value.tabs.firstOrNull { it.id == activeId }
                if (activeTab?.kind == TabKind.DIRECTORY) {
                    navigateInTab(activeId, sdcard.absolutePath)
                }
            }
            emitMessage("All Files Access granted — /sdcard and /sdcard/.Trash are now active")
        }
        refreshAllDirectoryTabs()
        refreshTrash()
    }

    fun getFocusedTabId(): String {
        val state = _uiState.value
        return if (preferences.value.dualPaneEnabled && state.focusedPane == 1 && state.secondaryTabId != null) {
            state.secondaryTabId
        } else {
            state.activeTabId
        }
    }

    fun getTabById(tabId: String): ExplorerTab? = _uiState.value.tabs.firstOrNull { it.id == tabId }

    fun setFocusedPane(paneIndex: Int) {
        _uiState.update { it.copy(focusedPane = paneIndex.coerceIn(0, 1)) }
    }

    // --- Theme & Preferences ---

    fun toggleLightDarkMode() {
        viewModelScope.launch {
            val current = preferences.value.themeMode
            val next = if (current == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
            prefsRepo.setThemeMode(next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColor(enabled) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            prefsRepo.setShowHiddenFiles(show)
            refreshAllDirectoryTabs()
        }
    }

    fun setViewLayout(layout: ViewLayout) {
        viewModelScope.launch { prefsRepo.setViewLayout(layout) }
    }

    fun setSort(option: SortOption, ascending: Boolean, foldersFirst: Boolean) {
        viewModelScope.launch {
            prefsRepo.setSort(option, ascending, foldersFirst)
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { tab ->
                        if (tab.kind == TabKind.DIRECTORY) {
                            tab.copy(entries = FileSystemEngine.sortNodes(tab.entries, option, ascending, foldersFirst))
                        } else tab
                    }
                )
            }
        }
    }

    fun toggleDualPane() {
        viewModelScope.launch {
            val next = !preferences.value.dualPaneEnabled
            prefsRepo.setDualPaneEnabled(next)
            if (next) {
                val state = _uiState.value
                if (state.tabs.size < 2) {
                    val currentPath = getTabById(state.activeTabId)?.takeIf { it.kind == TabKind.DIRECTORY }?.path
                        ?: FileSystemEngine.getDefaultRootPath(appContext)
                    val secondTab = ExplorerTab(
                        id = UUID.randomUUID().toString(),
                        kind = TabKind.DIRECTORY,
                        title = labelForPath(currentPath),
                        path = currentPath
                    )
                    _uiState.update {
                        it.copy(
                            tabs = it.tabs + secondTab,
                            secondaryTabId = secondTab.id
                        )
                    }
                    refreshTab(secondTab.id)
                    persistActiveWorkspaceTabs()
                } else if (state.secondaryTabId == null || state.secondaryTabId == state.activeTabId) {
                    val other = state.tabs.firstOrNull { it.id != state.activeTabId } ?: state.tabs.first()
                    _uiState.update { it.copy(secondaryTabId = other.id) }
                }
            }
        }
    }

    // --- Tab Management ---

    fun openNewTab(
        path: String = FileSystemEngine.getDefaultRootPath(appContext),
        kind: TabKind = TabKind.DIRECTORY,
        title: String? = null
    ) {
        // If opening a singleton tool tab that already exists, focus it
        if (kind != TabKind.DIRECTORY && kind != TabKind.SEARCH) {
            val existing = _uiState.value.tabs.firstOrNull { it.kind == kind }
            if (existing != null) {
                selectTab(existing.id, _uiState.value.focusedPane)
                when (kind) {
                    TabKind.TRASH -> refreshTrash()
                    TabKind.APPS -> if (_uiState.value.installedApps.isEmpty()) loadInstalledApps()
                    TabKind.STORAGE_ANALYZER -> if (_uiState.value.storageReport == null) runStorageAnalysis()
                    else -> Unit
                }
                return
            }
        }

        val resolvedTitle = title ?: when (kind) {
            TabKind.DIRECTORY -> labelForPath(path)
            TabKind.TRASH -> "Trash"
            TabKind.SEARCH -> "Search"
            TabKind.STORAGE_ANALYZER -> "Analyzer"
            TabKind.HTTP_SERVER -> "LAN Server"
            TabKind.VAULT -> "Vault"
            TabKind.APPS -> "Apps"
        }

        val newTab = ExplorerTab(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = resolvedTitle,
            path = path
        )

        _uiState.update { state ->
            val updatedTabs = state.tabs + newTab
            if (preferences.value.dualPaneEnabled && state.focusedPane == 1) {
                state.copy(tabs = updatedTabs, secondaryTabId = newTab.id)
            } else {
                state.copy(tabs = updatedTabs, activeTabId = newTab.id)
            }
        }

        when (kind) {
            TabKind.DIRECTORY -> refreshTab(newTab.id)
            TabKind.TRASH -> refreshTrash()
            TabKind.APPS -> loadInstalledApps()
            TabKind.STORAGE_ANALYZER -> runStorageAnalysis()
            else -> Unit
        }
        persistActiveWorkspaceTabs()
    }

    fun selectTab(tabId: String, paneIndex: Int = _uiState.value.focusedPane) {
        _uiState.update { state ->
            if (preferences.value.dualPaneEnabled && paneIndex == 1) {
                state.copy(secondaryTabId = tabId, focusedPane = 1)
            } else {
                state.copy(activeTabId = tabId, focusedPane = 0)
            }
        }
    }

    fun closeTab(tabId: String) {
        val state = _uiState.value
        if (state.tabs.size <= 1) {
            emitMessage("At least one tab must stay open")
            return
        }
        val idx = state.tabs.indexOfFirst { it.id == tabId }
        if (idx == -1) return
        val updated = state.tabs.filterNot { it.id == tabId }
        val fallback = updated.getOrNull((idx - 1).coerceAtLeast(0)) ?: updated.first()
        _uiState.update {
            it.copy(
                tabs = updated,
                activeTabId = if (it.activeTabId == tabId) fallback.id else it.activeTabId,
                secondaryTabId = if (it.secondaryTabId == tabId) fallback.id else it.secondaryTabId
            )
        }
        persistActiveWorkspaceTabs()
    }

    fun duplicateTab(tabId: String) {
        val tab = getTabById(tabId) ?: return
        openNewTab(path = tab.path, kind = tab.kind, title = tab.title)
    }

    fun moveTab(tabId: String, delta: Int) {
        _uiState.update { state ->
            val list = state.tabs.toMutableList()
            val idx = list.indexOfFirst { it.id == tabId }
            val targetIdx = idx + delta
            if (idx == -1 || targetIdx !in list.indices) return@update state
            val item = list.removeAt(idx)
            list.add(targetIdx, item)
            state.copy(tabs = list)
        }
        persistActiveWorkspaceTabs()
    }

    fun closeOtherTabs(keepTabId: String) {
        val keep = getTabById(keepTabId) ?: return
        _uiState.update {
            it.copy(
                tabs = listOf(keep),
                activeTabId = keep.id,
                secondaryTabId = keep.id
            )
        }
        persistActiveWorkspaceTabs()
    }

    // --- Navigation ---

    fun navigateInTab(tabId: String, newPath: String, recordBackStack: Boolean = true) {
        val normalized = File(newPath).absolutePath
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id != tabId) tab
                    else tab.copy(
                        kind = TabKind.DIRECTORY,
                        path = normalized,
                        title = labelForPath(normalized),
                        backStack = if (recordBackStack && tab.kind == TabKind.DIRECTORY && tab.path != normalized) {
                            (tab.backStack + tab.path).takeLast(30)
                        } else tab.backStack,
                        forwardStack = if (recordBackStack) emptyList() else tab.forwardStack,
                        selectedPaths = emptySet(),
                        filterQuery = "",
                        isLoading = true,
                        errorMessage = null
                    )
                }
            )
        }
        viewModelScope.launch {
            dao.upsertHistory(HistoryEntity(path = normalized, label = labelForPath(normalized)))
        }
        refreshTab(tabId)
        persistActiveWorkspaceTabs()
    }

    fun navigateUp(tabId: String): Boolean {
        val tab = getTabById(tabId) ?: return false
        if (tab.kind != TabKind.DIRECTORY) return false
        val parent = File(tab.path).parentFile ?: return false
        if (!parent.canRead() && parent.absolutePath == "/storage/emulated") {
            return false
        }
        navigateInTab(tabId, parent.absolutePath)
        return true
    }

    fun navigateBack(tabId: String): Boolean {
        val tab = getTabById(tabId) ?: return false
        val previous = tab.backStack.lastOrNull() ?: return false
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { t ->
                    if (t.id != tabId) t
                    else t.copy(
                        path = previous,
                        title = labelForPath(previous),
                        backStack = t.backStack.dropLast(1),
                        forwardStack = (t.forwardStack + t.path).takeLast(30),
                        selectedPaths = emptySet(),
                        filterQuery = "",
                        isLoading = true,
                        errorMessage = null
                    )
                }
            )
        }
        refreshTab(tabId)
        persistActiveWorkspaceTabs()
        return true
    }

    fun navigateForward(tabId: String): Boolean {
        val tab = getTabById(tabId) ?: return false
        val next = tab.forwardStack.lastOrNull() ?: return false
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { t ->
                    if (t.id != tabId) t
                    else t.copy(
                        path = next,
                        title = labelForPath(next),
                        forwardStack = t.forwardStack.dropLast(1),
                        backStack = (t.backStack + t.path).takeLast(30),
                        selectedPaths = emptySet(),
                        filterQuery = "",
                        isLoading = true,
                        errorMessage = null
                    )
                }
            )
        }
        refreshTab(tabId)
        persistActiveWorkspaceTabs()
        return true
    }

    fun refreshTab(tabId: String) {
        val tab = getTabById(tabId) ?: return
        if (tab.kind != TabKind.DIRECTORY) return
        val prefs = preferences.value
        viewModelScope.launch {
            _uiState.update { s ->
                s.copy(tabs = s.tabs.map { if (it.id == tabId) it.copy(isLoading = true, errorMessage = null) else it })
            }
            val res = FileSystemEngine.listDirectory(
                path = tab.path,
                showHidden = prefs.showHiddenFiles,
                sortOption = prefs.sortOption,
                ascending = prefs.sortAscending,
                foldersFirst = prefs.foldersFirst
            )
            _uiState.update { s ->
                s.copy(
                    tabs = s.tabs.map { t ->
                        if (t.id != tabId) t
                        else res.fold(
                            onSuccess = { list ->
                                t.copy(entries = list, isLoading = false, errorMessage = null)
                            },
                            onFailure = { err ->
                                t.copy(entries = emptyList(), isLoading = false, errorMessage = err.message)
                            }
                        )
                    }
                )
            }
        }
    }

    fun refreshAllDirectoryTabs() {
        _uiState.value.tabs.filter { it.kind == TabKind.DIRECTORY }.forEach { refreshTab(it.id) }
    }

    fun setQuickFilter(tabId: String, query: String) {
        _uiState.update { s ->
            s.copy(tabs = s.tabs.map { if (it.id == tabId) it.copy(filterQuery = query) else it })
        }
    }

    // --- Selection & Clipboard ---

    fun toggleSelect(tabId: String, path: String) {
        _uiState.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.id != tabId) t
                    else {
                        val next = if (path in t.selectedPaths) t.selectedPaths - path else t.selectedPaths + path
                        t.copy(selectedPaths = next)
                    }
                }
            )
        }
    }

    fun selectAll(tabId: String) {
        _uiState.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.id != tabId) t
                    else t.copy(selectedPaths = t.entries.map { it.path }.toSet())
                }
            )
        }
    }

    fun invertSelection(tabId: String) {
        _uiState.update { s ->
            s.copy(
                tabs = s.tabs.map { t ->
                    if (t.id != tabId) t
                    else {
                        val all = t.entries.map { it.path }.toSet()
                        t.copy(selectedPaths = all - t.selectedPaths)
                    }
                }
            )
        }
    }

    fun clearSelection(tabId: String) {
        _uiState.update { s ->
            s.copy(tabs = s.tabs.map { if (it.id == tabId) it.copy(selectedPaths = emptySet()) else it })
        }
    }

    fun copyPaths(paths: List<String>, clearTabId: String? = null) {
        if (paths.isEmpty()) return
        _uiState.update { it.copy(clipboard = ClipboardState(ClipboardOp.COPY, paths)) }
        if (clearTabId != null) clearSelection(clearTabId)
        emitMessage("Copied ${paths.size} item(s) to clipboard")
    }

    fun cutPaths(paths: List<String>, clearTabId: String? = null) {
        if (paths.isEmpty()) return
        _uiState.update { it.copy(clipboard = ClipboardState(ClipboardOp.CUT, paths)) }
        if (clearTabId != null) clearSelection(clearTabId)
        emitMessage("Cut ${paths.size} item(s) to clipboard")
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboard = null) }
    }

    fun pasteIntoTab(tabId: String) {
        val clip = _uiState.value.clipboard ?: return
        val tab = getTabById(tabId) ?: return
        if (tab.kind != TabKind.DIRECTORY) {
            emitMessage("Switch to a directory tab to paste")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = if (clip.operation == ClipboardOp.CUT) "Moving files..." else "Copying files...") }
            val result = FileSystemEngine.copyOrMove(
                sourcePaths = clip.paths,
                destinationDirPath = tab.path,
                isMove = clip.operation == ClipboardOp.CUT
            )
            _uiState.update { it.copy(busyMessage = null) }
            result.fold(
                onSuccess = { count ->
                    if (clip.operation == ClipboardOp.CUT) clearClipboard()
                    refreshAllDirectoryTabs()
                    emitMessage("${if (clip.operation == ClipboardOp.CUT) "Moved" else "Copied"} $count item(s)")
                },
                onFailure = { err -> emitMessage("Paste failed: ${err.message}") }
            )
        }
    }

    fun transferToOtherPane(sourceTabId: String, isMove: Boolean) {
        val state = _uiState.value
        val sourceTab = getTabById(sourceTabId) ?: return
        val targetTabId = if (sourceTabId == state.activeTabId) state.secondaryTabId else state.activeTabId
        val targetTab = targetTabId?.let { getTabById(it) }
        if (targetTab == null || targetTab.kind != TabKind.DIRECTORY) {
            emitMessage("Other pane must be open to a directory")
            return
        }
        val paths = sourceTab.selectedPaths.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = if (isMove) "Moving to other pane..." else "Copying to other pane...") }
            val result = FileSystemEngine.copyOrMove(paths, targetTab.path, isMove)
            _uiState.update { it.copy(busyMessage = null) }
            result.fold(
                onSuccess = { count ->
                    clearSelection(sourceTabId)
                    refreshAllDirectoryTabs()
                    emitMessage("${if (isMove) "Moved" else "Copied"} $count item(s) to ${targetTab.title}")
                },
                onFailure = { err -> emitMessage("Transfer failed: ${err.message}") }
            )
        }
    }

    // --- File CRUD, Rename, Delete (/sdcard/.Trash), Archive ---

    fun createFolder(tabId: String, name: String) {
        val tab = getTabById(tabId) ?: return
        viewModelScope.launch {
            FileSystemEngine.createFolder(tab.path, name).fold(
                onSuccess = {
                    refreshAllDirectoryTabs()
                    emitMessage("Created folder ${it.name}")
                },
                onFailure = { emitMessage(it.message ?: "Failed to create folder") }
            )
        }
    }

    fun createFile(tabId: String, name: String) {
        val tab = getTabById(tabId) ?: return
        viewModelScope.launch {
            FileSystemEngine.createFile(tab.path, name).fold(
                onSuccess = { created ->
                    refreshAllDirectoryTabs()
                    emitMessage("Created ${created.name}")
                },
                onFailure = { emitMessage(it.message ?: "Failed to create file") }
            )
        }
    }

    fun renameFile(tabId: String, path: String, newName: String) {
        viewModelScope.launch {
            FileSystemEngine.renameFile(path, newName).fold(
                onSuccess = {
                    clearSelection(tabId)
                    refreshAllDirectoryTabs()
                    emitMessage("Renamed to ${it.name}")
                },
                onFailure = { emitMessage(it.message ?: "Rename failed") }
            )
        }
    }

    fun batchRename(tabId: String, pairs: List<Pair<FileNode, String>>) {
        viewModelScope.launch {
            FileSystemEngine.executeBatchRename(pairs).fold(
                onSuccess = { count ->
                    clearSelection(tabId)
                    refreshAllDirectoryTabs()
                    emitMessage("Batch renamed $count item(s)")
                },
                onFailure = { emitMessage("Batch rename error: ${it.message}") }
            )
        }
    }

    fun deletePaths(tabId: String?, paths: List<String>, permanent: Boolean = false) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = if (permanent) "Permanently deleting..." else "Moving to .Trash...") }
            if (permanent) {
                var removed = 0
                paths.forEach { p ->
                    val f = File(p)
                    if (f.exists() && FileSystemEngine.deleteRecursive(f)) removed++
                }
                _uiState.update { it.copy(busyMessage = null) }
                if (tabId != null) clearSelection(tabId)
                refreshAllDirectoryTabs()
                emitMessage("Permanently deleted $removed item(s)")
            } else {
                val res = trashManager.moveToTrash(paths)
                _uiState.update { it.copy(busyMessage = null) }
                res.fold(
                    onSuccess = { trashed ->
                        if (tabId != null) clearSelection(tabId)
                        refreshAllDirectoryTabs()
                        refreshTrash()
                        val sampleEpoch = trashed.firstOrNull()?.epochTimestamp
                        val rootLabel = trashManager.getActiveTrashPath()
                        emitMessage(
                            if (trashed.size == 1 && sampleEpoch != null) {
                                "Moved to $rootLabel/$sampleEpoch"
                            } else {
                                "Moved ${trashed.size} item(s) to $rootLabel/<epoch>"
                            }
                        )
                    },
                    onFailure = { err -> emitMessage("Trash failed: ${err.message}") }
                )
            }
        }
    }

    fun compressToZip(tabId: String, paths: List<String>, zipFileName: String, level: Int, stripMac: Boolean) {
        val tab = getTabById(tabId) ?: return
        if (paths.isEmpty()) return
        val finalName = if (zipFileName.endsWith(".zip", ignoreCase = true)) zipFileName else "$zipFileName.zip"
        val outPath = File(tab.path, finalName).absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = "Compressing into $finalName...") }
            val res = ArchiveManager.compressToZip(paths, outPath, level, stripMac)
            _uiState.update { it.copy(busyMessage = null) }
            res.fold(
                onSuccess = {
                    clearSelection(tabId)
                    refreshAllDirectoryTabs()
                    emitMessage("Created archive ${it.name} (${FileSystemEngine.formatSize(it.length())})")
                },
                onFailure = { emitMessage("Compression failed: ${it.message}") }
            )
        }
    }

    fun extractArchive(zipPath: String, destinationDirPath: String, stripMac: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = "Extracting archive...") }
            val res = ArchiveManager.extractZip(zipPath, destinationDirPath, stripMac)
            _uiState.update { it.copy(busyMessage = null) }
            res.fold(
                onSuccess = { count ->
                    refreshAllDirectoryTabs()
                    emitMessage("Extracted $count file(s) to ${File(destinationDirPath).name}")
                },
                onFailure = { emitMessage("Extraction failed: ${it.message}") }
            )
        }
    }

    // --- Trash Actions ---

    fun refreshTrash() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTrashLoading = true,
                    activeTrashPath = trashManager.getActiveTrashPath(),
                    isUsingSdcardTrash = trashManager.isUsingPrimarySdcardTrash()
                )
            }
            val items = trashManager.listTrashItems()
            _uiState.update { it.copy(trashItems = items, isTrashLoading = false) }
        }
    }

    fun restoreTrashItem(item: TrashItem) {
        viewModelScope.launch {
            trashManager.restoreItem(item).fold(
                onSuccess = { restored ->
                    refreshTrash()
                    refreshAllDirectoryTabs()
                    emitMessage("Restored ${restored.name} to ${restored.parent}")
                },
                onFailure = { emitMessage("Restore failed: ${it.message}") }
            )
        }
    }

    fun permanentlyDeleteTrashItem(item: TrashItem) {
        viewModelScope.launch {
            trashManager.permanentlyDeleteItem(item).fold(
                onSuccess = {
                    refreshTrash()
                    emitMessage("Permanently removed ${item.originalName}")
                },
                onFailure = { emitMessage("Delete failed: ${it.message}") }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashManager.emptyTrash().fold(
                onSuccess = { count ->
                    refreshTrash()
                    emitMessage("Emptied $count item(s) from Trash")
                },
                onFailure = { emitMessage("Empty trash failed: ${it.message}") }
            )
        }
    }

    // --- Workspaces, Bookmarks, History ---

    fun createWorkspace(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val serialized = serializeTabs(_uiState.value.tabs.map { TabSpec(it.kind, it.title, it.path) })
            val id = dao.upsertWorkspace(WorkspaceEntity(name = trimmed, tabPathsSerialized = serialized))
            prefsRepo.setActiveWorkspaceId(id)
            emitMessage("Saved workspace \"$trimmed\"")
        }
    }

    fun switchWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            prefsRepo.setActiveWorkspaceId(workspace.id)
            val defaultRoot = FileSystemEngine.getDefaultRootPath(appContext)
            val specs = deserializeTabs(workspace.tabPathsSerialized).ifEmpty {
                listOf(TabSpec(TabKind.DIRECTORY, labelForPath(defaultRoot), defaultRoot))
            }
            val newTabs = specs.map { spec ->
                val resolvedPath = if (spec.kind == TabKind.DIRECTORY && !File(spec.path).exists()) {
                    defaultRoot
                } else spec.path
                ExplorerTab(
                    id = UUID.randomUUID().toString(),
                    kind = spec.kind,
                    title = if (spec.kind == TabKind.DIRECTORY) labelForPath(resolvedPath) else spec.title,
                    path = resolvedPath
                )
            }
            _uiState.update {
                it.copy(
                    tabs = newTabs,
                    activeTabId = newTabs.first().id,
                    secondaryTabId = newTabs.getOrNull(1)?.id ?: newTabs.first().id
                )
            }
            newTabs.filter { it.kind == TabKind.DIRECTORY }.forEach { refreshTab(it.id) }
            emitMessage("Switched to workspace \"${workspace.name}\"")
        }
    }

    fun deleteWorkspace(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            if (workspaces.value.size <= 1) {
                emitMessage("Cannot delete the last workspace")
                return@launch
            }
            dao.deleteWorkspace(workspace.id)
            emitMessage("Deleted workspace \"${workspace.name}\"")
        }
    }

    private fun persistActiveWorkspaceTabs() {
        viewModelScope.launch {
            val activeId = preferences.value.activeWorkspaceId
            val current = dao.getWorkspaceById(activeId) ?: return@launch
            val serialized = serializeTabs(_uiState.value.tabs.map { TabSpec(it.kind, it.title, it.path) })
            dao.upsertWorkspace(current.copy(tabPathsSerialized = serialized, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleBookmark(path: String) {
        viewModelScope.launch {
            val existing = bookmarks.value.firstOrNull { it.path == path }
            if (existing != null) {
                dao.deleteBookmark(existing.id)
                emitMessage("Removed bookmark")
            } else {
                dao.insertBookmark(BookmarkEntity(label = labelForPath(path), path = path))
                emitMessage("Bookmarked ${labelForPath(path)}")
            }
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch { dao.deleteBookmark(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
            emitMessage("Cleared navigation history")
        }
    }

    // --- Power Search ---

    fun runPowerSearch(rootPath: String, filter: SearchFilter) {
        val searchTabId = UUID.randomUUID().toString()
        val label = if (filter.query.isNotBlank()) "Search: ${filter.query}" else "Search: *${filter.extensionFilter}"
        val searchTab = ExplorerTab(
            id = searchTabId,
            kind = TabKind.SEARCH,
            title = label,
            path = rootPath,
            isLoading = true
        )
        _uiState.update { state ->
            val updated = state.tabs + searchTab
            if (preferences.value.dualPaneEnabled && state.focusedPane == 1) {
                state.copy(tabs = updated, secondaryTabId = searchTabId)
            } else {
                state.copy(tabs = updated, activeTabId = searchTabId)
            }
        }
        viewModelScope.launch {
            val hits = FileSystemEngine.searchRecursive(rootPath, filter)
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { t ->
                        if (t.id == searchTabId) t.copy(entries = hits, isLoading = false) else t
                    }
                )
            }
        }
    }

    // --- Storage Analyzer ---

    fun runStorageAnalysis(
        rootPath: String = FileSystemEngine.getDefaultRootPath(appContext),
        largeFileThresholdMb: Long = 5L
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingStorage = true) }
            val report = FileSystemEngine.analyzeStorage(rootPath, largeFileThresholdMb)
            _uiState.update { it.copy(storageReport = report, isAnalyzingStorage = false) }
        }
    }

    // --- LAN HTTP Server ---

    fun startHttpServer(rootPath: String, port: Int, readOnly: Boolean) {
        httpServer.start(rootPath, port, readOnly)
    }

    fun stopHttpServer() {
        httpServer.stop()
    }

    // --- Encrypted Vault ---

    fun hasVaultPin(): Boolean = preferences.value.vaultPinHash.isNotEmpty()

    fun setupOrUnlockVault(pin: String): Boolean {
        val trimmed = pin.trim()
        if (trimmed.length < 4) {
            emitMessage("PIN / Passphrase must be at least 4 characters")
            return false
        }
        val hashed = vaultManager.hashPin(trimmed)
        val storedHash = preferences.value.vaultPinHash
        return if (storedHash.isEmpty()) {
            viewModelScope.launch { prefsRepo.setVaultPinHash(hashed) }
            _uiState.update { it.copy(unlockedVaultPin = trimmed) }
            emitMessage("Encrypted Vault initialized & unlocked")
            true
        } else if (storedHash == hashed) {
            _uiState.update { it.copy(unlockedVaultPin = trimmed) }
            true
        } else {
            emitMessage("Incorrect Vault PIN")
            false
        }
    }

    fun lockVault() {
        _uiState.update { it.copy(unlockedVaultPin = null) }
    }

    fun lockFilesIntoVault(tabId: String, paths: List<String>) {
        val pin = _uiState.value.unlockedVaultPin
        if (pin == null) {
            openNewTab(kind = TabKind.VAULT)
            emitMessage("Unlock or create your Vault PIN first, then lock files")
            return
        }
        val regularFiles = paths.filter { File(it).isFile }
        if (regularFiles.isEmpty()) {
            emitMessage("Select regular files to encrypt into the Vault")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = "Encrypting ${regularFiles.size} file(s) with AES-256-GCM...") }
            var success = 0
            for (p in regularFiles) {
                if (vaultManager.lockFileIntoVault(p, pin).isSuccess) success++
            }
            _uiState.update { it.copy(busyMessage = null) }
            clearSelection(tabId)
            refreshAllDirectoryTabs()
            emitMessage("Encrypted $success file(s) into Hidden Vault")
        }
    }

    fun restoreFromVault(entry: VaultEntryEntity) {
        val pin = _uiState.value.unlockedVaultPin ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = "Decrypting ${entry.originalName}...") }
            val res = vaultManager.restoreFromVault(entry, pin)
            _uiState.update { it.copy(busyMessage = null) }
            res.fold(
                onSuccess = { restored ->
                    refreshAllDirectoryTabs()
                    emitMessage("Decrypted & restored ${restored.name}")
                },
                onFailure = { emitMessage("Decryption failed: ${it.message}") }
            )
        }
    }

    fun deleteVaultEntry(entry: VaultEntryEntity) {
        viewModelScope.launch {
            vaultManager.deleteVaultEntryPermanently(entry)
            emitMessage("Permanently destroyed ${entry.originalName}")
        }
    }

    // --- App Inspector ---

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = FileSystemEngine.listLaunchableApps(appContext)
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun backupAppApk(app: InstalledAppInfo) {
        val targetDir = File(FileSystemEngine.getDefaultRootPath(appContext), "APK_Backups").absolutePath
        viewModelScope.launch {
            _uiState.update { it.copy(busyMessage = "Backing up ${app.label}...") }
            val res = FileSystemEngine.backupApk(app, targetDir)
            _uiState.update { it.copy(busyMessage = null) }
            res.fold(
                onSuccess = {
                    refreshAllDirectoryTabs()
                    emitMessage("Backed up APK to ${it.absolutePath}")
                },
                onFailure = { emitMessage("Backup failed: ${it.message}") }
            )
        }
    }

    fun launchApp(packageName: String) {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } else {
            emitMessage("Unable to launch $packageName")
        }
    }

    fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    // --- Properties & Built-in Viewers ---

    fun openProperties(node: FileNode) {
        viewModelScope.launch {
            val props = FileSystemEngine.computeProperties(node)
            _uiState.update {
                it.copy(
                    inspectingProperties = props,
                    inspectingHashes = null,
                    isComputingHashes = false
                )
            }
        }
    }

    fun computePropertiesHashes() {
        val props = _uiState.value.inspectingProperties ?: return
        if (props.node.isDirectory) return
        viewModelScope.launch {
            _uiState.update { it.copy(isComputingHashes = true) }
            val res = FileSystemEngine.computeHashes(props.node.file)
            _uiState.update {
                it.copy(
                    isComputingHashes = false,
                    inspectingHashes = res.getOrNull()
                )
            }
            res.onFailure { emitMessage("Hash error: ${it.message}") }
        }
    }

    fun closeProperties() {
        _uiState.update { it.copy(inspectingProperties = null, inspectingHashes = null, isComputingHashes = false) }
    }

    fun openFileSmart(node: FileNode, forcedMode: ViewerMode? = null) {
        if (node.isDirectory) return
        val mode = forcedMode ?: FileSystemEngine.suggestViewerMode(node) ?: ViewerMode.HEX_VIEWER
        _uiState.update {
            it.copy(
                activeViewer = ActiveViewerSession(
                    filePath = node.path,
                    fileName = node.name,
                    mode = mode
                )
            )
        }
    }

    fun switchViewerMode(mode: ViewerMode) {
        _uiState.update { state ->
            state.copy(activeViewer = state.activeViewer?.copy(mode = mode))
        }
    }

    fun closeViewer() {
        _uiState.update { it.copy(activeViewer = null) }
    }

    fun openExternally(file: File) {
        FileSystemEngine.openExternally(appContext, file)
            .onFailure { emitMessage("No external app found to open ${file.name}") }
    }

    fun sharePaths(paths: List<String>) {
        FileSystemEngine.shareFiles(appContext, paths.map { File(it) })
            .onFailure { emitMessage(it.message ?: "Unable to share files") }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    override fun onCleared() {
        super.onCleared()
        httpServer.stop()
    }

    private fun labelForPath(path: String): String {
        return when (path.trimEnd('/')) {
            "", "/" -> "Root /"
            "/sdcard", "/storage/emulated/0" -> "Internal"
            else -> File(path).name.ifEmpty { path }
        }
    }

    private data class TabSpec(val kind: TabKind, val title: String, val path: String)

    private fun serializeTabs(specs: List<TabSpec>): String {
        val arr = JSONArray()
        specs.forEach { s ->
            arr.put(JSONObject().apply {
                put("kind", s.kind.name)
                put("title", s.title)
                put("path", s.path)
            })
        }
        return arr.toString()
    }

    private fun deserializeTabs(raw: String): List<TabSpec> {
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val kind = runCatching { TabKind.valueOf(obj.getString("kind")) }
                        .getOrDefault(TabKind.DIRECTORY)
                    add(TabSpec(kind, obj.optString("title", "Tab"), obj.optString("path", "/sdcard")))
                }
            }
        }.getOrDefault(emptyList())
    }
}
