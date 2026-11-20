package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.FileStorageManager
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.model.SortOrder
import com.example.data.model.ViewMode
import com.example.localization.LocalStrings
import com.example.ui.components.DocumentGridCard
import com.example.ui.components.DocumentListCard
import com.example.ui.viewmodel.DocLibraryTab
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentsScreen(
    viewModel: MainViewModel,
    onOpenDocumentPdf: (String) -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onOpenOcr: (String) -> Unit,
    onShareDoc: (DocumentEntity) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeDocs by viewModel.activeDocuments.collectAsStateWithLifecycle()
    val deletedDocs by viewModel.deletedDocuments.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var renamingDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var infoDoc by remember { mutableStateOf<DocumentEntity?>(null) }
    var moveDocIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingDeleteIds by remember { mutableStateOf<List<String>?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val baseList = when (uiState.libraryTab) {
        DocLibraryTab.TRASH -> deletedDocs
        DocLibraryTab.FAVORITES -> activeDocs.filter { it.isFavorite }
        DocLibraryTab.STARRED -> activeDocs.filter { it.isStarred }
        DocLibraryTab.RECENT -> activeDocs.sortedByDescending { it.updatedAt }.take(20)
        DocLibraryTab.ALL -> activeDocs
    }

    val folderFiltered = if (uiState.libraryTab == DocLibraryTab.ALL && uiState.selectedFolderId != null) {
        baseList.filter { it.folderId == uiState.selectedFolderId }
    } else {
        baseList
    }

    val q = uiState.searchQuery.trim()
    val searched = if (q.isEmpty()) folderFiltered else {
        folderFiltered.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.ocrSummaryText.contains(q, ignoreCase = true)
        }
    }

    val displayedDocs = remember(searched, uiState.sortOrder) {
        when (uiState.sortOrder) {
            SortOrder.MODIFIED_DESC -> searched.sortedByDescending { it.updatedAt }
            SortOrder.DATE_DESC -> searched.sortedByDescending { it.createdAt }
            SortOrder.NAME_ASC -> searched.sortedBy { it.title.lowercase() }
            SortOrder.SIZE_DESC -> searched.sortedByDescending { it.totalSizeBytes }
        }
    }

    val isSelectionMode = uiState.selectedDocIds.isNotEmpty()
    val isTrash = uiState.libraryTab == DocLibraryTab.TRASH

    Column(modifier = Modifier.fillMaxSize()) {
        // Multi-select toolbar OR Search & controls
        if (isSelectionMode) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = strings.clearSelection)
                        }
                        Text(
                            text = strings.docsSelectedCount(uiState.selectedDocIds.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row {
                        IconButton(onClick = { viewModel.selectAllDocs(displayedDocs.map { it.id }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = strings.selectAll)
                        }
                        if (isTrash) {
                            IconButton(onClick = { viewModel.restoreDocuments(uiState.selectedDocIds.toList()) }) {
                                Icon(Icons.Filled.Restore, contentDescription = strings.docsRestore)
                            }
                            IconButton(onClick = { viewModel.hardDelete(uiState.selectedDocIds.toList()) }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.docsDeleteForever)
                            }
                        } else {
                            IconButton(onClick = {
                                val selected = displayedDocs.filter { it.id in uiState.selectedDocIds }
                                viewModel.shareBulkDocuments(context, selected, strings.docsBulkExportPdf)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = strings.docsBulkExportPdf)
                            }
                            IconButton(onClick = { moveDocIds = uiState.selectedDocIds.toList() }) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = strings.docsMoveTo)
                            }
                            IconButton(onClick = { pendingDeleteIds = uiState.selectedDocIds.toList() }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.delete)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(strings.docsSearchHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = strings.search) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = strings.close)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("docs_search_input")
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = { viewModel.toggleViewMode() }, modifier = Modifier.testTag("toggle_view_mode_btn")) {
                    Icon(
                        imageVector = if (uiState.viewMode == ViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (uiState.viewMode == ViewMode.GRID) strings.docsListView else strings.docsGridView
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.testTag("sort_docs_btn")) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = strings.docsSortBy)
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(strings.sortLabel(order)) },
                                onClick = { sortMenuOpen = false; viewModel.setSortOrder(order) }
                            )
                        }
                    }
                }
            }
        }

        // Tabs
        val tabs = listOf(
            DocLibraryTab.ALL to strings.docsAll,
            DocLibraryTab.RECENT to strings.docsRecent,
            DocLibraryTab.FAVORITES to strings.docsFavorites,
            DocLibraryTab.STARRED to strings.docsStarred,
            DocLibraryTab.TRASH to strings.docsTrash
        )
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == uiState.libraryTab }.coerceAtLeast(0),
            edgePadding = 12.dp
        ) {
            tabs.forEach { (tab, label) ->
                Tab(
                    selected = uiState.libraryTab == tab,
                    onClick = { viewModel.setLibraryTab(tab) },
                    text = { Text(label) }
                )
            }
        }

        // Folders row (when in ALL tab)
        if (uiState.libraryTab == DocLibraryTab.ALL) {
            val childFolders = folders.filter { it.parentFolderId == uiState.selectedFolderId }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedFolderId == null,
                        onClick = { viewModel.selectFolder(null) },
                        label = { Text(strings.docsRootFolder) }
                    )
                }
                items(childFolders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = uiState.selectedFolderId == folder.id,
                        onClick = { viewModel.selectFolder(folder.id) },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text(folder.name) }
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showCreateFolderDialog = true },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        label = { Text(strings.docsNewFolder) },
                        modifier = Modifier.testTag("new_folder_chip")
                    )
                }
            }
        }

        if (isTrash && displayedDocs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.emptyTrash() }) {
                    Text(strings.docsEmptyTrash, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (displayedDocs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isTrash) strings.docsTrashEmptyMsg else strings.homeNoDocsTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (uiState.viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedDocs, key = { it.id }) { doc ->
                    val selected = doc.id in uiState.selectedDocIds
                    DocumentGridCard(
                        doc = doc,
                        isSelected = selected,
                        isSelectionMode = isSelectionMode,
                        isTrashMode = isTrash,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleDocSelection(doc.id)
                            else if (!isTrash) onOpenDocumentPdf(doc.id)
                        },
                        onLongClick = { viewModel.toggleDocSelection(doc.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                        onToggleStar = { viewModel.toggleStarred(doc) },
                        onEditPages = { onOpenPageEditor(doc.id) },
                        onOcr = { onOpenOcr(doc.id) },
                        onShare = { onShareDoc(doc) },
                        onRename = { renamingDoc = doc },
                        onMove = { moveDocIds = listOf(doc.id) },
                        onDuplicate = { viewModel.duplicateDocument(doc.id) },
                        onInfo = { infoDoc = doc },
                        onDelete = {
                            if (isTrash) viewModel.hardDelete(listOf(doc.id))
                            else pendingDeleteIds = listOf(doc.id)
                        },
                        onRestore = { viewModel.restoreDocuments(listOf(doc.id)) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedDocs, key = { it.id }) { doc ->
                    val selected = doc.id in uiState.selectedDocIds
                    DocumentListCard(
                        doc = doc,
                        isSelected = selected,
                        isSelectionMode = isSelectionMode,
                        isTrashMode = isTrash,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleDocSelection(doc.id)
                            else if (!isTrash) onOpenDocumentPdf(doc.id)
                        },
                        onLongClick = { viewModel.toggleDocSelection(doc.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(doc) },
                        onToggleStar = { viewModel.toggleStarred(doc) },
                        onEditPages = { onOpenPageEditor(doc.id) },
                        onOcr = { onOpenOcr(doc.id) },
                        onShare = { onShareDoc(doc) },
                        onRename = { renamingDoc = doc },
                        onMove = { moveDocIds = listOf(doc.id) },
                        onDuplicate = { viewModel.duplicateDocument(doc.id) },
                        onInfo = { infoDoc = doc },
                        onDelete = {
                            if (isTrash) viewModel.hardDelete(listOf(doc.id))
                            else pendingDeleteIds = listOf(doc.id)
                        },
                        onRestore = { viewModel.restoreDocuments(listOf(doc.id)) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(strings.docsNewFolder) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.docsFolderNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createFolder(name, uiState.selectedFolderId)
                    showCreateFolderDialog = false
                }) { Text(strings.create) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    renamingDoc?.let { doc ->
        var title by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renamingDoc = null },
            title = { Text(strings.rename) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.docsDocumentNameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameDocument(doc.id, title)
                    renamingDoc = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingDoc = null }) { Text(strings.cancel) }
            }
        )
    }

    pendingDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text(strings.docsConfirmDeleteTitle) },
            text = { Text(strings.docsConfirmDeleteMsg) },
            confirmButton = {
                Button(onClick = {
                    viewModel.softDelete(ids)
                    pendingDeleteIds = null
                }) { Text(strings.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) { Text(strings.cancel) }
            }
        )
    }

    moveDocIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { moveDocIds = null },
            title = { Text(strings.docsMoveTo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        viewModel.moveDocuments(ids, null)
                        moveDocIds = null
                    }) { Text(strings.docsRootFolder) }
                    folders.forEach { f ->
                        TextButton(onClick = {
                            viewModel.moveDocuments(ids, f.id)
                            moveDocIds = null
                        }) { Text(f.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveDocIds = null }) { Text(strings.cancel) }
            }
        )
    }

    infoDoc?.let { doc ->
        val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { infoDoc = null },
            title = { Text(strings.docsInfo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${strings.pdfDocTitle}: ${doc.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("${strings.docsPagesLabel}: ${doc.pageCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("${strings.docsSize}: ${FileStorageManager.formatBytes(doc.totalSizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${strings.docsCreated}: ${fmt.format(Date(doc.createdAt))}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.docsModified}: ${fmt.format(Date(doc.updatedAt))}", style = MaterialTheme.typography.bodySmall)
                    if (doc.ocrSummaryText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(strings.docsOcrTextLabel, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = doc.ocrSummaryText.take(260),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDoc = null }) { Text(strings.close) }
            }
        )
    }
}
