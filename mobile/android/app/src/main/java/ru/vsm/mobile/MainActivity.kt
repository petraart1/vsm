package ru.vsm.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.vsm.mobile.ui.navigation.VsmNavHost
import ru.vsm.mobile.ui.theme.VsmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VsmTheme {
                VsmNavHost()
            }
        }
    }
}
