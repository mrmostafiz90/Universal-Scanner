package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.FileStorageManager
import com.example.data.local.DocumentEntity
import com.example.localization.LocalStrings
import com.example.ui.theme.SemanticFavorite
import com.example.ui.theme.SemanticStar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridCard(
    doc: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isTrashMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleStar: () -> Unit,
    onEditPages: () -> Unit,
    onOcr: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}")
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (doc.thumbnailPath != null && File(doc.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(doc.thumbnailPath),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = doc.title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Page count badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = strings.homePagesCount(doc.pageCount),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (doc.isEncrypted || doc.isPdfPasswordProtected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = strings.docsEncryptedBadge,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(14.dp)
                        )
                    }
                }

                if (isSelectionMode) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                    )
                } else {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("doc_menu_${doc.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = doc.title,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DocContextMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            isTrashMode = isTrashMode,
                            onEditPages = onEditPages,
                            onOcr = onOcr,
                            onShare = onShare,
                            onRename = onRename,
                            onMove = onMove,
                            onDuplicate = onDuplicate,
                            onInfo = onInfo,
                            onDelete = onDelete,
                            onRestore = onRestore
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FileStorageManager.formatBytes(doc.totalSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isTrashMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (doc.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = strings.docsFavorites,
                                    tint = if (doc.isFavorite) SemanticFavorite else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onToggleStar,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = strings.docsStarred,
                                    tint = if (doc.isStarred) SemanticStar else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListCard(
    doc: DocumentEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isTrashMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleStar: () -> Unit,
    onEditPages: () -> Unit,
    onOcr: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(doc.updatedAt) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(doc.updatedAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_list_card_${doc.id}")
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (doc.thumbnailPath != null && File(doc.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(doc.thumbnailPath),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = doc.title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (doc.isEncrypted || doc.isPdfPasswordProtected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = strings.docsEncryptedBadge,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${strings.homePagesCount(doc.pageCount)}  •  ${FileStorageManager.formatBytes(doc.totalSizeBytes)}  •  $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelectionMode) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            } else {
                if (!isTrashMode) {
                    IconButton(onClick = onToggleStar, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = strings.docsStarred,
                            tint = if (doc.isStarred) SemanticStar else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = doc.title)
                    }
                    DocContextMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        isTrashMode = isTrashMode,
                        onEditPages = onEditPages,
                        onOcr = onOcr,
                        onShare = onShare,
                        onRename = onRename,
                        onMove = onMove,
                        onDuplicate = onDuplicate,
                        onInfo = onInfo,
                        onDelete = onDelete,
                        onRestore = onRestore
                    )
                }
            }
        }
    }
}

@Composable
private fun DocContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isTrashMode: Boolean,
    onEditPages: () -> Unit,
    onOcr: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    val strings = LocalStrings.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isTrashMode) {
            DropdownMenuItem(text = { Text(strings.docsRestore) }, onClick = { onDismiss(); onRestore() })
            DropdownMenuItem(text = { Text(strings.docsDeleteForever) }, onClick = { onDismiss(); onDelete() })
        } else {
            DropdownMenuItem(text = { Text(strings.editorTitle) }, onClick = { onDismiss(); onEditPages() })
            DropdownMenuItem(text = { Text(strings.editorRunOcr) }, onClick = { onDismiss(); onOcr() })
            DropdownMenuItem(text = { Text(strings.share) }, onClick = { onDismiss(); onShare() })
            DropdownMenuItem(text = { Text(strings.rename) }, onClick = { onDismiss(); onRename() })
            DropdownMenuItem(text = { Text(strings.docsMoveTo) }, onClick = { onDismiss(); onMove() })
            DropdownMenuItem(text = { Text(strings.docsCopyTo) }, onClick = { onDismiss(); onDuplicate() })
            DropdownMenuItem(text = { Text(strings.docsInfo) }, onClick = { onDismiss(); onInfo() })
            DropdownMenuItem(text = { Text(strings.delete) }, onClick = { onDismiss(); onDelete() })
        }
    }
}
