package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    ORIGINAL,
    AUTO_ENHANCE,
    MAGIC_COLOR,
    BLACK_WHITE,
    GRAYSCALE,
    DOCUMENT_CLEAR,
    PHOTO_MODE,
    HIGH_CONTRAST,
    BRIGHTNESS,
    CONTRAST,
    SHARPNESS,
    SATURATION,
    EXPOSURE,
    NOISE_REDUCTION,
    SHADOW_REDUCTION,
    BACKGROUND_WHITENING
}

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

@Serializable
data class CropQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0f, 0f),
    val topRight: NormalizedPoint = NormalizedPoint(1f, 0f),
    val bottomRight: NormalizedPoint = NormalizedPoint(1f, 1f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0f, 1f)
) {
    fun isFullFrame(): Boolean =
        topLeft.x <= 0.01f && topLeft.y <= 0.01f &&
            topRight.x >= 0.99f && topRight.y <= 0.01f &&
            bottomRight.x >= 0.99f && bottomRight.y >= 0.99f &&
            bottomLeft.x <= 0.01f && bottomLeft.y >= 0.99f
}

@Serializable
data class ImageAdjustments(
    val filterType: FilterType = FilterType.DOCUMENT_CLEAR,
    val intensity: Float = 1.0f,
    val brightness: Float = 0f,          // -100..100
    val contrast: Float = 1.0f,          // 0.5..2.0
    val sharpness: Float = 0.25f,        // 0.0..1.0
    val saturation: Float = 1.0f,        // 0.0..2.0
    val exposure: Float = 0f,            // -1.0..1.0
    val noiseReduction: Float = 0f,      // 0.0..1.0
    val shadowReduction: Float = 0.2f,   // 0.0..1.0
    val backgroundWhitening: Float = 0.2f, // 0.0..1.0
    val rotationDegrees: Int = 0,        // 0, 90, 180, 270
    val cropQuad: CropQuad = CropQuad()
)

@Serializable
enum class PdfPageSize(val widthPt: Float, val heightPt: Float) {
    A4(595f, 842f),
    A3(842f, 1191f),
    LETTER(612f, 792f),
    LEGAL(612f, 1008f),
    CUSTOM(595f, 842f)
}

@Serializable
enum class PdfOrientation {
    PORTRAIT,
    LANDSCAPE
}

@Serializable
data class PdfExportOptions(
    val title: String = "Scanned Document",
    val author: String = "ScanPro PDF",
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val marginMm: Float = 8f,
    val quality: Int = 85,
    val password: String = "",
    val watermarkText: String = "",
    val includePageNumbers: Boolean = true,
    val searchableOcr: Boolean = true
)

@Serializable
enum class OcrLanguage(val tessCode: String, val downloadFileName: String?) {
    ENG("eng", "eng.traineddata"),
    BEN("ben", "ben.traineddata"),
    BEN_ENG("ben+eng", null),
    HIN("hin", "hin.traineddata"),
    ARA("ara", "ara.traineddata")
}

data class OcrModelStatus(
    val code: String,
    val displayName: String,
    val isInstalled: Boolean,
    val sizeBytes: Long,
    val isBundled: Boolean
)

enum class SortOrder {
    MODIFIED_DESC,
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC
}

enum class ViewMode {
    GRID,
    LIST
}

enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class CameraResolution(val key: String, val maxLongEdgePx: Int, val label: String) {
    HIGH("high", 2480, "High (2480p)"),
    STANDARD("standard", 1754, "Standard (1754p)"),
    COMPACT("compact", 1240, "Compact (1240p)");

    companion object {
        fun fromKey(key: String?): CameraResolution =
            entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

@Serializable
enum class AnnotationKind {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    FREEHAND,
    TEXT_BOX,
    STICKY_NOTE,
    SHAPE_RECT,
    SIGNATURE
}

@Serializable
data class PdfAnnotationItem(
    val id: String,
    val pageIndex: Int,
    val kind: AnnotationKind,
    val points: List<NormalizedPoint> = emptyList(),
    val x: Float = 0.1f,
    val y: Float = 0.1f,
    val width: Float = 0.3f,
    val height: Float = 0.1f,
    val rotationDeg: Float = 0f,
    val colorArgb: Long = 0xFFFFEB3B,
    val strokeWidthNorm: Float = 0.006f,
    val text: String = "",
    val signaturePath: String = ""
)

@Serializable
data class AppSettingsSnapshot(
    val languageCode: String = "en",
    val themeMode: String = "system",
    val defaultPageSize: String = "A4",
    val defaultQuality: Int = 85,
    val defaultFilter: String = "DOCUMENT_CLEAR",
    val autoCapture: Boolean = false,
    val autoCrop: Boolean = true,
    val defaultOcrLang: String = "BEN_ENG",
    val autoBackupEnabled: Boolean = false,
    val includeSettingsInBackup: Boolean = true
)
