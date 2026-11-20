package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CameraResolution
import com.example.data.model.CropQuad
import com.example.localization.LocalStrings
import com.example.scanner.DocumentEdgeDetector
import com.example.ui.viewmodel.MainViewModel
import java.io.File
import java.util.concurrent.Executors

@Composable
fun ScanScreen(
    viewModel: MainViewModel,
    appendToDocId: String?,
    onBack: () -> Unit,
    onProceedToEditor: (String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val capturedFiles = remember { mutableStateListOf<File>() }
    var useFrontCamera by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var autoCaptureEnabled by remember(settings.autoCapture) { mutableStateOf(settings.autoCapture) }
    var selectedResolution by remember(settings.cameraResolution) { mutableStateOf(settings.cameraResolution) }
    var resMenuOpen by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    var liveQuad by remember { mutableStateOf(DocumentEdgeDetector.defaultInsetQuad()) }
    var docDetected by remember { mutableStateOf(false) }
    var stableFrames by remember { mutableIntStateOf(0) }
    var lastStableQuad by remember { mutableStateOf<CropQuad?>(null) }
    var lastAutoCaptureMs by remember { mutableStateOf(0L) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var exposureIndex by remember { mutableIntStateOf(0) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    fun triggerCapture() {
        val ic = imageCapture ?: return
        val outFile = viewModel.container.storage.newTempCameraFile()
        val outputOpts = ImageCapture.OutputFileOptions.Builder(outFile).build()
        ic.takePicture(
            outputOpts,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturedFiles.add(outFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.postMessage(exception.message ?: strings.errCameraUnavailable)
                }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryImages(uris) { docId -> onProceedToEditor(docId) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(strings.scanPermissionTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.scanPermissionMsg, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("grant_camera_permission_btn")
                ) {
                    Text(strings.scanGrantPermission)
                }
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Text(strings.scanGalleryImport)
                }
            }
        } else if (cameraUnavailable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(strings.scanCameraUnavailable, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("fallback_gallery_import_btn")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.scanGalleryImport)
                }
            }
        } else {
            val previewView = remember {
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            LaunchedEffect(useFrontCamera, selectedResolution) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val provider = cameraProviderFuture.get()
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        if (!provider.hasCamera(selector)) {
                            cameraUnavailable = true
                            return@addListener
                        }

                        val targetSize = when (selectedResolution) {
                            CameraResolution.HIGH -> Size(1920, 2480)
                            CameraResolution.STANDARD -> Size(1240, 1754)
                            CameraResolution.COMPACT -> Size(900, 1240)
                        }
                        val resSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()

                        val preview = Preview.Builder()
                            .setResolutionSelector(resSelector)
                            .build().also { it.surfaceProvider = previewView.surfaceProvider }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(resSelector)
                            .build()
                        imageCapture = capture

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(analysisExecutor) { proxy ->
                            try {
                                val plane = proxy.planes.firstOrNull()
                                if (plane != null) {
                                    val w = proxy.width
                                    val h = proxy.height
                                    val step = (maxOf(w, h) / 140).coerceAtLeast(2)
                                    val sw = w / step
                                    val sh = h / step
                                    val lum = IntArray(sw * sh)
                                    val buf = plane.buffer
                                    val rowStride = plane.rowStride
                                    for (y in 0 until sh) {
                                        val rowOffset = y * step * rowStride
                                        for (x in 0 until sw) {
                                            val idx = rowOffset + x * step
                                            if (idx < buf.limit()) {
                                                lum[y * sw + x] = buf.get(idx).toInt() and 0xFF
                                            }
                                        }
                                    }
                                    val res = DocumentEdgeDetector.detectFromLuminance(lum, sw, sh)
                                    liveQuad = res.quad
                                    docDetected = res.isDocumentDetected
                                    if (autoCaptureEnabled && res.isDocumentDetected) {
                                        val prev = lastStableQuad
                                        if (prev != null && DocumentEdgeDetector.areQuadsClose(prev, res.quad)) {
                                            stableFrames++
                                        } else {
                                            stableFrames = 0
                                        }
                                        lastStableQuad = res.quad
                                        val now = System.currentTimeMillis()
                                        if (stableFrames >= 5 && now - lastAutoCaptureMs > 2600L) {
                                            lastAutoCaptureMs = now
                                            stableFrames = 0
                                            ContextCompat.getMainExecutor(context).execute { triggerCapture() }
                                        }
                                    }
                                }
                            } finally {
                                proxy.close()
                            }
                        }

                        provider.unbindAll()
                        val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                        boundCamera = cam
                        cam.cameraControl.enableTorch(torchOn && !useFrontCamera)
                    } catch (_: Exception) {
                        cameraUnavailable = true
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            AndroidView(
                factory = {
                    previewView.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                            boundCamera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).build()
                            )
                            v.performClick()
                        }
                        true
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Live Document Boundary Quad Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val tl = Offset(liveQuad.topLeft.x * w, liveQuad.topLeft.y * h)
                val tr = Offset(liveQuad.topRight.x * w, liveQuad.topRight.y * h)
                val br = Offset(liveQuad.bottomRight.x * w, liveQuad.bottomRight.y * h)
                val bl = Offset(liveQuad.bottomLeft.x * w, liveQuad.bottomLeft.y * h)

                val strokeColor = if (docDetected) Color(0xFF2DD4BF) else Color.White.copy(alpha = 0.45f)
                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(path, color = strokeColor, style = Stroke(width = if (docDetected) 5f else 2.5f))
                listOf(tl, tr, br, bl).forEach { corner ->
                    drawCircle(color = strokeColor, radius = 10f, center = corner)
                }
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = Color.White)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (docDetected) Color(0xCC0F766E) else Color.Black.copy(alpha = 0.55f)
            ) {
                Text(
                    text = if (docDetected) strings.scanDocDetected else strings.scanAlignDoc,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        boundCamera?.cameraControl?.enableTorch(torchOn && !useFrontCamera)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("scan_flash_btn")
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) strings.scanFlashOn else strings.scanFlashOff,
                        tint = if (torchOn) Color(0xFFFDE047) else Color.White
                    )
                }
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("scan_switch_camera_btn")
                ) {
                    Icon(Icons.Filled.FlipCameraAndroid, contentDescription = strings.scanSwitchCamera, tint = Color.White)
                }
                Box {
                    TextButton(
                        onClick = { resMenuOpen = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    ) {
                        Text(selectedResolution.name.take(3), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = resMenuOpen, onDismissRequest = { resMenuOpen = false }) {
                        CameraResolution.entries.forEach { res ->
                            DropdownMenuItem(
                                text = { Text(res.label) },
                                onClick = {
                                    resMenuOpen = false
                                    selectedResolution = res
                                    viewModel.setCameraResolution(res)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls + Filmstrip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Exposure Slider
            boundCamera?.cameraInfo?.exposureState?.let { exp ->
                if (exp.isExposureCompensationSupported) {
                    val range = exp.exposureCompensationRange
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.scanExposure, color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = {
                                exposureIndex = it.toInt()
                                boundCamera?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                            },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Captured Pages Filmstrip
            if (capturedFiles.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    itemsIndexed(capturedFiles) { idx, file ->
                        Box(modifier = Modifier.size(56.dp, 72.dp)) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                            )
                            IconButton(
                                onClick = { capturedFiles.removeAt(idx) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = strings.delete, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("scan_gallery_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = strings.scanGalleryImport, tint = Color.White)
                    }
                    Text(strings.scanGalleryImport, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }

                // Auto Capture Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            autoCaptureEnabled = !autoCaptureEnabled
                            viewModel.setAutoCapture(autoCaptureEnabled)
                        },
                        modifier = Modifier.testTag("scan_auto_toggle_btn")
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = strings.scanAutoCapture,
                            tint = if (autoCaptureEnabled) Color(0xFF2DD4BF) else Color.White
                        )
                    }
                    Text(
                        strings.scanAutoCapture,
                        color = if (autoCaptureEnabled) Color(0xFF2DD4BF) else Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Big Shutter Button
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(70.dp)
                        .border(4.dp, Color(0xFF2DD4BF), CircleShape)
                        .clickable { triggerCapture() }
                        .testTag("scan_shutter_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = strings.scanManualCapture, tint = Color(0xFF0F172A))
                    }
                }

                // Finish / Proceed to Editor
                if (capturedFiles.isNotEmpty()) {
                    Button(
                        onClick = {
                            val list = capturedFiles.toList()
                            if (appendToDocId != null) {
                                viewModel.appendCapturedFilesToDocument(appendToDocId, list) {
                                    onProceedToEditor(appendToDocId)
                                }
                            } else {
                                viewModel.createDocumentFromCapturedFiles(list) { newDocId ->
                                    onProceedToEditor(newDocId)
                                }
                            }
                        },
                        modifier = Modifier.testTag("scan_finish_btn")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.scanFinishPages(capturedFiles.size))
                    }
                } else {
                    Spacer(modifier = Modifier.width(88.dp))
                }
            }
        }
    }
}
