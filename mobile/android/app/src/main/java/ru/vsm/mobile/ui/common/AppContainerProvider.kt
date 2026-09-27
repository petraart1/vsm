package ru.vsm.mobile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel as androidxViewModel
import ru.vsm.mobile.VsmApp
import ru.vsm.mobile.di.AppContainer

/** Достаёт [AppContainer] приложения из [LocalContext] — используется вьюмоделями экранов. */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current.applicationContext
    return (context as VsmApp).container
}

/**
 * Тонкая обёртка над `androidx.lifecycle.viewmodel.compose.viewModel` — создаёт вьюмодель через
 * простой инициализатор без CreationExtras, единый способ конструирования вьюмоделей экранов
 * вручную (без Hilt), поверх [appContainer].
 */
@Composable
inline fun <reified VM : ViewModel> viewModel(noinline initializer: () -> VM): VM =
    androidxViewModel { initializer() }
