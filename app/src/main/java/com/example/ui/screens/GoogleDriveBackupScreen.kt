package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.localization.LocalStrings
import com.example.ui.viewmodel.MainViewModel
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingDriveAction { CONNECT, BACKUP, RESTORE, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDriveBackupScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val history by viewModel.backupHistory.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    var selectedFoldersOnly by remember { mutableStateOf(false) }
    val pickedFolderIds = remember { mutableStateListOf<String>() }
    var pendingAction by remember { mutableStateOf(PendingDriveAction.CONNECT) }

    fun executeWithToken(token: String, action: PendingDriveAction) {
        scope.launch {
            if (settings.driveAccountEmail == null || action == PendingDriveAction.CONNECT) {
                val email = viewModel.container.driveService.fetchUserEmail(token) ?: "Google Account"
                viewModel.setDriveAccountEmail(email)
            }
            when (action) {
                PendingDriveAction.CONNECT -> Unit
                PendingDriveAction.BACKUP -> {
                    val fIds = if (selectedFoldersOnly && pickedFolderIds.isNotEmpty()) pickedFolderIds.toSet() else null
                    viewModel.runDriveBackup(token, fIds, settings.includeSettingsInBackup)
                }
                PendingDriveAction.RESTORE -> viewModel.runDriveRestore(token)
                PendingDriveAction.DELETE -> viewModel.deleteDriveBackup(token)
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        runCatching {
            val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
            val token = authResult.accessToken
            if (!token.isNullOrBlank()) {
                executeWithToken(token, pendingAction)
            } else {
                viewModel.postMessage(strings.errGoogleAuth)
            }
        }.onFailure {
            viewModel.postMessage(strings.errGoogleAuth)
        }
    }

    fun startDriveAction(action: PendingDriveAction) {
        pendingAction = action
        scope.launch {
            runCatching {
                val res = viewModel.container.driveService.requestDriveAuthorization()
                if (res.hasResolution()) {
                    val pi = res.pendingIntent
                    if (pi != null) {
                        consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    val token = res.accessToken
                    if (!token.isNullOrBlank()) {
                        executeWithToken(token, action)
                    } else {
                        viewModel.postMessage(strings.errGoogleAuth)
                    }
                }
            }.onFailure {
                viewModel.postMessage(strings.errGoogleAuth)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.driveTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settings.driveAccountEmail?.let { strings.driveConnectedAs(it) }
                                        ?: strings.driveNotConnected,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                val lastStr = settings.lastBackupAt?.let {
                                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it))
                                } ?: strings.driveNever
                                Text(
                                    text = "${strings.driveLastBackup}: $lastStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (settings.driveAccountEmail == null) {
                            Button(
                                onClick = { startDriveAction(PendingDriveAction.CONNECT) },
                                modifier = Modifier.fillMaxWidth().testTag("drive_connect_btn")
                            ) {
                                Text(strings.driveConnect)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { startDriveAction(PendingDriveAction.BACKUP) },
                                    modifier = Modifier.weight(1f).testTag("drive_backup_now_btn")
                                ) {
                                    Icon(Icons.Filled.Backup, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.driveBackupNow)
                                }
                                OutlinedButton(
                                    onClick = { startDriveAction(PendingDriveAction.RESTORE) },
                                    modifier = Modifier.weight(1f).testTag("drive_restore_now_btn")
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.driveRestoreNow)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = { startDriveAction(PendingDriveAction.DELETE) }) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.driveDeleteRemote, color = MaterialTheme.colorScheme.error)
                                }
                                TextButton(onClick = { viewModel.setDriveAccountEmail(null) }) {
                                    Text(strings.driveDisconnect)
                                }
                            }
                        }
                    }
                }
            }

            uiState.driveSyncProgress?.let { (done, total) ->
                item {
                    Column {
                        Text(strings.driveProgress(done, total), style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (done.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.driveAutoBackup, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.autoBackupEnabled,
                                onCheckedChange = { viewModel.setAutoBackup(context, it) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.driveIncludeSettings, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.includeSettingsInBackup,
                                onCheckedChange = { viewModel.setIncludeSettingsInBackup(it) }
                            )
                        }
                        if (folders.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.driveSelectedFoldersOnly, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = selectedFoldersOnly,
                                    onCheckedChange = { selectedFoldersOnly = it }
                                )
                            }
                            if (selectedFoldersOnly) {
                                folders.forEach { folder ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (folder.id in pickedFolderIds) pickedFolderIds.remove(folder.id)
                                                else pickedFolderIds.add(folder.id)
                                            }
                                    ) {
                                        Checkbox(
                                            checked = folder.id in pickedFolderIds,
                                            onCheckedChange = {
                                                if (it) pickedFolderIds.add(folder.id) else pickedFolderIds.remove(folder.id)
                                            }
                                        )
                                        Text(folder.name, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = strings.driveQuotaNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (history.isNotEmpty()) {
                item { Text(strings.driveHistory, style = MaterialTheme.typography.titleMedium) }
                items(history, key = { it.id }) { item ->
                    val t = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${item.status} — ${item.message}", style = MaterialTheme.typography.bodyMedium)
                                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
