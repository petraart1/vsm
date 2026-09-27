package ru.vsm.mobile.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences

/** Выбор темы приложения, сохраняемый на устройстве. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

private const val PREFS_NAME = "vsm.settings"
private const val KEY_THEME = "theme_choice"
private const val KEY_API_BASE_URL = "api_base_url_override"

/**
 * Настройки, хранящиеся локально на устройстве: тема оформления и (для демо-стендов) адрес
 * backend поверх значения по умолчанию из сборки. Корневой экран приложения (вне этого модуля)
 * может читать [themeChoice] при построении темы; смена адреса сервера применяется при следующем
 * запуске процесса — DI-контейнер собирается один раз при старте.
 */
object SettingsPreferences {

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
    }

    fun apiBaseUrlOverride(context: Context): String? = prefs(context).getString(KEY_API_BASE_URL, null)

    fun setApiBaseUrlOverride(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_API_BASE_URL, value?.ifBlank { null }).apply()
    }
}
