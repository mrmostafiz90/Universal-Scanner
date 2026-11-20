package com.example.localization

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    BENGALI("bn", "বাংলা");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
