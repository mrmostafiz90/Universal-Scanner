package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.localization.LocalStrings
import com.example.ui.viewmodel.MainViewModel

private enum class ActiveToolModal {
    MERGE, SPLIT, COMPRESS, EXTRACT, PDF_TO_IMG, PROTECT, WATERMARK
}

private data class ToolTileItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    onOpenDocumentPdf: (String) -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onOpenSignatures: () -> Unit,
    onOpenOcrModels: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val docs by viewModel.activeDocuments.collectAsStateWithLifecycle()

    var activeModal by remember { mutableStateOf<ActiveToolModal?>(null) }

    val imagesToPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryImages(uris) { docId -> onOpenPageEditor(docId) }
        }
    }

    val tools = listOf(
        ToolTileItem("merge", Icons.Filled.MergeType, strings.toolsMergePdf, strings.toolsMergeDesc) {
            activeModal = ActiveToolModal.MERGE
        },
        ToolTileItem("split", Icons.Filled.CallSplit, strings.toolsSplitPdf, strings.toolsSplitDesc) {
            activeModal = ActiveToolModal.SPLIT
        },
        ToolTileItem("compress", Icons.Filled.Compress, strings.toolsCompressPdf, strings.toolsCompressDesc) {
            activeModal = ActiveToolModal.COMPRESS
        },
        ToolTileItem("extract", Icons.Filled.Reorder, strings.toolsExtractPages, strings.toolsExtractDesc) {
            activeModal = ActiveToolModal.EXTRACT
        },
        ToolTileItem("pdf2img", Icons.Filled.Image, strings.toolsPdfToImages, strings.toolsPdfToImagesDesc) {
            activeModal = ActiveToolModal.PDF_TO_IMG
        },
        ToolTileItem("img2pdf", Icons.Filled.AddPhotoAlternate, strings.toolsImagesToPdf, strings.toolsImagesToPdfDesc) {
            imagesToPdfPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        ToolTileItem("protect", Icons.Filled.Lock, strings.toolsProtectPdf, strings.toolsProtectDesc) {
            activeModal = ActiveToolModal.PROTECT
        },
        ToolTileItem("watermark", Icons.Filled.BrandingWatermark, strings.toolsWatermarkPdf, strings.toolsWatermarkDesc) {
            activeModal = ActiveToolModal.WATERMARK
        },
        ToolTileItem("signatures", Icons.Filled.Draw, strings.toolsSignatures, strings.toolsSignaturesDesc, onOpenSignatures),
        ToolTileItem("ocr_models", Icons.Filled.Language, strings.toolsOcrModels, strings.toolsOcrModelsDesc, onOpenOcrModels)
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(strings.toolsTitle, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tool_${item.id}")
                            .clickable(onClick = item.onClick)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2
                            )
                        }
                    }
                }
            }
        }
    }

    // Tool Dialogs
    when (activeModal) {
        ActiveToolModal.MERGE -> {
            val selected = remember { mutableStateListOf<DocumentEntity>() }
            var title by remember { mutableStateOf("Merged Document") }
            AlertDialog(
                onDismissRequest = { activeModal = null },
                title = { Text(strings.toolsMergePdf) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(strings.pdfDocTitle) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(strings.toolsSelectDocs, style = MaterialTheme.typography.labelLarge)
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(docs, key = { it.id }) { d ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (d in selected) selected.remove(d) else selected.add(d)
                                        }
                                ) {
                                    Checkbox(checked = d in selected, onCheckedChange = {
                                        if (it) selected.add(d) else selected.remove(d)
                                    })
                                    Text(d.title, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = selected.size >= 2,
                        onClick = {
                            val list = selected.toList()
                            activeModal = null
                            viewModel.mergeSelectedPdfs(list, title) { id ->
                                viewModel.postMessage(strings.toolsMergeSuccess)
                                onOpenDocumentPdf(id)
                            }
                        }
                    ) { Text(strings.apply) }
                },
                dismissButton = {
                    TextButton(onClick = { activeModal = null }) { Text(strings.cancel) }
                }
            )
        }
        ActiveToolModal.SPLIT -> {
            SingleDocToolDialog(
                title = strings.toolsSplitPdf,
                docs = docs,
                onDismiss = { activeModal = null }
            ) { doc, extra1, _, _ ->
                viewModel.splitPdf(doc, extra1.ifBlank { "1" }, "${doc.title} ($extra1)") { id ->
                    viewModel.postMessage(strings.toolsSplitSuccess)
                    onOpenDocumentPdf(id)
                }
            }
        }
        ActiveToolModal.COMPRESS -> {
            SingleDocToolDialog(
                title = strings.toolsCompressPdf,
                docs = docs,
                showQualitySlider = true,
                onDismiss = { activeModal = null }
            ) { doc, _, quality, _ ->
                viewModel.compressPdf(doc, quality) { id ->
                    viewModel.postMessage(strings.toolsCompressSuccess("$quality%"))
                    onOpenDocumentPdf(id)
                }
            }
        }
        ActiveToolModal.EXTRACT -> {
            SingleDocToolDialog(
                title = strings.toolsExtractPages,
                docs = docs,
                showExtraInput = false,
                onDismiss = { activeModal = null }
            ) { doc, _, _, _ ->
                onOpenPageEditor(doc.id)
            }
        }
        ActiveToolModal.PDF_TO_IMG -> {
            SingleDocToolDialog(
                title = strings.toolsPdfToImages,
                docs = docs,
                showExtraInput = false,
                onDismiss = { activeModal = null }
            ) { doc, _, _, _ ->
                viewModel.shareDocumentAsImages(context, doc, asPng = false, chooserTitle = strings.toolsPdfToImages)
            }
        }
        ActiveToolModal.PROTECT -> {
            SingleDocToolDialog(
                title = strings.toolsProtectPdf,
                docs = docs,
                isPasswordField = true,
                onDismiss = { activeModal = null }
            ) { doc, password, _, _ ->
                if (password.isNotBlank()) {
                    viewModel.protectOrWatermarkPdf(doc, "", false, password) { id -> onOpenDocumentPdf(id) }
                }
            }
        }
        ActiveToolModal.WATERMARK -> {
            SingleDocToolDialog(
                title = strings.toolsWatermarkPdf,
                docs = docs,
                showPageNumSwitch = true,
                onDismiss = { activeModal = null }
            ) { doc, wmText, _, pageNums ->
                viewModel.protectOrWatermarkPdf(doc, wmText, pageNums, "") { id -> onOpenDocumentPdf(id) }
            }
        }
        null -> Unit
    }
}

