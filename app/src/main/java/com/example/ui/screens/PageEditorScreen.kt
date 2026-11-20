package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CropQuad
import com.example.data.model.FilterType
import com.example.data.model.NormalizedPoint
import com.example.localization.LocalStrings
import com.example.ui.components.PdfExportConfigDialog
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PageEditorViewModel
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    docId: String,
    initialPageIndex: Int,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onAddMorePages: (String) -> Unit,
    onOpenOcr: (String, Int) -> Unit,
    onPdfSaved: (String) -> Unit
) {
    val strings = LocalStrings.current
    val vm: PageEditorViewModel = viewModel(
        key = "editor_$docId",
        factory = PageEditorViewModel.Factory(docId, initialPageIndex, mainViewModel.container)
    )
    val doc by vm.document.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(pages) {
        vm.onPagesUpdated(pages)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val currentPage = pages.getOrNull(uiState.currentPageIndex)
    var activeSubPanel by remember { mutableStateOf("filters") } // "filters", "adjust", "crop"
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc?.title ?: strings.editorTitle, maxLines = 1)
                        if (pages.isNotEmpty()) {
                            Text(
                                text = strings.editorPageOf(uiState.currentPageIndex + 1, pages.size),
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
                    IconButton(
                        onClick = { onOpenOcr(docId, uiState.currentPageIndex) },
                        modifier = Modifier.testTag("editor_ocr_btn")
                    ) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = strings.editorRunOcr)
                    }
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.padding(end = 8.dp).testTag("editor_export_pdf_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.editorExportPdf)
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
            // Main Preview / Cropper Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isCropMode && currentPage != null) {
                    val origBmp = remember(currentPage.originalImagePath) {
                        BitmapFactory.decodeFile(currentPage.originalImagePath)
                    }
                    if (origBmp != null) {
                        InteractiveCornerCropper(
                            bitmap = origBmp,
                            quad = uiState.workingAdjustments.cropQuad,
                            onQuadChanged = { nextQuad ->
                                vm.setCropQuad(currentPage, nextQuad)
                            }
                        )
                    }
                } else {
                    val shownBmp = if (uiState.isShowingOriginal) {
                        uiState.originalPreviewBitmap ?: uiState.previewBitmap
                    } else {
                        uiState.previewBitmap
                    }

                    if (shownBmp != null) {
                        Image(
                            bitmap = shownBmp.asImageBitmap(),
                            contentDescription = strings.editorTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            vm.setShowingOriginal(true)
                                            tryAwaitRelease()
                                            vm.setShowingOriginal(false)
                                        }
                                    )
                                }
                                .testTag("editor_preview_image")
                        )
                    }

                    // Before/After hint chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Compare, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.editorBeforeAfterHint, color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (uiState.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            // Page switcher & reorder strip (when multi-page)
            if (pages.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { idx, p ->
                        val active = idx == uiState.currentPageIndex
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .border(
                                    width = if (active) 2.dp else 1.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { vm.selectPage(idx, pages) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (active && idx > 0) {
                                    IconButton(
                                        onClick = { vm.movePage(pages, idx, idx - 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = strings.editorReorderPages)
                                    }
                                }
                                Text("${idx + 1}", style = MaterialTheme.typography.labelMedium)
                                if (active && idx < pages.lastIndex) {
                                    IconButton(
                                        onClick = { vm.movePage(pages, idx, idx + 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = strings.editorReorderPages)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sub-panels: Filters / Fine Adjust / Crop Controls
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    when (activeSubPanel) {
                        "crop" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { currentPage?.let { vm.autoDetectCrop(it) } }) {
                                    Text(strings.editorAutoCrop)
                                }
                                TextButton(onClick = { currentPage?.let { vm.setCropQuad(it, CropQuad()) } }) {
                                    Text(strings.editorResetCrop)
                                }
                                Button(onClick = {
                                    vm.toggleCropMode(false)
                                    activeSubPanel = "filters"
                                }) {
                                    Text(strings.done)
                                }
                            }
                        }
                        "filters" -> {
                            if (uiState.workingAdjustments.filterType != FilterType.ORIGINAL) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.editorIntensity, style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = uiState.workingAdjustments.intensity,
                                        onValueChange = { v ->
                                            currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(intensity = v) } }
                                        },
                                        valueRange = 0.2f..1.4f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(FilterType.entries) { filter ->
                                    val selected = uiState.workingAdjustments.filterType == filter
                                    val thumb = uiState.filterThumbnails[filter]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                currentPage?.let { p ->
                                                    vm.updateAdjustments(p) { it.copy(filterType = filter) }
                                                }
                                            }
                                            .testTag("filter_chip_${filter.name}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    width = if (selected) 2.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb.asImageBitmap(),
                                                    contentDescription = strings.filterName(filter),
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = strings.filterName(filter),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        "adjust" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                val adj = uiState.workingAdjustments
                                AdjustmentSlider(strings.editorBrightness, adj.brightness, -80f..80f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(brightness = v) } }
                                }
                                AdjustmentSlider(strings.editorContrast, adj.contrast, 0.5f..2.0f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(contrast = v) } }
                                }
                                AdjustmentSlider(strings.editorSharpness, adj.sharpness, 0f..1f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(sharpness = v) } }
                                }
                                AdjustmentSlider(strings.editorSaturation, adj.saturation, 0f..2f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(saturation = v) } }
                                }
                                AdjustmentSlider(strings.editorExposure, adj.exposure, -1f..1f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(exposure = v) } }
                                }
                                AdjustmentSlider(strings.editorNoiseReduction, adj.noiseReduction, 0f..1f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(noiseReduction = v) } }
                                }
                                AdjustmentSlider(strings.editorShadowReduction, adj.shadowReduction, 0f..1f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(shadowReduction = v) } }
                                }
                                AdjustmentSlider(strings.editorBgWhitening, adj.backgroundWhitening, 0f..1f) { v ->
                                    currentPage?.let { p -> vm.updateAdjustments(p) { it.copy(backgroundWhitening = v) } }
                                }
                            }
                        }
                    }

                    // Bottom Action Bar
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            EditorToolbarBtn(Icons.Filled.Crop, strings.editorCrop) {
                                val next = !uiState.isCropMode
                                vm.toggleCropMode(next)
                                activeSubPanel = if (next) "crop" else "filters"
                            }
                        }
                        item {
                            EditorToolbarBtn(Icons.Filled.RotateRight, strings.editorRotate) {
                                currentPage?.let { vm.rotate90(it) }
                            }
                        }
                        item {
                            EditorToolbarBtn(Icons.Filled.FilterBAndW, strings.editorFilters) {
                                vm.toggleCropMode(false)
                                activeSubPanel = "filters"
                            }
                        }
                        item {
                            EditorToolbarBtn(Icons.Filled.Tune, strings.editorAdjust) {
                                vm.toggleCropMode(false)
                                activeSubPanel = "adjust"
                            }
                        }
                        item {
                            EditorToolbarBtn(Icons.Filled.DoneAll, strings.applyAll) {
                                vm.applyFilterToAllPages(pages, strings.editorSaveChanges)
                            }
                        }
                        item {
                            EditorToolbarBtn(Icons.Filled.AddAPhoto, strings.editorAddPage) {
                                onAddMorePages(docId)
                            }
                        }
                        if (pages.size > 1) {
                            item {
                                EditorToolbarBtn(Icons.Filled.Delete, strings.editorDeletePage) {
                                    currentPage?.let { vm.deletePage(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        PdfExportConfigDialog(
            initialTitle = doc?.title ?: "Scanned Document",
            defaultPageSize = settings.defaultPageSize,
            defaultQuality = settings.defaultQuality,
            onDismiss = { showExportDialog = false },
            onConfirm = { opts ->
                showExportDialog = false
                vm.exportPdf(currentPage, opts) { savedDocId ->
                    onPdfSaved(savedDocId)
                }
            }
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(120.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EditorToolbarBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InteractiveCornerCropper(
    bitmap: android.graphics.Bitmap,
    quad: CropQuad,
    onQuadChanged: (CropQuad) -> Unit
) {
    var activeCorner by remember { mutableIntStateOf(-1) }
    var workingQuad by remember(quad) { mutableStateOf(quad) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val boxRatio = maxWidth / maxHeight
        val imgW = if (ratio > boxRatio) maxWidth else maxHeight * ratio
        val imgH = if (ratio > boxRatio) maxWidth / ratio else maxHeight

        Box(modifier = Modifier.size(imgW, imgH)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val pts = listOf(
                                    Offset(workingQuad.topLeft.x * w, workingQuad.topLeft.y * h),
                                    Offset(workingQuad.topRight.x * w, workingQuad.topRight.y * h),
                                    Offset(workingQuad.bottomRight.x * w, workingQuad.bottomRight.y * h),
                                    Offset(workingQuad.bottomLeft.x * w, workingQuad.bottomLeft.y * h)
                                )
                                var minIdx = -1
                                var minDist = 140f
                                pts.forEachIndexed { idx, p ->
                                    val d = hypot((p.x - start.x).toDouble(), (p.y - start.y).toDouble()).toFloat()
                                    if (d < minDist) {
                                        minDist = d
                                        minIdx = idx
                                    }
                                }
                                activeCorner = minIdx
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (activeCorner < 0) return@detectDragGestures
                                val dx = dragAmount.x / size.width.toFloat()
                                val dy = dragAmount.y / size.height.toFloat()
                                workingQuad = when (activeCorner) {
                                    0 -> workingQuad.copy(
                                        topLeft = NormalizedPoint(
                                            (workingQuad.topLeft.x + dx).coerceIn(0f, 0.48f),
                                            (workingQuad.topLeft.y + dy).coerceIn(0f, 0.48f)
                                        )
                                    )
                                    1 -> workingQuad.copy(
                                        topRight = NormalizedPoint(
                                            (workingQuad.topRight.x + dx).coerceIn(0.52f, 1f),
                                            (workingQuad.topRight.y + dy).coerceIn(0f, 0.48f)
                                        )
                                    )
                                    2 -> workingQuad.copy(
                                        bottomRight = NormalizedPoint(
                                            (workingQuad.bottomRight.x + dx).coerceIn(0.52f, 1f),
                                            (workingQuad.bottomRight.y + dy).coerceIn(0.52f, 1f)
                                        )
                                    )
                                    else -> workingQuad.copy(
                                        bottomLeft = NormalizedPoint(
                                            (workingQuad.bottomLeft.x + dx).coerceIn(0f, 0.48f),
                                            (workingQuad.bottomLeft.y + dy).coerceIn(0.52f, 1f)
                                        )
                                    )
                                }
                            },
                            onDragEnd = {
                                activeCorner = -1
                                onQuadChanged(workingQuad)
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val tl = Offset(workingQuad.topLeft.x * w, workingQuad.topLeft.y * h)
                val tr = Offset(workingQuad.topRight.x * w, workingQuad.topRight.y * h)
                val br = Offset(workingQuad.bottomRight.x * w, workingQuad.bottomRight.y * h)
                val bl = Offset(workingQuad.bottomLeft.x * w, workingQuad.bottomLeft.y * h)

                val path = Path().apply {
                    moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
                }
                drawPath(path, color = Color(0xFF14B8A6), style = Stroke(width = 4f))
                listOf(tl, tr, br, bl).forEach { pt ->
                    drawCircle(color = Color.White, radius = 16f, center = pt)
                    drawCircle(color = Color(0xFF0F766E), radius = 12f, center = pt)
                }
            }
        }
    }
}
