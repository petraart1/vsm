package ru.vsm.mobile.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** Выбор темы приложения, сохраняемый на устройстве. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME = "vsm.settings"
private const val KEY_THEME = "theme_choice"
private const val KEY_API_BASE_URL = "api_base_url_override"

/**
 * Настройки, хранящиеся локально на устройстве: тема оформления и (для демо-стендов) адрес
 * backend поверх значения по умолчанию из сборки. [themeChoiceState] — реактивное Compose-состояние
 * поверх сохранённого выбора темы, которое читает корневой экран приложения при построении
 * [ru.vsm.mobile.ui.theme.VsmTheme], поэтому смена темы применяется сразу, без перезапуска;
 * смена адреса сервера по-прежнему применяется только при следующем запуске процесса — DI-контейнер
 * собирается один раз при старте.
 */
object SettingsPreferences {

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeChoiceState = mutableStateOf(ThemeChoice.SYSTEM)

    /** Текущий выбор темы — читать в составе Compose-дерева, чтобы реагировать на смену без перезапуска. */
    val themeChoiceState: State<ThemeChoice> get() = _themeChoiceState

    /** Читает сохранённый выбор темы и синхронизирует [themeChoiceState] — вызывать один раз при старте процесса. */
    fun loadThemeChoice(context: Context): ThemeChoice {
        val value = themeChoice(context)
        _themeChoiceState.value = value
        return value
    }

    fun themeChoice(context: Context): ThemeChoice =
        when (prefs(context).getString(KEY_THEME, null)) {
            "light" -> ThemeChoice.LIGHT
            "dark" -> ThemeChoice.DARK
            else -> ThemeChoice.SYSTEM
        }

    fun setThemeChoice(context: Context, choice: ThemeChoice) {
        prefs(context).edit().putString(
            KEY_THEME,
            when (choice) {
                ThemeChoice.LIGHT -> "light"
                ThemeChoice.DARK -> "dark"
                ThemeChoice.SYSTEM -> "system"
            },
        ).apply()
        _themeChoiceState.value = choice
    }

    fun apiBaseUrlOverride(context: Context): String? = prefs(context).getString(KEY_API_BASE_URL, null)

    fun setApiBaseUrlOverride(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_API_BASE_URL, value?.let(::normalizeBaseUrl)).apply()
    }

    /**
     * Retrofit требует, чтобы базовый URL заканчивался `/` (иначе `IllegalArgumentException` при
     * сборке [retrofit2.Retrofit] в [ru.vsm.mobile.di.AppContainer]) — сохранённое человеком
     * значение может прийти без него. `null`/пустая строка после `trim()` — сброс к значению по
     * умолчанию из сборки.
     */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
