package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.OcrLanguage
import com.example.localization.LocalStrings
import com.example.pdf.PdfPrintAndShareHelper
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OcrViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OcrScreen(
    docId: String,
    initialPageIndex: Int,
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenOcrModels: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val vm: OcrViewModel = viewModel(
        key = "ocr_$docId",
        factory = OcrViewModel.Factory(docId, initialPageIndex, mainViewModel.container)
    )
    val doc by vm.document.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(pages) {
        vm.syncPageText(pages)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val currentPage = pages.getOrNull(uiState.selectedPageIndex)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(strings.ocrTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenOcrModels, modifier = Modifier.testTag("ocr_manage_models_btn")) {
                        Icon(Icons.Filled.Language, contentDescription = strings.ocrManageModels)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                uiState.progressText?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Language Chips (Bengali, English, Bengali+English, Hindi, Arabic)
            Text(strings.ocrSelectLanguage, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OcrLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = uiState.selectedLanguage == lang,
                        onClick = { vm.selectLanguage(lang) },
                        label = { Text(strings.ocrLanguageName(lang)) },
                        modifier = Modifier.testTag("ocr_lang_${lang.name}")
                    )
                }
            }

            // Page selector strip
            if (pages.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { idx, _ ->
                        FilterChip(
                            selected = uiState.selectedPageIndex == idx,
                            onClick = { vm.selectPage(idx, pages) },
                            label = { Text("${strings.docsPagesLabel} ${idx + 1}") }
                        )
                    }
                }
            }

            // Run OCR Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { currentPage?.let { vm.runOcrCurrentPage(it) } },
                    enabled = currentPage != null && !uiState.isRunning,
                    modifier = Modifier.weight(1f).testTag("ocr_run_page_btn")
                ) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.ocrRunCurrentPage)
                }
                if (pages.size > 1) {
                    OutlinedButton(
                        onClick = { vm.runOcrAllPages(pages) },
                        enabled = !uiState.isRunning,
                        modifier = Modifier.weight(1f).testTag("ocr_run_all_btn")
                    ) {
                        Text(strings.ocrRunAllPages)
                    }
                }
            }

            // Search within recognized text
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { vm.updateSearchQuery(it) },
                placeholder = { Text(strings.ocrSearchHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = strings.search) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.searchQuery.isNotBlank()) {
                val matches = Regex(Regex.escape(uiState.searchQuery), RegexOption.IGNORE_CASE)
                    .findAll(uiState.editableText).count()
                Text(
                    text = "$matches matches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Editable OCR Result Box (Unicode Bengali + English supported)
            OutlinedTextField(
                value = uiState.editableText,
                onValueChange = { vm.updateEditableText(it) },
                placeholder = { Text(strings.ocrResultPlaceholder) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("ocr_result_editor")
            )

            // Export & Copy Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        PdfPrintAndShareHelper.copyTextToClipboard(context, "OCR", uiState.editableText)
                        mainViewModel.postMessage(strings.copiedToClipboard)
                    },
                    modifier = Modifier.testTag("ocr_copy_btn")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = strings.copy)
                }
                OutlinedButton(
                    onClick = { currentPage?.let { vm.saveEditedText(it) } },
                    modifier = Modifier.testTag("ocr_save_btn")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = strings.save)
                }
                OutlinedButton(
                    onClick = { vm.exportTxt(context, doc?.title ?: "OCR", strings.ocrExportTxt) },
                    modifier = Modifier.weight(1f).testTag("ocr_export_txt_btn")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.ocrExportTxt, maxLines = 1)
                }
                Button(
                    onClick = { doc?.let { vm.exportSearchablePdf(context, it, strings.ocrExportSearchablePdf) } },
                    enabled = doc != null && !uiState.isRunning,
                    modifier = Modifier.weight(1f).testTag("ocr_export_searchable_pdf_btn")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.ocrExportSearchablePdf, maxLines = 1)
                }
            }
        }
    }
}