@Composable
private fun SingleDocToolDialog(
    title: String,
    docs: List<DocumentEntity>,
    showExtraInput: Boolean = true,
    isPasswordField: Boolean = false,
    showQualitySlider: Boolean = false,
    showPageNumSwitch: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (DocumentEntity, String, Int, Boolean) -> Unit
) {
    val strings = LocalStrings.current
    var selectedDoc by remember { mutableStateOf(docs.firstOrNull()) }
    var extraText by remember { mutableStateOf(if (isPasswordField || showPageNumSwitch) "" else "1-2") }
    var quality by remember { mutableIntStateOf(65) }
    var includePageNumbers by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (docs.isEmpty()) {
                    Text(strings.homeNoDocsTitle)
                } else {
                    LazyColumn(modifier = Modifier.height(170.dp)) {
                        items(docs, key = { it.id }) { doc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDoc = doc }
                            ) {
                                RadioButton(selected = selectedDoc?.id == doc.id, onClick = { selectedDoc = doc })
                                Text("${doc.title} (${doc.pageCount}p)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (showExtraInput && !showQualitySlider) {
                        OutlinedTextField(
                            value = extraText,
                            onValueChange = { extraText = it },
                            label = {
                                Text(
                                    when {
                                        isPasswordField -> strings.pdfPasswordHint
                                        showPageNumSwitch -> strings.pdfWatermarkHint
                                        else -> strings.toolsPageRangeHint
                                    }
                                )
                            },
                            visualTransformation = if (isPasswordField) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (showQualitySlider) {
                        Text("${strings.toolsTargetQuality}: $quality%", style = MaterialTheme.typography.labelMedium)
                        Slider(value = quality.toFloat(), onValueChange = { quality = it.toInt() }, valueRange = 30f..90f)
                    }
                    if (showPageNumSwitch) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.pdfPageNumbers, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = includePageNumbers, onCheckedChange = { includePageNumbers = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedDoc != null,
                onClick = {
                    val d = selectedDoc ?: return@Button
                    onDismiss()
                    onConfirm(d, extraText, quality, includePageNumbers)
                }
            ) { Text(strings.apply) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
