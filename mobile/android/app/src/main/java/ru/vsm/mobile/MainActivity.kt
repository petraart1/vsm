package ru.vsm.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import ru.vsm.mobile.ui.navigation.VsmNavHost
import ru.vsm.mobile.ui.screens.settings.SettingsPreferences
import ru.vsm.mobile.ui.screens.settings.ThemeChoice
import ru.vsm.mobile.ui.theme.VsmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsPreferences.loadThemeChoice(this)
        setContent {
            val themeChoice by SettingsPreferences.themeChoiceState
            val darkTheme = when (themeChoice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            VsmTheme(darkTheme = darkTheme) {
                VsmNavHost()
            }
        }
    }
}
