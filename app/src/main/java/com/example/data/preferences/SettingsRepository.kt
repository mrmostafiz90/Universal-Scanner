package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettingsSnapshot
import com.example.data.model.AppThemeMode
import com.example.data.model.CameraResolution
import com.example.data.model.FilterType
import com.example.data.model.OcrLanguage
import com.example.data.model.PdfPageSize
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanpro_settings")

data class UserSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultPageSize: PdfPageSize = PdfPageSize.A4,
    val defaultQuality: Int = 85,
    val defaultFilter: FilterType = FilterType.DOCUMENT_CLEAR,
    val autoCapture: Boolean = false,
    val autoCrop: Boolean = true,
    val defaultOcrLang: OcrLanguage = OcrLanguage.BEN_ENG,
    val cameraResolution: CameraResolution = CameraResolution.STANDARD,
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val localEncryptionEnabled: Boolean = false,
    val driveAccountEmail: String? = null,
    val autoBackupEnabled: Boolean = false,
    val includeSettingsInBackup: Boolean = true,
    val lastBackupAt: Long? = null
)

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val DEFAULT_FILTER = stringPreferencesKey("default_filter")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val AUTO_CROP = booleanPreferencesKey("auto_crop")
        val DEFAULT_OCR_LANG = stringPreferencesKey("default_ocr_lang")
        val CAMERA_RESOLUTION = stringPreferencesKey("camera_resolution")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val LOCAL_ENCRYPTION = booleanPreferencesKey("local_encryption")
        val DRIVE_EMAIL = stringPreferencesKey("drive_email")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val INCLUDE_SETTINGS_BACKUP = booleanPreferencesKey("include_settings_backup")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = AppThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            defaultPageSize = runCatching {
                PdfPageSize.valueOf(prefs[Keys.DEFAULT_PAGE_SIZE] ?: "A4")
            }.getOrDefault(PdfPageSize.A4),
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: 85,
            defaultFilter = runCatching {
                FilterType.valueOf(prefs[Keys.DEFAULT_FILTER] ?: "DOCUMENT_CLEAR")
            }.getOrDefault(FilterType.DOCUMENT_CLEAR),
            autoCapture = prefs[Keys.AUTO_CAPTURE] ?: false,
            autoCrop = prefs[Keys.AUTO_CROP] ?: true,
            defaultOcrLang = runCatching {
                OcrLanguage.valueOf(prefs[Keys.DEFAULT_OCR_LANG] ?: "BEN_ENG")
            }.getOrDefault(OcrLanguage.BEN_ENG),
            cameraResolution = CameraResolution.fromKey(prefs[Keys.CAMERA_RESOLUTION]),
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            localEncryptionEnabled = prefs[Keys.LOCAL_ENCRYPTION] ?: false,
            driveAccountEmail = prefs[Keys.DRIVE_EMAIL],
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP] ?: false,
            includeSettingsInBackup = prefs[Keys.INCLUDE_SETTINGS_BACKUP] ?: true,
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT]
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setDefaultPageSize(size: PdfPageSize) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.coerceIn(40, 100) }
    }

    suspend fun setDefaultFilter(filter: FilterType) {
        context.dataStore.edit { it[Keys.DEFAULT_FILTER] = filter.name }
    }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    }

    suspend fun setAutoCrop(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CROP] = enabled }
    }

    suspend fun setDefaultOcrLang(lang: OcrLanguage) {
        context.dataStore.edit { it[Keys.DEFAULT_OCR_LANG] = lang.name }
    }

    suspend fun setCameraResolution(res: CameraResolution) {
        context.dataStore.edit { it[Keys.CAMERA_RESOLUTION] = res.key }
    }

    suspend fun setPin(enabled: Boolean, hash: String, salt: String) {
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = enabled
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
        }
    }

    suspend fun disablePin() {
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = false
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setLocalEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCAL_ENCRYPTION] = enabled }
    }

    suspend fun setDriveAccount(email: String?) {
        context.dataStore.edit {
            if (email == null) it.remove(Keys.DRIVE_EMAIL) else it[Keys.DRIVE_EMAIL] = email
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setIncludeSettingsInBackup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_SETTINGS_BACKUP] = enabled }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }

    suspend fun exportSettingsJson(): String {
        val current = settingsFlow.first()
        val snap = AppSettingsSnapshot(
            languageCode = current.language.code,
            themeMode = current.themeMode.key,
            defaultPageSize = current.defaultPageSize.name,
            defaultQuality = current.defaultQuality,
            defaultFilter = current.defaultFilter.name,
            autoCapture = current.autoCapture,
            autoCrop = current.autoCrop,
            defaultOcrLang = current.defaultOcrLang.name,
            autoBackupEnabled = current.autoBackupEnabled,
            includeSettingsInBackup = current.includeSettingsInBackup
        )
        return json.encodeToString(snap)
    }

    suspend fun importSettingsJson(rawJson: String): Boolean {
        return runCatching {
            val snap = json.decodeFromString<AppSettingsSnapshot>(rawJson)
            context.dataStore.edit {
                it[Keys.LANGUAGE] = AppLanguage.fromCode(snap.languageCode).code
                it[Keys.THEME_MODE] = AppThemeMode.fromKey(snap.themeMode).key
                it[Keys.DEFAULT_PAGE_SIZE] = snap.defaultPageSize
                it[Keys.DEFAULT_QUALITY] = snap.defaultQuality.coerceIn(40, 100)
                it[Keys.DEFAULT_FILTER] = snap.defaultFilter
                it[Keys.AUTO_CAPTURE] = snap.autoCapture
                it[Keys.AUTO_CROP] = snap.autoCrop
                it[Keys.DEFAULT_OCR_LANG] = snap.defaultOcrLang
                it[Keys.AUTO_BACKUP] = snap.autoBackupEnabled
                it[Keys.INCLUDE_SETTINGS_BACKUP] = snap.includeSettingsInBackup
            }
            true
        }.getOrDefault(false)
    }
}
