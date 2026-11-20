package com.example

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.DocumentEntity
import com.example.localization.AppLanguage
import com.example.localization.BengaliStrings
import com.example.localization.EnglishStrings
import com.example.localization.LocalStrings
import com.example.pdf.PdfPrintAndShareHelper
import com.example.ui.components.PinLockScreen
import com.example.ui.components.ShareExportBottomSheet
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.GoogleDriveBackupScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OcrModelsScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.PageEditorScreen
import com.example.ui.screens.PdfViewerEditorScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignatureStudioScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.ScanProTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ScanProApplication

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.container)
            )
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

            val strings = when (settings.language) {
                AppLanguage.BENGALI -> BengaliStrings
                AppLanguage.ENGLISH -> EnglishStrings
            }

            CompositionLocalProvider(LocalStrings provides strings) {
                ScanProTheme(themeMode = settings.themeMode) {
                    if (uiState.isAppLocked) {
                        val canBio = remember(settings.biometricEnabled) {
                            settings.biometricEnabled &&
                                app.container.security.canAuthenticateBiometric(this@MainActivity)
                        }
                        LaunchedEffect(canBio) {
                            if (canBio) {
                                app.container.security.promptBiometric(
                                    activity = this@MainActivity,
                                    title = strings.secBiometricPromptTitle,
                                    subtitle = strings.secBiometricPromptSubtitle,
                                    onSuccess = { mainViewModel.unlockWithBiometric() },
                                    onError = {}
                                )
                            }
                        }
                        PinLockScreen(
                            biometricAvailable = canBio,
                            onVerifyPin = { pin -> mainViewModel.verifyPinAndUnlock(pin) },
                            onRequestBiometric = {
                                app.container.security.promptBiometric(
                                    activity = this@MainActivity,
                                    title = strings.secBiometricPromptTitle,
                                    subtitle = strings.secBiometricPromptSubtitle,
                                    onSuccess = { mainViewModel.unlockWithBiometric() },
                                    onError = {}
                                )
                            }
                        )
                    } else {
                        ScanProMainScaffold(mainViewModel)
                    }
                }
            }
        }
    }
}

