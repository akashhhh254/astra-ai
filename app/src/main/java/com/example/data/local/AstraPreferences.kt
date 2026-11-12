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
    val thinkingLevel: String = "Medium",
    val defaultModel: String = "gemini-3.8-flash",
    val webSearchEnabled: Boolean = true,
    val showSources: Boolean = true,
    val fontSize: String = "Medium",
    val themeMode: AppThemeMode = AppThemeMode.DARK
)

object AstraPreferences {
    private const val PREFS_NAME = "astra_ai_preferences"
    private const val KEY_THEME = "pref_theme_mode"
    private const val KEY_LANG = "pref_language"
    private const val KEY_TONE = "pref_response_tone"
    private const val KEY_LENGTH = "pref_response_length"
    private const val KEY_THINKING = "pref_thinking_level"
    private const val KEY_MODEL = "pref_default_model"
    private const val KEY_WEB_SEARCH = "pref_web_search"
    private const val KEY_SHOW_SOURCES = "pref_show_sources"
    private const val KEY_FONT_SIZE = "pref_font_size"

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
        val thinking = prefs.getString(KEY_THINKING, "Medium") ?: "Medium"
        val model = prefs.getString(KEY_MODEL, "gemini-3.8-flash") ?: "gemini-3.8-flash"
        val webSearch = prefs.getBoolean(KEY_WEB_SEARCH, true)
        val showSources = prefs.getBoolean(KEY_SHOW_SOURCES, true)
        val fontSize = prefs.getString(KEY_FONT_SIZE, "Medium") ?: "Medium"

        _themeMode.value = theme
        _settings.value = PersonalizationSettings(
            language = lang,
            responseLength = length,
            responseTone = tone,
            thinkingLevel = thinking,
            defaultModel = model,
            webSearchEnabled = webSearch,
            showSources = showSources,
            fontSize = fontSize,
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
        responseLength: String,
        thinkingLevel: String = _settings.value.thinkingLevel,
        defaultModel: String = _settings.value.defaultModel,
        webSearchEnabled: Boolean = _settings.value.webSearchEnabled,
        showSources: Boolean = _settings.value.showSources,
        fontSize: String = _settings.value.fontSize
    ) {
        _settings.value = _settings.value.copy(
            language = language,
            responseTone = responseTone,
            responseLength = responseLength,
            thinkingLevel = thinkingLevel,
            defaultModel = defaultModel,
            webSearchEnabled = webSearchEnabled,
            showSources = showSources,
            fontSize = fontSize
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LANG, language)
            .putString(KEY_TONE, responseTone)
            .putString(KEY_LENGTH, responseLength)
            .putString(KEY_THINKING, thinkingLevel)
            .putString(KEY_MODEL, defaultModel)
            .putBoolean(KEY_WEB_SEARCH, webSearchEnabled)
            .putBoolean(KEY_SHOW_SOURCES, showSources)
            .putString(KEY_FONT_SIZE, fontSize)
            .apply()
    }
}
