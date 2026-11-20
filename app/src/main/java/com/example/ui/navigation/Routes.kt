package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Documents : AppRoute

    @Serializable
    data class Scan(val appendToDocId: String? = null) : AppRoute

    @Serializable
    data object Tools : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class PageEditor(val docId: String, val initialPageIndex: Int = 0) : AppRoute

    @Serializable
    data class PdfReader(val docId: String) : AppRoute

    @Serializable
    data class OcrStudio(val docId: String, val initialPageIndex: Int = 0) : AppRoute

    @Serializable
    data object SignatureStudio : AppRoute

    @Serializable
    data object OcrModels : AppRoute

    @Serializable
    data object DriveBackup : AppRoute
}
