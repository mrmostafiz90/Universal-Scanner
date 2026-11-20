package com.example.localization

import com.example.data.model.FilterType
import com.example.data.model.OcrLanguage
import com.example.data.model.PdfPageSize
import com.example.data.model.SortOrder

interface AppStrings {
    val appName: String
    val tagline: String

    // Bottom Navigation
    val navHome: String
    val navDocuments: String
    val navScan: String
    val navTools: String
    val navSettings: String

    // Common Actions
    val ok: String
    val cancel: String
    val save: String
    val delete: String
    val rename: String
    val share: String
    val export: String
    val print: String
    val close: String
    val back: String
    val done: String
    val retry: String
    val apply: String
    val applyAll: String
    val search: String
    val selectAll: String
    val clearSelection: String
    val create: String
    val confirm: String
    val loading: String
    val processing: String
    val copy: String
    val copiedToClipboard: String

    // Home Screen
    val homeScanNow: String
    val homeScanSubtitle: String
    val homeQuickActions: String
    val homeImportGallery: String
    val homeImportPdf: String
    val homeMergePdf: String
    val homeOcrExtract: String
    val homeSignPdf: String
    val homeStorageUsed: String
    val homeDocumentsCount: (Int) -> String
    val homePagesCount: (Int) -> String
    val homeFolders: String
    val homeRecentDocs: String
    val homeRecentActivity: String
    val homeNoDocsTitle: String
    val homeNoDocsSubtitle: String
    val homeOfflineBadge: String

    // Documents Screen
    val docsAll: String
    val docsRecent: String
    val docsFavorites: String
    val docsStarred: String
    val docsTrash: String
    val docsFolders: String
    val docsNewFolder: String
    val docsFolderNameHint: String
    val docsDocumentNameHint: String
    val docsSearchHint: String
    val docsGridView: String
    val docsListView: String
    val docsSortBy: String
    val docsMoveTo: String
    val docsCopyTo: String
    val docsRestore: String
    val docsDeleteForever: String
    val docsEmptyTrash: String
    val docsRootFolder: String
    val docsInfo: String
    val docsCreated: String
    val docsModified: String
    val docsSize: String
    val docsPagesLabel: String
    val docsEncryptedBadge: String
    val docsOcrTextLabel: String
    val docsSelectedCount: (Int) -> String
    val docsConfirmDeleteTitle: String
    val docsConfirmDeleteMsg: String
    val docsTrashEmptyMsg: String
    val docsBulkExportPdf: String

    fun sortLabel(order: SortOrder): String

    // Scanner Screen
    val scanTitle: String
    val scanPermissionTitle: String
    val scanPermissionMsg: String
    val scanGrantPermission: String
    val scanAutoCapture: String
    val scanManualCapture: String
    val scanFlashOn: String
    val scanFlashOff: String
    val scanSwitchCamera: String
    val scanGalleryImport: String
    val scanResolution: String
    val scanExposure: String
    val scanDocDetected: String
    val scanAlignDoc: String
    val scanFinishPages: (Int) -> String
    val scanCameraUnavailable: String

    // Page Editor Screen
    val editorTitle: String
    val editorPageOf: (Int, Int) -> String
    val editorCrop: String
    val editorRotate: String
    val editorFilters: String
    val editorAdjust: String
    val editorRetake: String
    val editorAddPage: String
    val editorDeletePage: String
    val editorReorderPages: String
    val editorBeforeAfterHint: String
    val editorIntensity: String
    val editorBrightness: String
    val editorContrast: String
    val editorSharpness: String
    val editorSaturation: String
    val editorExposure: String
    val editorNoiseReduction: String
    val editorShadowReduction: String
    val editorBgWhitening: String
    val editorExportPdf: String
    val editorSaveChanges: String
    val editorRunOcr: String
    val editorResetCrop: String
    val editorAutoCrop: String

    fun filterName(filter: FilterType): String

    // PDF Creation & Export Config
    val pdfCreateTitle: String
    val pdfDocTitle: String
    val pdfAuthor: String
    val pdfPageSize: String
    val pdfOrientation: String
    val pdfPortrait: String
    val pdfLandscape: String
    val pdfCustomWidthMm: String
    val pdfCustomHeightMm: String
    val pdfMarginMm: String
    val pdfQuality: String
    val pdfPasswordProtect: String
    val pdfPasswordHint: String
    val pdfWatermark: String
    val pdfWatermarkHint: String
    val pdfPageNumbers: String
    val pdfSearchableLayer: String
    val pdfGenerateAndSave: String
    fun pageSizeLabel(size: PdfPageSize): String

