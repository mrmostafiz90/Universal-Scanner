package com.example.core

import android.content.Context
import com.example.backup.GoogleDriveBackupService
import com.example.data.local.ScanProDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.DocumentRepository
import com.example.ocr.TesseractOcrEngine
import com.example.pdf.PdfEngine
import com.example.security.SecurityManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val storage = FileStorageManager(appContext)
    val security = SecurityManager()
    val database = ScanProDatabase.getInstance(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val pdfEngine = PdfEngine(appContext, storage)
    val ocrEngine = TesseractOcrEngine(appContext, storage)

    val documentRepository = DocumentRepository(
        context = appContext,
        documentDao = database.documentDao(),
        folderDao = database.folderDao(),
        pageDao = database.pageDao(),
        signatureDao = database.signatureDao(),
        backupHistoryDao = database.backupHistoryDao(),
        activityLogDao = database.activityLogDao(),
        storage = storage,
        pdfEngine = pdfEngine,
        security = security
    )

    val driveService = GoogleDriveBackupService(
        context = appContext,
        repository = documentRepository,
        settingsRepository = settingsRepository
    )
}
