package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ExplorerTab
import com.example.engine.TabKind
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserTabStrip(
    tabs: List<ExplorerTab>,
    activeTabId: String,
    secondaryTabId: String?,
    dualPaneEnabled: Boolean,
    focusedPane: Int,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onDuplicateTab: (String) -> Unit,
    onMoveTab: (String, Int) -> Unit,
    onCloseOtherTabs: (String) -> Unit,
    onAssignToOtherPane: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    val isPrimaryActive = tab.id == activeTabId
                    val isSecondaryActive = dualPaneEnabled && tab.id == secondaryTabId
                    val isHighlighted = (focusedPane == 0 && isPrimaryActive) ||
                        (focusedPane == 1 && isSecondaryActive) ||
                        (!dualPaneEnabled && isPrimaryActive)

                    var menuExpanded by remember { mutableStateOf(false) }

                    val containerColor by animateColorAsState(
                        targetValue = when {
                            isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                            isPrimaryActive || isSecondaryActive -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        label = "tab_bg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = when {
                            isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                            isPrimaryActive || isSecondaryActive -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "tab_fg"
                    )

                    Box {
                        Surface(
                            color = containerColor,
                            contentColor = contentColor,
                            shape = RoundedCornerShape(12.dp),
                            border = if (isHighlighted) {
                                BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                            } else null,
                            modifier = Modifier
                                .height(40.dp)
                                .widthIn(min = 96.dp, max = 180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = { onSelectTab(tab.id) },
                                    onLongClick = { menuExpanded = true }
                                )
                                .testTag("tab_item_$index")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = iconForTabKind(tab.kind),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (dualPaneEnabled && (isPrimaryActive || isSecondaryActive)) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isPrimaryActive && isSecondaryActive) "A/B"
                                        else if (isPrimaryActive) "A" else "B",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (tabs.size > 1) {
                                    IconButton(
                                        onClick = { onCloseTab(tab.id) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("close_tab_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_close_tab),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(6.dp))
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Duplicate Tab") },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicateTab(tab.id)
                                }
                            )
                            if (dualPaneEnabled) {
                                DropdownMenuItem(
                                    text = { Text("Open in Other Pane") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onAssignToOtherPane(tab.id)
                                    }
                                )
                            }
                            if (index > 0) {
                                DropdownMenuItem(
                                    text = { Text("Move Left") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveTab(tab.id, -1)
                                    }
                                )
                            }
                            if (index < tabs.lastIndex) {
                                DropdownMenuItem(
                                    text = { Text("Move Right") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveTab(tab.id, 1)
                                    }
                                )
                            }
                            if (tabs.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Close Other Tabs") },
                                    onClick = {
                                        menuExpanded = false
                                        onCloseOtherTabs(tab.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            FilledTonalIconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("new_tab_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_new_tab),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BreadcrumbPathBar(
    tab: ExplorerTab,
    isBookmarked: Boolean,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onOpenGoToPath: () -> Unit,
    onCopyPath: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tab.kind != TabKind.DIRECTORY) return

    val scrollState = rememberScrollState()
    LaunchedEffect(tab.path) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val segments = remember(tab.path) {
        val cleaned = tab.path.trimEnd('/')
        if (cleaned.isEmpty()) {
            listOf("/" to "/")
        } else {
            val parts = cleaned.split('/').filter { it.isNotEmpty() }
            buildList {
                add("/" to "/")
                var acc = ""
                for (p in parts) {
                    acc += "/$p"
                    add(p to acc)
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                enabled = tab.backStack.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onNavigateForward,
                enabled = tab.forwardStack.isNotEmpty(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.cd_forward),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("navigate_up_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.cd_navigate_up),
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp)
            ) {
                segments.forEachIndexed { idx, (label, fullPath) ->
                    if (idx > 0) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    val isLast = idx == segments.lastIndex
                    Surface(
                        onClick = { onNavigateToPath(fullPath) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLast) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Toggle bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onCopyPath,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_path),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onOpenGoToPath,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("go_to_path_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = "Go to path",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
