# ScanPro PDF (Android — Kotlin & Jetpack Compose)

**ScanPro PDF** is an offline-first, local-first document scanning, image processing, PDF editing, and bilingual (**Bengali / বাংলা + English**) OCR Android application built with **Kotlin** and **Jetpack Compose (Material 3)**.

> **Platform Note:** This workspace targets native Android (`minSdk 24`, `targetSdk 36`, `compileSdk 36`, `applicationId = com.aistudio.scanpropdf.mkrvtx`).

---

## Architecture Overview

The application follows a clean, modular MVVM + Repository architecture with manual constructor dependency injection via `AppContainer`:

- **UI Layer (`ui/`)**:
  - `HomeScreen`, `DocumentsScreen`, `ScanScreen`, `PageEditorScreen`, `PdfViewerEditorScreen`, `OcrScreen`, `ToolsScreen`, `SignatureStudioScreen`, `OcrModelsScreen`, `GoogleDriveBackupScreen`, `SettingsScreen`
  - Adaptive Material 3 navigation (`NavigationBar` on compact phones, `NavigationRail` on medium/expanded foldables and tablets) with `enableEdgeToEdge()`.
  - Full runtime **Bengali (`বাংলা`) + English** localization via `AppStrings`, `BengaliStrings`, and `EnglishStrings`, bundled with local `NotoSansBengali`, `PlusJakartaSans`, and `JetBrainsMono` fonts (`res/font/`).
- **Camera & Image Processing (`scanner/`)**:
  - `ScanScreen` uses **CameraX** (live preview, tap-to-focus & exposure slider, torch, front/rear switch, resolution selector, batch multi-page capture, auto-capture countdown, Photo Picker & SAF PDF import).
  - `DocumentEdgeDetector` performs real-time luminance gradient & 4-corner quadrilateral detection with visual overlay and manual corner dragging.
  - `ImageProcessor` applies non-destructive perspective warp (`Matrix.setPolyToPoly`), rotation, and 16 enhancement filters (Original, Auto Enhance, Magic Color, B&W, Grayscale, Document Clear, Photo Mode, High Contrast, Brightness, Contrast, Sharpness, Saturation, Exposure, Noise Reduction, Shadow Reduction, Background Whitening).
- **PDF Engine (`pdf/`)**:
  - Powered by **PDFBox Android** (`com.tom-roush:pdfbox-android`) and Android `PdfRenderer` / `PrintManager`.
  - Multi-page PDF export (A4, A3, Letter, Legal, Custom; Portrait/Landscape; adjustable margins & JPEG quality; metadata title/author; Bengali-safe `PDType0Font` for watermarks, page numbers, and invisible searchable OCR layers; AES-128 password protection).
  - Merge, split by page range, reorder/rotate pages, compress, flatten annotations & handwritten signatures, export pages to JPG/PNG, system Print & Share Sheet, and Downloads export (`MediaStore`).
- **Offline OCR (`ocr/`)**:
  - Powered by **Tesseract4Android** (`4.7.0`).
  - Pre-bundled offline `tessdata_fast` models in `app/src/main/assets/tessdata/`: `ben.traineddata` (Bengali) and `eng.traineddata` (English) — works in Airplane Mode on first launch.
  - On-demand downloadable packs for Hindi (`hin`) and Arabic (`ara`) via `OcrModelsScreen`.
- **Local Storage & Security (`data/`, `security/`)**:
  - **Room** (`ScanProDatabase`) for documents, pages, folders, signatures, backup history, and activity logs.
  - **DataStore** (`SettingsRepository`) for user preferences and JSON settings export/import.
  - **SecurityManager**: Salted SHA-256 PIN lock, `BiometricPrompt`, and Android Keystore AES-256-GCM local vault encryption.
- **Optional Google Drive Backup (`backup/`)**:
  - Uses `AuthorizationClient` (`https://www.googleapis.com/auth/drive.file`, `email`, `profile`) and Drive REST v3 multipart upload/update, restore, remote wipe, and optional daily **WorkManager** sync (`BackupWorker`).

---

## Build & Test Commands

```bash
# Build debug APK
gradle :app:assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest
```

## Google Drive OAuth Notes

- Scope requested: `https://www.googleapis.com/auth/drive.file` (least-privilege access only to files created by ScanPro PDF in the `ScanPro PDF Backups` folder).
- For production Play Store / release distribution, register your app's package name (`com.aistudio.scanpropdf.mkrvtx`) and release/debug signing SHA-1 fingerprints in Google Cloud Console for the OAuth 2.0 Android client.

## Release Checklist

1. Verify `applicationId`, `versionCode`, and `versionName` in `app/build.gradle.kts`.
2. Confirm no broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`) are declared; media uses Android Photo Picker & SAF.
3. Verify offline `ben.traineddata` and `eng.traineddata` exist in `app/src/main/assets/tessdata/`.
4. Run `gradle :app:testDebugUnitTest` and `gradle :app:assembleRelease` with release signing credentials.
5. Test physical camera autofocus/flash, BiometricPrompt, PrintManager, and Google Drive OAuth consent on a physical Android device with Google Play Services.