    // PDF Reader & Editor
    val readerTitle: String
    val readerContinuousScroll: String
    val readerSinglePage: String
    val readerBookmark: String
    val readerBookmarked: String
    val readerHighlight: String
    val readerUnderline: String
    val readerStrikethrough: String
    val readerFreehand: String
    val readerTextBox: String
    val readerStickyNote: String
    val readerShape: String
    val readerSignature: String
    val readerUndo: String
    val readerRedo: String
    val readerSaveEdited: String
    val readerEnterTextHint: String
    val readerEnterPasswordTitle: String
    val readerEnterPasswordHint: String
    val readerWrongPassword: String
    val readerTapToPlace: String
    val readerSignatureScale: String
    val readerSignatureRotate: String
    val readerRemoveSignature: String

    // OCR Screen
    val ocrTitle: String
    val ocrSelectLanguage: String
    val ocrRunCurrentPage: String
    val ocrRunAllPages: String
    val ocrSearchHint: String
    val ocrResultPlaceholder: String
    val ocrExportTxt: String
    val ocrExportSearchablePdf: String
    val ocrManageModels: String
    val ocrModelReady: String
    val ocrModelNotDownloaded: String
    val ocrDownloadModel: String
    val ocrDownloading: String
    val ocrModelDelete: String
    val ocrOfflineReadyNote: String
    fun ocrLanguageName(lang: OcrLanguage): String

    // Tools & Signatures
    val toolsTitle: String
    val toolsMergePdf: String
    val toolsMergeDesc: String
    val toolsSplitPdf: String
    val toolsSplitDesc: String
    val toolsCompressPdf: String
    val toolsCompressDesc: String
    val toolsExtractPages: String
    val toolsExtractDesc: String
    val toolsPdfToImages: String
    val toolsPdfToImagesDesc: String
    val toolsImagesToPdf: String
    val toolsImagesToPdfDesc: String
    val toolsProtectPdf: String
    val toolsProtectDesc: String
    val toolsWatermarkPdf: String
    val toolsWatermarkDesc: String
    val toolsSignatures: String
    val toolsSignaturesDesc: String
    val toolsOcrModels: String
    val toolsOcrModelsDesc: String
    val toolsSelectDocs: String
    val toolsPageRangeHint: String
    val toolsTargetQuality: String
    val toolsSplitSuccess: String
    val toolsMergeSuccess: String
    val toolsCompressSuccess: (String) -> String

    // Signatures
    val sigDrawTitle: String
    val sigNameHint: String
    val sigClear: String
    val sigImportImage: String
    val sigSavedList: String
    val sigNoSaved: String
    val sigPenColor: String
    val sigStrokeWidth: String

    // Share & Export
    val shareTitle: String
    val sharePdf: String
    val shareJpg: String
    val sharePng: String
    val shareTxt: String
    val shareSearchablePdf: String
    val shareSaveToDownloads: String
    val shareSystemPrint: String
    val shareSystemSheet: String
    val shareSavedDownloadsMsg: (String) -> String

    // Security & Lock
    val secLockTitle: String
    val secEnterPin: String
    val secSetPinTitle: String
    val secConfirmPin: String
    val secPinMismatch: String
    val secWrongPin: String
    val secBiometricPromptTitle: String
    val secBiometricPromptSubtitle: String
    val secUseBiometric: String
    val secPinSetSuccess: String

    // Google Drive Backup
    val driveTitle: String
    val driveConnectedAs: (String) -> String
    val driveNotConnected: String
    val driveConnect: String
    val driveDisconnect: String
    val driveBackupNow: String
    val driveRestoreNow: String
    val driveAutoBackup: String
    val driveIncludeSettings: String
    val driveSelectedFoldersOnly: String
    val driveLastBackup: String
    val driveNever: String
    val driveHistory: String
    val driveDeleteRemote: String
    val driveProgress: (Int, Int) -> String
    val driveQuotaNotice: String

    // Settings
    val settingsTitle: String
    val settingsLanguage: String
    val settingsTheme: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsScanDefaults: String
    val settingsDefaultPageSize: String
    val settingsDefaultQuality: String
    val settingsDefaultFilter: String
    val settingsAutoCrop: String
    val settingsDefaultOcrLang: String
    val settingsSecurity: String
    val settingsEnablePin: String
    val settingsChangePin: String
    val settingsEnableBiometric: String
    val settingsEncryptLocal: String
    val settingsStorage: String
    val settingsClearTemp: String
    val settingsTempCleared: String
    val settingsExportConfig: String
    val settingsImportConfig: String
    val settingsPrivacyPolicy: String
    val settingsPrivacySummary: String
    val settingsAbout: String
    val settingsVersion: String

    // Errors
    val errCameraUnavailable: String
    val errStorageDenied: String
    val errInsufficientStorage: String
    val errOcrFailed: String
    val errPdfGeneration: String
    val errPdfCorrupted: String
    val errInvalidImage: String
    val errNetworkUnavailable: String
    val errGoogleAuth: String
    val errBackupInterrupted: String
    val errRestoreFailed: String
    val errGeneric: (String) -> String
}
