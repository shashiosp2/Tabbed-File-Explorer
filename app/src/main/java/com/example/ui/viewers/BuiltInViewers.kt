package com.example.ui.viewers

import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.engine.ActiveViewerSession
import com.example.engine.ArchiveEntryInfo
import com.example.engine.ArchiveManager
import com.example.engine.FileSystemEngine
import com.example.engine.ViewerMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInViewerOverlay(
    session: ActiveViewerSession,
    onSwitchMode: (ViewerMode) -> Unit,
    onClose: () -> Unit,
    onOpenExternal: (File) -> Unit,
    onExtractArchive: (zipPath: String, destDir: String) -> Unit,
    onEmitMessage: (String) -> Unit
) {
    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.mode.name.replace('_', ' '),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_close_viewer))
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenExternal(File(session.filePath)) }) {
                        Icon(Icons.Outlined.OpenInNew, "Open with external app")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Switcher Bar (allows inspecting any file as Text, Hex, JSON, CSV, Markdown, SQLite, Archive)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                val modes = listOf(
                    ViewerMode.TEXT_EDITOR to "Text/Code",
                    ViewerMode.HEX_VIEWER to "Hex",
                    ViewerMode.JSON_TREE to "JSON Tree",
                    ViewerMode.CSV_TABLE to "CSV Grid",
                    ViewerMode.MARKDOWN to "Markdown",
                    ViewerMode.SQLITE_DB to "SQLite DB",
                    ViewerMode.IMAGE to "Image",
                    ViewerMode.ARCHIVE to "ZIP Archive"
                )
                items(modes) { (m, label) ->
                    FilterChip(
                        selected = session.mode == m,
                        onClick = { onSwitchMode(m) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            HorizontalDivider()

            when (session.mode) {
                ViewerMode.TEXT_EDITOR -> TextAndCodeEditorView(session.filePath, onEmitMessage)
                ViewerMode.HEX_VIEWER -> BinaryHexViewerView(session.filePath)
                ViewerMode.JSON_TREE -> JsonTreeViewerView(session.filePath)
                ViewerMode.CSV_TABLE -> CsvTableViewerView(session.filePath)
                ViewerMode.MARKDOWN -> MarkdownPreviewView(session.filePath) {
                    onSwitchMode(ViewerMode.TEXT_EDITOR)
                }
                ViewerMode.SQLITE_DB -> SqliteDatabaseViewerView(session.filePath)
                ViewerMode.IMAGE -> PinchZoomImageViewer(session.filePath)
                ViewerMode.ARCHIVE -> ZipArchiveInspectorView(
                    zipPath = session.filePath,
                    onExtractAll = {
                        val file = File(session.filePath)
                        val dest = File(file.parentFile, file.nameWithoutExtension).absolutePath
                        onExtractArchive(session.filePath, dest)
                    }
                )
            }
        }
    }
}

@Composable
private fun TextAndCodeEditorView(
    filePath: String,
    onEmitMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var encodingName by remember { mutableStateOf("UTF-8") }
    var encodingMenuExpanded by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(true) }
    var content by remember(filePath) { mutableStateOf("") }
    var isDirty by remember(filePath) { mutableStateOf(false) }
    var isLoading by remember(filePath) { mutableStateOf(true) }
    var errorMsg by remember(filePath) { mutableStateOf<String?>(null) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showFindBar by remember { mutableStateOf(false) }

    LaunchedEffect(filePath, encodingName) {
        isLoading = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(filePath)
                if (f.length() > 4 * 1024 * 1024) {
                    error("File is larger than 4 MB (${FileSystemEngine.formatSize(f.length())}). Switch to Hex Viewer for binary/large files.")
                }
                f.readText(Charset.forName(encodingName))
            }
        }.fold(
            onSuccess = { text ->
                content = text
                isDirty = false
                isLoading = false
            },
            onFailure = { err ->
                errorMsg = err.message
                isLoading = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Editor Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box {
                TextButton(onClick = { encodingMenuExpanded = true }) {
                    Text(encodingName, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = encodingMenuExpanded, onDismissRequest = { encodingMenuExpanded = false }) {
                    listOf("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII").forEach { enc ->
                        DropdownMenuItem(
                            text = { Text(enc) },
                            onClick = { encodingName = enc; encodingMenuExpanded = false }
                        )
                    }
                }
            }
            TextButton(onClick = { showFindBar = !showFindBar }) {
                Text("Find/Replace", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { wordWrap = !wordWrap }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.WrapText,
                    "Toggle wrap",
                    tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            runCatching { File(filePath).writeText(content, Charset.forName(encodingName)) }
                        }
                        res.fold(
                            onSuccess = {
                                isDirty = false
                                onEmitMessage("Saved ${File(filePath).name}")
                            },
                            onFailure = { onEmitMessage("Save failed: ${it.message}") }
                        )
                    }
                },
                enabled = isDirty,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Save, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isDirty) "Save*" else "Saved", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (showFindBar) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = { replaceQuery = it },
                    label = { Text("Replace") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (findQuery.isNotEmpty()) {
                            content = content.replace(findQuery, replaceQuery)
                            isDirty = true
                        }
                    },
                    enabled = findQuery.isNotEmpty()
                ) { Text("All") }
            }
        }

        HorizontalDivider()

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            errorMsg != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val linesCount = remember(content) { content.count { it == '\n' } + 1 }
                val scrollVert = rememberScrollState()
                val scrollHoriz = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollVert)
                        .padding(10.dp)
                ) {
                    // Line number gutter
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            text = (1..linesCount).joinToString("\n"),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    BasicTextField(
                        value = content,
                        onValueChange = {
                            content = it
                            isDirty = true
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (!wordWrap) Modifier.horizontalScroll(scrollHoriz) else Modifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun BinaryHexViewerView(filePath: String) {
    val pageSize = 2048 // 2 KB per page = 256 rows of 8 bytes
    var fileSize by remember(filePath) { mutableLongStateOf(0L) }
    var offset by remember(filePath) { mutableLongStateOf(0L) }
    var pageBytes by remember(filePath) { mutableStateOf(ByteArray(0)) }
    var jumpOffsetInput by remember { mutableStateOf("") }

    LaunchedEffect(filePath, offset) {
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(filePath)
                fileSize = f.length()
                val safeOffset = offset.coerceIn(0L, (fileSize - 1).coerceAtLeast(0L))
                RandomAccessFile(f, "r").use { raf ->
                    raf.seek(safeOffset)
                    val buf = ByteArray(pageSize)
                    val read = raf.read(buf).coerceAtLeast(0)
                    buf.copyOf(read)
                }
            }.getOrDefault(ByteArray(0))
        }.let { pageBytes = it }
    }

    val rows = remember(pageBytes, offset) {
        val list = mutableListOf<Triple<String, String, String>>()
        var i = 0
        while (i < pageBytes.size) {
            val end = minOf(i + 8, pageBytes.size)
            val addr = "%08X".format(offset + i)
            val hex = (i until end).joinToString(" ") { "%02X".format(pageBytes[it].toInt() and 0xFF) }
                .padEnd(23, ' ')
            val ascii = (i until end).map {
                val c = pageBytes[it].toInt() and 0xFF
                if (c in 32..126) c.toChar() else '.'
            }.joinToString("")
            list.add(Triple(addr, hex, ascii))
            i += 8
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            OutlinedButton(
                onClick = { offset = (offset - pageSize).coerceAtLeast(0L) },
                enabled = offset > 0L,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("Prev") }
            OutlinedButton(
                onClick = { if (offset + pageSize < fileSize) offset += pageSize },
                enabled = offset + pageSize < fileSize,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("Next") }
            OutlinedTextField(
                value = jumpOffsetInput,
                onValueChange = { jumpOffsetInput = it.filter { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' } },
                placeholder = { Text("Hex offset", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val target = jumpOffsetInput.toLongOrNull(16) ?: return@Button
                    offset = target.coerceIn(0L, (fileSize - 1).coerceAtLeast(0L))
                },
                enabled = jumpOffsetInput.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) { Text("Jump") }
        }
        Text(
            text = "Offset: 0x%08X / %s (%d bytes)".format(offset, FileSystemEngine.formatSize(fileSize), fileSize),
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        HorizontalDivider()
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            items(rows) { (addr, hex, ascii) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    Text(addr, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(hex, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(ascii, fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

private data class JsonNodeRow(
    val id: String,
    val depth: Int,
    val key: String,
    val valuePreview: String,
    val isContainer: Boolean,
    val childCount: Int
)

@Composable
private fun JsonTreeViewerView(filePath: String) {
    var parsedRoot by remember(filePath) { mutableStateOf<Any?>(null) }
    var parseError by remember(filePath) { mutableStateOf<String?>(null) }
    val expandedMap = remember(filePath) { mutableStateMapOf<String, Boolean>("root" to true) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val text = File(filePath).readText()
                JSONTokener(text).nextValue()
            }
        }.fold(
            onSuccess = { parsedRoot = it; parseError = null },
            onFailure = { parseError = it.message ?: "Invalid JSON" }
        )
    }

    if (parseError != null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("JSON Parse Error: $parseError", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val flatRows = remember(parsedRoot, expandedMap.toMap()) {
        val list = mutableListOf<JsonNodeRow>()
        fun walk(id: String, key: String, node: Any?, depth: Int) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys().asSequence().toList()
                    list.add(JsonNodeRow(id, depth, key, "{ ${keys.size} keys }", true, keys.size))
                    if (expandedMap[id] == true) {
                        keys.forEach { k -> walk("$id.$k", k, node.opt(k), depth + 1) }
                    }
                }
                is JSONArray -> {
                    list.add(JsonNodeRow(id, depth, key, "[ ${node.length()} items ]", true, node.length()))
                    if (expandedMap[id] == true) {
                        for (i in 0 until node.length()) {
                            walk("$id[$i]", "[$i]", node.opt(i), depth + 1)
                        }
                    }
                }
                else -> {
                    val display = when (node) {
                        null, JSONObject.NULL -> "null"
                        is String -> "\"$node\""
                        else -> node.toString()
                    }
                    list.add(JsonNodeRow(id, depth, key, display, false, 0))
                }
            }
        }
        if (parsedRoot != null) walk("root", "root", parsedRoot, 0)
        list
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(flatRows, key = { it.id }) { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = row.isContainer) {
                        expandedMap[row.id] = !(expandedMap[row.id] ?: false)
                    }
                    .padding(start = (row.depth * 16).dp, top = 4.dp, bottom = 4.dp)
            ) {
                if (row.isContainer) {
                    Icon(
                        imageVector = if (expandedMap[row.id] == true) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                } else {
                    Spacer(Modifier.width(20.dp))
                }
                Text(
                    text = "${row.key}: ",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = row.valuePreview,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isContainer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CsvTableViewerView(filePath: String) {
    var rows by remember(filePath) { mutableStateOf<List<List<String>>>(emptyList()) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val lines = File(filePath).readLines().take(500)
                val delimiter = when {
                    filePath.endsWith(".tsv", true) -> '\t'
                    lines.firstOrNull()?.count { it == '\t' } ?: 0 > (lines.firstOrNull()?.count { it == ',' } ?: 0) -> '\t'
                    lines.firstOrNull()?.count { it == ';' } ?: 0 > (lines.firstOrNull()?.count { it == ',' } ?: 0) -> ';'
                    else -> ','
                }
                lines.map { line -> parseCsvLine(line, delimiter) }
            }.getOrDefault(emptyList())
        }.let { rows = it }
    }

    val header = rows.firstOrNull() ?: emptyList()
    val dataRows = remember(rows, filter) {
        val body = rows.drop(1)
        if (filter.isBlank()) body
        else body.filter { r -> r.any { it.contains(filter, ignoreCase = true) } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Filter ${rows.size} rows...") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            if (header.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        header.forEach { cell ->
                            Text(
                                text = cell,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .width(150.dp)
                                    .padding(horizontal = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            itemsIndexed(dataRows) { idx, row ->
                Row(
                    modifier = Modifier
                        .background(
                            if (idx % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .padding(vertical = 6.dp, horizontal = 6.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == delimiter && !inQuotes -> {
                result.add(sb.toString().trim())
                sb.clear()
            }
            else -> sb.append(ch)
        }
    }
    result.add(sb.toString().trim())
    return result
}

@Composable
private fun MarkdownPreviewView(filePath: String, onEditRaw: () -> Unit) {
    var lines by remember(filePath) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(filePath) {
        lines = withContext(Dispatchers.IO) { runCatching { File(filePath).readLines() }.getOrDefault(emptyList()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = onEditRaw) { Text("Edit Markdown Source") }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(lines) { raw ->
                val trimmed = raw.trim()
                when {
                    trimmed.startsWith("### ") -> Text(
                        trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    trimmed.startsWith("## ") -> Text(
                        trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    trimmed.startsWith("# ") -> Text(
                        trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
                        text = "• ${trimmed.drop(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                    trimmed.startsWith("> ") -> Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trimmed.removePrefix("> "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))
                    else -> Text(trimmed, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SqliteDatabaseViewerView(filePath: String) {
    val scope = rememberCoroutineScope()
    var tables by remember(filePath) { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember(filePath) { mutableStateOf<String?>(null) }
    var sqlQuery by remember(filePath) { mutableStateOf("") }
    var columns by remember(filePath) { mutableStateOf<List<String>>(emptyList()) }
    var rows by remember(filePath) { mutableStateOf<List<List<String>>>(emptyList()) }
    var dbError by remember(filePath) { mutableStateOf<String?>(null) }

    fun executeQuery(query: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery(query, null).use { cursor ->
                            val cols = cursor.columnNames.toList()
                            val data = mutableListOf<List<String>>()
                            var count = 0
                            while (cursor.moveToNext() && count < 200) {
                                data.add((0 until cols.size).map { idx ->
                                    runCatching { cursor.getString(idx) ?: "NULL" }.getOrDefault("<BLOB>")
                                })
                                count++
                            }
                            cols to data
                        }
                    }
                }
            }.fold(
                onSuccess = { (cols, data) ->
                    columns = cols
                    rows = data
                    dbError = null
                },
                onFailure = { dbError = it.message }
            )
        }
    }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val names = mutableListOf<String>()
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view') ORDER BY name", null).use { c ->
                        while (c.moveToNext()) names.add(c.getString(0))
                    }
                    names
                }
            }
        }.fold(
            onSuccess = { list ->
                tables = list
                dbError = null
                list.firstOrNull()?.let { first ->
                    selectedTable = first
                    val q = "SELECT * FROM \"$first\" LIMIT 100"
                    sqlQuery = q
                    executeQuery(q)
                }
            },
            onFailure = { dbError = "Cannot open SQLite DB: ${it.message}" }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tables.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                items(tables) { tbl ->
                    FilterChip(
                        selected = selectedTable == tbl,
                        onClick = {
                            selectedTable = tbl
                            val q = "SELECT * FROM \"$tbl\" LIMIT 100"
                            sqlQuery = q
                            executeQuery(q)
                        },
                        label = { Text(tbl, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                label = { Text("SQL Query (Read-Only)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { executeQuery(sqlQuery) }, enabled = sqlQuery.isNotBlank()) {
                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
            }
        }

        if (dbError != null) {
            Text(dbError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            if (columns.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                            .padding(8.dp)
                    ) {
                        columns.forEach { col ->
                            Text(
                                text = col,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(140.dp).padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
            itemsIndexed(rows) { idx, row ->
                Row(
                    modifier = Modifier
                        .background(if (idx % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(140.dp).padding(horizontal = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinchZoomImageViewer(filePath: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
    ) {
        AsyncImage(
            model = File(filePath),
            contentDescription = File(filePath).name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
private fun ZipArchiveInspectorView(
    zipPath: String,
    onExtractAll: () -> Unit
) {
    var entries by remember(zipPath) { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember(zipPath) { mutableStateOf(true) }
    var errorMsg by remember(zipPath) { mutableStateOf<String?>(null) }

    LaunchedEffect(zipPath) {
        isLoading = true
        ArchiveManager.listZipEntries(zipPath).fold(
            onSuccess = { entries = it; errorMsg = null },
            onFailure = { errorMsg = it.message }
        )
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${entries.size} archive entries",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onExtractAll) {
                Icon(Icons.Outlined.Archive, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Extract All")
            }
        }
        HorizontalDivider()
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            errorMsg != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!entry.isDirectory) {
                                    Text(
                                        text = "${FileSystemEngine.formatSize(entry.sizeBytes)} -> ${FileSystemEngine.formatSize(entry.compressedBytes)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
