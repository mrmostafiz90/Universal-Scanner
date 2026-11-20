package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.DocumentEntity
import com.example.data.model.AnnotationKind
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfAnnotationItem
import com.example.localization.LocalStrings
import com.example.pdf.PdfPrintAndShareHelper
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerEditorScreen(
    docId: String,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onOpenOcr: (String) -> Unit,
    onOpenSignatureStudio: () -> Unit,
    onShareDoc: (DocumentEntity) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val vm: PdfReaderViewModel = viewModel(
        key = "reader_$docId",
        factory = PdfReaderViewModel.Factory(docId, mainViewModel.container)
    )
    val doc by vm.document.collectAsStateWithLifecycle()
    val signatures by vm.signatures.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.savedBanner, uiState.error) {
        (uiState.savedBanner ?: uiState.error)?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeBanner()
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3.5f)
        panOffset = if (zoomScale > 1.02f) panOffset + panChange else Offset.Zero
    }

    var pendingTextAnnotation by remember { mutableStateOf<Triple<Int, AnnotationKind, Offset>?>(null) }
    var showSignaturePicker by remember { mutableStateOf(false) }
    var showAnnotationBar by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val bookmarkedSet = remember(doc?.bookmarkedPagesCsv) {
        doc?.bookmarkedPagesCsv?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc?.title ?: strings.readerTitle, maxLines = 1)
                        if (uiState.pageCount > 0) {
                            Text(
                                text = strings.editorPageOf(uiState.currentPage + 1, uiState.pageCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleBookmark(uiState.currentPage) }) {
                        val marked = uiState.currentPage in bookmarkedSet
                        Icon(
                            imageVector = if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (marked) strings.readerBookmarked else strings.readerBookmark
                        )
                    }
                    IconButton(onClick = { showAnnotationBar = !showAnnotationBar }, modifier = Modifier.testTag("toggle_annotate_bar")) {
                        Icon(Icons.Filled.Edit, contentDescription = strings.readerFreehand)
                    }
                    IconButton(onClick = { onOpenOcr(docId) }) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = strings.editorRunOcr)
                    }
                    IconButton(onClick = {
                        val act = context as? Activity ?: return@IconButton
                        scope.launch {
                            val d = doc ?: return@launch
                            val file = mainViewModel.container.documentRepository.getReadablePdfFile(d) ?: return@launch
                            PdfPrintAndShareHelper.printPdf(act, file, d.title)
                        }
                    }) {
                        Icon(Icons.Filled.Print, contentDescription = strings.print)
                    }
                    IconButton(onClick = { doc?.let(onShareDoc) }) {
                        Icon(Icons.Filled.Share, contentDescription = strings.share)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Zoom & Mode Quick Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = uiState.isContinuousScroll,
                        onClick = { vm.toggleScrollMode() },
                        leadingIcon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(if (uiState.isContinuousScroll) strings.readerContinuousScroll else strings.readerSinglePage)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onOpenPageEditor(docId) }) {
                        Text(strings.editorTitle)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { zoomScale = (zoomScale - 0.4f).coerceAtLeast(1f); if (zoomScale == 1f) panOffset = Offset.Zero }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                    }
                    Text("${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { zoomScale = (zoomScale + 0.4f).coerceAtMost(3.5f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                    }
                }
            }

            // Annotation Toolbar
            if (showAnnotationBar) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tools = listOf(
                                Triple(AnnotationKind.HIGHLIGHT, Icons.Filled.Highlight, strings.readerHighlight),
                                Triple(AnnotationKind.UNDERLINE, Icons.Filled.FormatUnderlined, strings.readerUnderline),
                                Triple(AnnotationKind.STRIKETHROUGH, Icons.Filled.FormatStrikethrough, strings.readerStrikethrough),
                                Triple(AnnotationKind.FREEHAND, Icons.Filled.Brush, strings.readerFreehand),
                                Triple(AnnotationKind.TEXT_BOX, Icons.Filled.TextFields, strings.readerTextBox),
                                Triple(AnnotationKind.STICKY_NOTE, Icons.Filled.NoteAdd, strings.readerStickyNote),
                                Triple(AnnotationKind.SHAPE_RECT, Icons.Filled.CropSquare, strings.readerShape),
                                Triple(AnnotationKind.SIGNATURE, Icons.Filled.Draw, strings.readerSignature)
                            )
                            items(tools) { (kind, icon, label) ->
                                FilterChip(
                                    selected = uiState.activeTool == kind,
                                    onClick = {
                                        if (kind == AnnotationKind.SIGNATURE) {
                                            showSignaturePicker = true
                                        } else {
                                            vm.selectTool(if (uiState.activeTool == kind) null else kind)
                                        }
                                    },
                                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0xFF0F766E, 0xFFDC2626, 0xFF2563EB, 0xFFFACC15, 0xFF0F172A).forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(col), RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (uiState.activeColorArgb == col) 2.dp else 0.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { vm.selectColor(col) }
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { vm.undo() }, enabled = uiState.undoStack.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.readerUndo)
                                }
                                IconButton(onClick = { vm.redo() }, enabled = uiState.redoStack.isNotEmpty()) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.readerRedo)
                                }
                                Button(
                                    onClick = { vm.saveAnnotatedPdf(strings.readerSaveEdited) },
                                    enabled = uiState.annotations.isNotEmpty() && !uiState.isSaving,
                                    modifier = Modifier.testTag("save_annotated_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.readerSaveEdited)
                                }
                            }
                        }
                    }
                }
            }

            // Selected Signature Resize/Rotate Controls
            val selectedSig = uiState.annotations.firstOrNull {
                it.id == uiState.selectedSignatureId && it.kind == AnnotationKind.SIGNATURE
            }
            if (selectedSig != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(strings.readerTapToPlace, style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.readerSignatureScale, style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = selectedSig.width,
                                onValueChange = { w ->
                                    vm.updateSignatureTransform(selectedSig.id, selectedSig.x, selectedSig.y, w, w * 0.48f, selectedSig.rotationDeg)
                                },
                                valueRange = 0.12f..0.65f,
                                modifier = Modifier.weight(1f)
                            )
                            Text(strings.readerSignatureRotate, style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = selectedSig.rotationDeg,
                                onValueChange = { deg ->
                                    vm.updateSignatureTransform(selectedSig.id, selectedSig.x, selectedSig.y, selectedSig.width, selectedSig.height, deg)
                                },
                                valueRange = -45f..45f,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.removeAnnotation(selectedSig.id) }) {
                                Text(strings.readerRemoveSignature, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // PDF Pages Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (uiState.activeTool == null) Modifier.transformable(transformState) else Modifier
                    )
            ) {
                if (uiState.pageCount == 0) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.isContinuousScroll) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                    ) {
                        items(uiState.pageCount) { pageIdx ->
                            PdfInteractivePageView(
                                pageIndex = pageIdx,
                                renderTick = uiState.renderTick,
                                vm = vm,
                                activeTool = uiState.activeTool,
                                activeColor = uiState.activeColorArgb,
                                annotations = uiState.annotations.filter { it.pageIndex == pageIdx },
                                selectedSignatureId = uiState.selectedSignatureId,
                                onRequestTextEntry = { kind, normPos ->
                                    pendingTextAnnotation = Triple(pageIdx, kind, normPos)
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfInteractivePageView(
                            pageIndex = uiState.currentPage,
                            renderTick = uiState.renderTick,
                            vm = vm,
                            activeTool = uiState.activeTool,
                            activeColor = uiState.activeColorArgb,
                            annotations = uiState.annotations.filter { it.pageIndex == uiState.currentPage },
                            selectedSignatureId = uiState.selectedSignatureId,
                            onRequestTextEntry = { kind, normPos ->
                                pendingTextAnnotation = Triple(uiState.currentPage, kind, normPos)
                            }
                        )
                    }
                }
            }

            // Bottom Page Thumbnail / Navigation Strip
            if (uiState.pageCount > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pageCount) { idx ->
                        val active = idx == uiState.currentPage
                        val marked = idx in bookmarkedSet
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clickable {
                                    vm.setCurrentPage(idx)
                                    if (uiState.isContinuousScroll) {
                                        scope.launch { listState.animateScrollToItem(idx) }
                                    }
                                }
                        ) {
                            Text(
                                text = if (marked) "${idx + 1} ★" else "${idx + 1}",
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Text / Sticky Note Entry Dialog
    pendingTextAnnotation?.let { (pageIdx, kind, normOffset) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextAnnotation = null },
            title = { Text(if (kind == AnnotationKind.STICKY_NOTE) strings.readerStickyNote else strings.readerTextBox) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(strings.readerEnterTextHint) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (text.isNotBlank()) {
                        vm.addAnnotation(
                            PdfAnnotationItem(
                                id = UUID.randomUUID().toString(),
                                pageIndex = pageIdx,
                                kind = kind,
                                x = normOffset.x.coerceIn(0.05f, 0.7f),
                                y = normOffset.y.coerceIn(0.05f, 0.85f),
                                width = 0.28f,
                                height = 0.08f,
                                colorArgb = uiState.activeColorArgb,
                                text = text
                            )
                        )
                    }
                    pendingTextAnnotation = null
                }) { Text(strings.apply) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextAnnotation = null }) { Text(strings.cancel) }
            }
        )
    }

    // Signature Picker Dialog
    if (showSignaturePicker) {
        AlertDialog(
            onDismissRequest = { showSignaturePicker = false },
            title = { Text(strings.sigSavedList) },
            text = {
                if (signatures.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(strings.sigNoSaved)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = {
                            showSignaturePicker = false
                            onOpenSignatureStudio()
                        }) { Text(strings.sigDrawTitle) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        signatures.forEach { sig ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSignaturePicker = false
                                        vm.addAnnotation(
                                            PdfAnnotationItem(
                                                id = UUID.randomUUID().toString(),
                                                pageIndex = uiState.currentPage,
                                                kind = AnnotationKind.SIGNATURE,
                                                x = 0.35f,
                                                y = 0.70f,
                                                width = 0.28f,
                                                height = 0.14f,
                                                signaturePath = sig.imagePath
                                            )
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val bmp = remember(sig.imagePath) { BitmapFactory.decodeFile(sig.imagePath) }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = sig.name,
                                            modifier = Modifier.size(72.dp, 36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(sig.name, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                        TextButton(onClick = {
                            showSignaturePicker = false
                            onOpenSignatureStudio()
                        }) { Text(strings.sigDrawTitle) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSignaturePicker = false }) { Text(strings.close) }
            }
        )
    }

    // Protected PDF Password Prompt
    if (uiState.isPasswordPromptRequired) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(strings.readerEnterPasswordTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text(strings.readerEnterPasswordHint) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("unlock_pdf_password_input")
                    )
                    if (uiState.passwordError) {
                        Text(strings.readerWrongPassword, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.loadPdf(pwd) }, modifier = Modifier.testTag("unlock_pdf_btn")) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
private fun PdfInteractivePageView(
    pageIndex: Int,
    renderTick: Int,
    vm: PdfReaderViewModel,
    activeTool: AnnotationKind?,
    activeColor: Long,
    annotations: List<PdfAnnotationItem>,
    selectedSignatureId: String?,
    onRequestTextEntry: (AnnotationKind, Offset) -> Unit
) {
    var bmp by remember(pageIndex, renderTick) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex, renderTick) {
        bmp = vm.getOrRenderPage(pageIndex)
    }

    val draftPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val ratio = bmp?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 0.707f
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.White)
        ) {
            val rendered = bmp
            if (rendered != null) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Annotation Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        when (activeTool) {
                            AnnotationKind.FREEHAND,
                            AnnotationKind.HIGHLIGHT,
                            AnnotationKind.UNDERLINE,
                            AnnotationKind.STRIKETHROUGH,
                            AnnotationKind.SHAPE_RECT -> Modifier.pointerInput(activeTool, activeColor, pageIndex) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        vm.setCurrentPage(pageIndex)
                                        val nx = (start.x / size.width).coerceIn(0f, 1f)
                                        val ny = (start.y / size.height).coerceIn(0f, 1f)
                                        if (activeTool == AnnotationKind.FREEHAND) {
                                            draftPoints.clear()
                                            draftPoints.add(NormalizedPoint(nx, ny))
                                        } else {
                                            dragStart = Offset(nx, ny)
                                            dragEnd = Offset(nx, ny)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                        if (activeTool == AnnotationKind.FREEHAND) {
                                            draftPoints.add(NormalizedPoint(nx, ny))
                                        } else {
                                            dragEnd = Offset(nx, ny)
                                        }
                                    },
                                    onDragEnd = {
                                        if (activeTool == AnnotationKind.FREEHAND) {
                                            vm.addFreehandStroke(pageIndex, draftPoints.toList())
                                            draftPoints.clear()
                                        } else {
                                            val s = dragStart; val e = dragEnd
                                            if (s != null && e != null) {
                                                val x = minOf(s.x, e.x)
                                                val y = minOf(s.y, e.y)
                                                val w = kotlin.math.abs(e.x - s.x).coerceAtLeast(0.03f)
                                                val h = kotlin.math.abs(e.y - s.y).coerceAtLeast(0.025f)
                                                vm.addAnnotation(
                                                    PdfAnnotationItem(
                                                        id = UUID.randomUUID().toString(),
                                                        pageIndex = pageIndex,
                                                        kind = activeTool,
                                                        x = x,
                                                        y = y,
                                                        width = w,
                                                        height = h,
                                                        colorArgb = activeColor
                                                    )
                                                )
                                            }
                                            dragStart = null; dragEnd = null
                                        }
                                    }
                                )
                            }
                            AnnotationKind.TEXT_BOX, AnnotationKind.STICKY_NOTE -> Modifier.pointerInput(activeTool, pageIndex) {
                                detectTapGestures { pos ->
                                    vm.setCurrentPage(pageIndex)
                                    onRequestTextEntry(activeTool, Offset(pos.x / size.width, pos.y / size.height))
                                }
                            }
                            else -> Modifier
                        }
                    )
            ) {
                val w = size.width
                val h = size.height

                annotations.forEach { ann ->
                    val col = Color(ann.colorArgb)
                    when (ann.kind) {
                        AnnotationKind.HIGHLIGHT -> {
                            drawRect(
                                color = col.copy(alpha = 0.35f),
                                topLeft = Offset(ann.x * w, ann.y * h),
                                size = Size(ann.width * w, ann.height * h)
                            )
                        }
                        AnnotationKind.UNDERLINE -> {
                            val y = (ann.y + ann.height) * h
                            drawLine(col, Offset(ann.x * w, y), Offset((ann.x + ann.width) * w, y), strokeWidth = 4f)
                        }
                        AnnotationKind.STRIKETHROUGH -> {
                            val y = (ann.y + ann.height * 0.5f) * h
                            drawLine(col, Offset(ann.x * w, y), Offset((ann.x + ann.width) * w, y), strokeWidth = 4f)
                        }
                        AnnotationKind.SHAPE_RECT -> {
                            drawRect(
                                color = col,
                                topLeft = Offset(ann.x * w, ann.y * h),
                                size = Size(ann.width * w, ann.height * h),
                                style = Stroke(width = 4f)
                            )
                        }
                        AnnotationKind.FREEHAND -> {
                            if (ann.points.size >= 2) {
                                val path = Path().apply {
                                    moveTo(ann.points.first().x * w, ann.points.first().y * h)
                                    ann.points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                                }
                                drawPath(path, color = col, style = Stroke(width = 5f))
                            }
                        }
                        else -> Unit
                    }
                }

                // Draft strokes
                if (draftPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(draftPoints.first().x * w, draftPoints.first().y * h)
                        draftPoints.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                    }
                    drawPath(path, color = Color(activeColor), style = Stroke(width = 5f))
                }
            }

            // Text boxes, sticky notes, and draggable signature overlays
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()

            annotations.filter {
                it.kind == AnnotationKind.TEXT_BOX || it.kind == AnnotationKind.STICKY_NOTE || it.kind == AnnotationKind.SIGNATURE
            }.forEach { ann ->
                if (ann.kind == AnnotationKind.SIGNATURE) {
                    val sigBmp = remember(ann.signaturePath) { BitmapFactory.decodeFile(ann.signaturePath) }
                    if (sigBmp != null) {
                        val isSel = ann.id == selectedSignatureId
                        Image(
                            bitmap = sigBmp.asImageBitmap(),
                            contentDescription = "Signature",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .offset { IntOffset((ann.x * boxW).roundToInt(), (ann.y * boxH).roundToInt()) }
                                .size(
                                    width = ((ann.width * boxW) / resourcesDensity()).dp,
                                    height = ((ann.height * boxH) / resourcesDensity()).dp
                                )
                                .rotate(ann.rotationDeg)
                                .then(
                                    if (isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary)
                                    else Modifier
                                )
                                .pointerInput(ann.id) {
                                    detectTapGestures { vm.selectSignatureAnnotation(ann.id) }
                                }
                                .pointerInput(ann.id) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        vm.selectSignatureAnnotation(ann.id)
                                        vm.updateSignatureTransform(
                                            ann.id,
                                            ann.x + drag.x / boxW,
                                            ann.y + drag.y / boxH,
                                            ann.width,
                                            ann.height,
                                            ann.rotationDeg
                                        )
                                    }
                                }
                        )
                    }
                } else {
                    Surface(
                        color = if (ann.kind == AnnotationKind.STICKY_NOTE) Color(0xFFFEF08A) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .offset { IntOffset((ann.x * boxW).roundToInt(), (ann.y * boxH).roundToInt()) }
                            .padding(4.dp)
                    ) {
                        Text(
                            text = ann.text,
                            color = Color(ann.colorArgb),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resourcesDensity(): Float = androidx.compose.ui.platform.LocalDensity.current.density