private data class TopLevelTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun ScanProMainScaffold(viewModel: MainViewModel) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var shareTargetDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(uiState.snackbarEvent) {
        uiState.snackbarEvent?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val topTabs = listOf(
        TopLevelTab(AppRoute.Home, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        TopLevelTab(AppRoute.Documents, strings.navDocuments, Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "nav_documents"),
        TopLevelTab(AppRoute.Scan(null), strings.navScan, Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner, "nav_scan"),
        TopLevelTab(AppRoute.Tools, strings.navTools, Icons.Filled.Build, Icons.Outlined.Build, "nav_tools"),
        TopLevelTab(AppRoute.Settings, strings.navSettings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevel = currentDest?.let { dest ->
        dest.hasRoute<AppRoute.Home>() ||
            dest.hasRoute<AppRoute.Documents>() ||
            dest.hasRoute<AppRoute.Tools>() ||
            dest.hasRoute<AppRoute.Settings>()
    } ?: true

    fun navigateTopTab(route: AppRoute) {
        if (route is AppRoute.Scan) {
            navController.navigate(route)
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp && isTopLevel

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = if (isTopLevel) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar {
                        topTabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is AppRoute.Home -> currentDest?.hasRoute<AppRoute.Home>() == true
                                is AppRoute.Documents -> currentDest?.hasRoute<AppRoute.Documents>() == true
                                is AppRoute.Tools -> currentDest?.hasRoute<AppRoute.Tools>() == true
                                is AppRoute.Settings -> currentDest?.hasRoute<AppRoute.Settings>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        topTabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is AppRoute.Home -> currentDest?.hasRoute<AppRoute.Home>() == true
                                is AppRoute.Documents -> currentDest?.hasRoute<AppRoute.Documents>() == true
                                is AppRoute.Tools -> currentDest?.hasRoute<AppRoute.Tools>() == true
                                is AppRoute.Settings -> currentDest?.hasRoute<AppRoute.Settings>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("${tab.testTag}_rail")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<AppRoute.Home> {
                        HomeScreen(
                            viewModel = viewModel,
                            onOpenScanner = { navController.navigate(AppRoute.Scan(null)) },
                            onOpenDocuments = { folderId ->
                                viewModel.selectFolder(folderId)
                                navigateTopTab(AppRoute.Documents)
                            },
                            onOpenDocumentPdf = { docId -> navController.navigate(AppRoute.PdfReader(docId)) },
                            onOpenPageEditor = { docId -> navController.navigate(AppRoute.PageEditor(docId, 0)) },
                            onOpenOcr = { docId -> navController.navigate(AppRoute.OcrStudio(docId, 0)) },
                            onOpenTools = { navigateTopTab(AppRoute.Tools) },
                            onOpenSignatures = { navController.navigate(AppRoute.SignatureStudio) },
                            onShareDoc = { doc -> shareTargetDoc = doc }
                        )
                    }

                    composable<AppRoute.Documents> {
                        DocumentsScreen(
                            viewModel = viewModel,
                            onOpenDocumentPdf = { docId -> navController.navigate(AppRoute.PdfReader(docId)) },
                            onOpenPageEditor = { docId -> navController.navigate(AppRoute.PageEditor(docId, 0)) },
                            onOpenOcr = { docId -> navController.navigate(AppRoute.OcrStudio(docId, 0)) },
                            onShareDoc = { doc -> shareTargetDoc = doc }
                        )
                    }

                    composable<AppRoute.Scan> { entry ->
                        val route = entry.toRoute<AppRoute.Scan>()
                        ScanScreen(
                            viewModel = viewModel,
                            appendToDocId = route.appendToDocId,
                            onBack = { navController.popBackStack() },
                            onProceedToEditor = { docId ->
                                navController.navigate(AppRoute.PageEditor(docId, 0)) {
                                    popUpTo<AppRoute.Scan> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<AppRoute.Tools> {
                        ToolsScreen(
                            viewModel = viewModel,
                            onOpenDocumentPdf = { docId -> navController.navigate(AppRoute.PdfReader(docId)) },
                            onOpenPageEditor = { docId -> navController.navigate(AppRoute.PageEditor(docId, 0)) },
                            onOpenSignatures = { navController.navigate(AppRoute.SignatureStudio) },
                            onOpenOcrModels = { navController.navigate(AppRoute.OcrModels) }
                        )
                    }

                    composable<AppRoute.Settings> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onOpenDriveBackup = { navController.navigate(AppRoute.DriveBackup) },
                            onOpenOcrModels = { navController.navigate(AppRoute.OcrModels) }
                        )
                    }

                    composable<AppRoute.PageEditor> { entry ->
                        val route = entry.toRoute<AppRoute.PageEditor>()
                        PageEditorScreen(
                            docId = route.docId,
                            initialPageIndex = route.initialPageIndex,
                            mainViewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onAddMorePages = { id -> navController.navigate(AppRoute.Scan(appendToDocId = id)) },
                            onOpenOcr = { id, pageIdx -> navController.navigate(AppRoute.OcrStudio(id, pageIdx)) },
                            onPdfSaved = { id ->
                                navController.navigate(AppRoute.PdfReader(id)) {
                                    popUpTo<AppRoute.PageEditor> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<AppRoute.PdfReader> { entry ->
                        val route = entry.toRoute<AppRoute.PdfReader>()
                        PdfViewerEditorScreen(
                            docId = route.docId,
                            mainViewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPageEditor = { id -> navController.navigate(AppRoute.PageEditor(id, 0)) },
                            onOpenOcr = { id -> navController.navigate(AppRoute.OcrStudio(id, 0)) },
                            onOpenSignatureStudio = { navController.navigate(AppRoute.SignatureStudio) },
                            onShareDoc = { doc -> shareTargetDoc = doc }
                        )
                    }

                    composable<AppRoute.OcrStudio> { entry ->
                        val route = entry.toRoute<AppRoute.OcrStudio>()
                        OcrScreen(
                            docId = route.docId,
                            initialPageIndex = route.initialPageIndex,
                            mainViewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenOcrModels = { navController.navigate(AppRoute.OcrModels) }
                        )
                    }

                    composable<AppRoute.SignatureStudio> {
                        SignatureStudioScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.OcrModels> {
                        OcrModelsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.DriveBackup> {
                        GoogleDriveBackupScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }

        // Global Busy Overlay
        if (uiState.isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(uiState.busyMessage ?: strings.processing, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    shareTargetDoc?.let { doc ->
        ShareExportBottomSheet(
            doc = doc,
            onDismiss = { shareTargetDoc = null },
            onSharePdf = { viewModel.shareDocumentAsPdf(context, doc, strings.sharePdf) },
            onShareJpg = { viewModel.shareDocumentAsImages(context, doc, asPng = false, chooserTitle = strings.shareJpg) },
            onSharePng = { viewModel.shareDocumentAsImages(context, doc, asPng = true, chooserTitle = strings.sharePng) },
            onOpenOcrExport = { navController.navigate(AppRoute.OcrStudio(doc.id, 0)) },
            onSaveToDownloads = { viewModel.exportDocumentToDownloads(context, doc, strings.shareSavedDownloadsMsg) },
            onPrint = {
                val act = context as? Activity ?: return@ShareExportBottomSheet
                scope.launch {
                    val file = viewModel.container.documentRepository.getReadablePdfFile(doc) ?: return@launch
                    PdfPrintAndShareHelper.printPdf(act, file, doc.title)
                }
            }
        )
    }
}
