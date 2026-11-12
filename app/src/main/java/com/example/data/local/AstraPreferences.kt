package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppThemeMode {
    SYSTEM,
    DARK,
    LIGHT
}

data class PersonalizationSettings(
    val language: String = "English",
    val responseLength: String = "Balanced",
    val responseTone: String = "Professional",
    val themeMode: AppThemeMode = AppThemeMode.DARK
)

object AstraPreferences {
    private const val PREFS_NAME = "astra_ai_preferences"
    private const val KEY_THEME = "pref_theme_mode"
    private const val KEY_LANG = "pref_language"
    private const val KEY_TONE = "pref_response_tone"
    private const val KEY_LENGTH = "pref_response_length"

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode

    private val _settings = MutableStateFlow(PersonalizationSettings())
    val settings: StateFlow<PersonalizationSettings> = _settings

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val theme = try {
            AppThemeMode.valueOf(savedTheme)
        } catch (e: Exception) {
            AppThemeMode.DARK
        }
        val lang = prefs.getString(KEY_LANG, "English") ?: "English"
        val tone = prefs.getString(KEY_TONE, "Professional") ?: "Professional"
        val length = prefs.getString(KEY_LENGTH, "Balanced") ?: "Balanced"

        _themeMode.value = theme
        _settings.value = PersonalizationSettings(
            language = lang,
            responseLength = length,
            responseTone = tone,
            themeMode = theme
        )
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        _settings.value = _settings.value.copy(themeMode = mode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun updatePersonalization(
        context: Context,
        language: String,
        responseTone: String,
        responseLength: String
    ) {
        _settings.value = _settings.value.copy(
            language = language,
            responseTone = responseTone,
            responseLength = responseLength
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LANG, language)
            .putString(KEY_TONE, responseTone)
            .putString(KEY_LENGTH, responseLength)
            .apply()
    }
}
