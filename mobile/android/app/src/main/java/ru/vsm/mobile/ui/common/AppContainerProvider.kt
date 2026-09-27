package ru.vsm.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ru.vsm.mobile.VsmApp
import ru.vsm.mobile.di.AppContainer

/** Достаёт [AppContainer] приложения из [LocalContext] — используется вьюмоделями экранов. */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return (context as VsmApp).container
}
