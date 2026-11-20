package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.FileStorageManager
import com.example.data.model.AppThemeMode
import com.example.data.model.FilterType
import com.example.data.model.OcrLanguage
import com.example.data.model.PdfPageSize
import com.example.localization.AppLanguage
import com.example.localization.LocalStrings
import com.example.ui.components.SetPinDialog
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenDriveBackup: () -> Unit,
    onOpenOcrModels: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showSetPinDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val raw = viewModel.container.settingsRepository.exportSettingsJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray()) }
                viewModel.postMessage(strings.settingsExportConfig)
            }
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val raw = context.contentResolver.openInputStream(uri)?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                val ok = viewModel.container.settingsRepository.importSettingsJson(raw)
                viewModel.postMessage(if (ok) strings.settingsImportConfig else strings.errGeneric("Invalid JSON"))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall) }

            // Language & Theme
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.settingsLanguage, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { viewModel.setLanguage(lang) },
                                    label = { Text(lang.nativeLabel) },
                                    modifier = Modifier.testTag("lang_chip_${lang.code}")
                                )
                            }
                        }

                        HorizontalDivider()
                        Text(strings.settingsTheme, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                                label = { Text(strings.settingsThemeSystem) }
                            )
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                                label = { Text(strings.settingsThemeLight) }
                            )
                            FilterChip(
                                selected = settings.themeMode == AppThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                                label = { Text(strings.settingsThemeDark) }
                            )
                        }
                    }
                }
            }

            // Scanner & PDF Defaults
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.settingsScanDefaults, style = MaterialTheme.typography.titleSmall)

                        Text(strings.settingsDefaultPageSize, style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(PdfPageSize.A4, PdfPageSize.A3, PdfPageSize.LETTER, PdfPageSize.LEGAL).forEach { sz ->
                                FilterChip(
                                    selected = settings.defaultPageSize == sz,
                                    onClick = { viewModel.setDefaultPageSize(sz) },
                                    label = { Text(strings.pageSizeLabel(sz)) }
                                )
                            }
                        }

                        Text("${strings.settingsDefaultQuality}: ${settings.defaultQuality}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = settings.defaultQuality.toFloat(),
                            onValueChange = { viewModel.setDefaultQuality(it.toInt()) },
                            valueRange = 45f..100f
                        )

                        Text(strings.settingsDefaultFilter, style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                FilterType.DOCUMENT_CLEAR,
                                FilterType.AUTO_ENHANCE,
                                FilterType.MAGIC_COLOR,
                                FilterType.BLACK_WHITE,
                                FilterType.ORIGINAL
                            ).forEach { ft ->
                                FilterChip(
                                    selected = settings.defaultFilter == ft,
                                    onClick = { viewModel.setDefaultFilter(ft) },
                                    label = { Text(strings.filterName(ft)) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.scanAutoCapture, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.autoCapture, onCheckedChange = { viewModel.setAutoCapture(it) })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.settingsAutoCrop, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.autoCrop, onCheckedChange = { viewModel.setAutoCrop(it) })
                        }

                        Text(strings.settingsDefaultOcrLang, style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(OcrLanguage.BEN_ENG, OcrLanguage.BEN, OcrLanguage.ENG, OcrLanguage.HIN, OcrLanguage.ARA).forEach { l ->
                                FilterChip(
                                    selected = settings.defaultOcrLang == l,
                                    onClick = { viewModel.setDefaultOcrLang(l) },
                                    label = { Text(strings.ocrLanguageName(l)) }
                                )
                            }
                        }
                    }
                }
            }

            // Google Drive & OCR Models
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column {
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(strings.driveTitle) },
                            supportingContent = {
                                Text(settings.driveAccountEmail?.let { strings.driveConnectedAs(it) } ?: strings.driveNotConnected)
                            },
                            modifier = Modifier.testTag("settings_drive_row").clickable(onClick = onOpenDriveBackup)
                        )
                        HorizontalDivider()
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(strings.ocrManageModels) },
                            supportingContent = { Text(strings.toolsOcrModelsDesc) },
                            modifier = Modifier.clickable(onClick = onOpenOcrModels)
                        )
                    }
                }
            }

            // Security & Privacy
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("  ${strings.settingsSecurity}", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.settingsEnablePin, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.pinEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) showSetPinDialog = true else viewModel.disablePinLock()
                                },
                                modifier = Modifier.testTag("settings_pin_switch")
                            )
                        }
                        if (settings.pinEnabled) {
                            TextButton(onClick = { showSetPinDialog = true }) {
                                Icon(Icons.Filled.Lock, contentDescription = null)
                                Text("  ${strings.settingsChangePin}")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.settingsEnableBiometric, style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = settings.biometricEnabled, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.settingsEncryptLocal, style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.localEncryptionEnabled, onCheckedChange = { viewModel.setLocalEncryptionEnabled(it) })
                        }
                    }
                }
            }

            // Storage & Config Export/Import & About
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.settingsStorage, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${strings.homeStorageUsed}: ${FileStorageManager.formatBytes(uiState.storageUsedBytes)}  (Temp: ${FileStorageManager.formatBytes(uiState.tempCacheBytes)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.clearTempFiles(strings.settingsTempCleared) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                            Text("  ${strings.settingsClearTemp}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportSettingsLauncher.launch("scanpro_settings.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.settingsExportConfig, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { importSettingsLauncher.launch(arrayOf("application/json")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(strings.settingsImportConfig, maxLines = 1)
                            }
                        }
                        HorizontalDivider()
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                            headlineContent = { Text(strings.settingsPrivacyPolicy) },
                            supportingContent = { Text(strings.settingsVersion) },
                            modifier = Modifier.clickable { showPrivacyDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onConfirmPin = { pin ->
                viewModel.setPinLock(pin)
                showSetPinDialog = false
                viewModel.postMessage(strings.secPinSetSuccess)
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.settingsAbout) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.settingsPrivacySummary, style = MaterialTheme.typography.bodyMedium)
                    Text(strings.settingsVersion, style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(strings.ok) }
            }
        )
    }
}
