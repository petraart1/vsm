package ru.vsm.mobile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import ru.vsm.mobile.ui.navigation.VsmNavHost
import ru.vsm.mobile.ui.screens.settings.SettingsPreferences
import ru.vsm.mobile.ui.screens.settings.ThemeChoice
import ru.vsm.mobile.ui.theme.VsmTheme

class MainActivity : ComponentActivity() {
    private val requestLocalNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handle result; permission granted/denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Request ACCESS_LOCAL_NETWORK permission on API 37+
        if (Build.VERSION.SDK_INT >= 37) {
            if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestLocalNetworkPermission.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
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
