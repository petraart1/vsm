package ru.vsm.mobile.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.AuthUser
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes

/**
 * Настройки: тема оформления, учётная запись (выход) и адрес backend для демо-стендов. Тема
 * хранится в [SettingsPreferences] — общий контракт с корневым экраном приложения, который
 * применяет её к теме Compose при следующем запуске процесса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navigator: AppNavigator) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentUser by container.authRepository.currentUser.collectAsState(initial = null)

    var theme by remember { mutableStateOf(SettingsPreferences.themeChoice(context)) }
    var apiBaseUrl by remember { mutableStateOf(SettingsPreferences.apiBaseUrlOverride(context) ?: "") }
    var apiUrlSaved by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AccountSection(
                    currentUser = currentUser,
                    navigator = navigator,
                    onLogout = { scope.launch { container.authRepository.logout() } },
                )
            }
            item {
                SectionCard(title = "Оформление") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Тема", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption(ThemeChoice.SYSTEM, "Как в системе", theme) {
                                theme = it
                                SettingsPreferences.setThemeChoice(context, it)
                            }
                            ThemeOption(ThemeChoice.LIGHT, "Светлая", theme) {
                                theme = it
                                SettingsPreferences.setThemeChoice(context, it)
                            }
                            ThemeOption(ThemeChoice.DARK, "Тёмная", theme) {
                                theme = it
                                SettingsPreferences.setThemeChoice(context, it)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Сервер") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Сейчас используется: ${container.activeBaseUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Переопределяет значение по умолчанию из сборки — нужно для реального устройства" +
                                " (не эмулятора) или другого демо-стенда.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = apiBaseUrl,
                            onValueChange = { apiBaseUrl = it; apiUrlSaved = false; checkResult = null },
                            label = { Text("Базовый URL (например http://10.0.2.2:8080/)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !checking,
                                onClick = {
                                    checking = true
                                    checkResult = null
                                    scope.launch {
                                        val target = apiBaseUrl.ifBlank { container.activeBaseUrl }
                                        val outcome = container.connectionChecker.check(target)
                                        checkResult = when {
                                            outcome.success -> "OK ${outcome.statusCode} · ${outcome.elapsedMs} мс"
                                            outcome.statusCode != null -> "Ошибка HTTP ${outcome.statusCode} · ${outcome.elapsedMs} мс"
                                            else -> "Нет ответа: ${outcome.errorMessage}"
                                        }
                                        checking = false
                                    }
                                },
                            ) { Text("Проверить соединение") }
                            if (checking) {
                                CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        checkResult?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                SettingsPreferences.setApiBaseUrlOverride(context, apiBaseUrl)
                                apiUrlSaved = true
                            }) { Text("Сохранить") }
                            OutlinedButton(onClick = {
                                apiBaseUrl = ""
                                SettingsPreferences.setApiBaseUrlOverride(context, null)
                                apiUrlSaved = true
                            }) { Text("Сбросить") }
                            OutlinedButton(onClick = { restartApp(context) }) { Text("Применить и перезапустить") }
                        }
                        if (apiUrlSaved) {
                            Text(
                                "Сохранено. Нажмите «Применить и перезапустить» (или перезапустите приложение вручную), чтобы адрес начал использоваться.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    currentUser: AuthUser?,
    navigator: AppNavigator,
    onLogout: () -> Unit,
) {
    SectionCard(title = "Учётная запись") {
        if (currentUser != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(currentUser.displayName ?: currentUser.login, fontWeight = FontWeight.Medium)
                Text(currentUser.login, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onLogout) { Text("Выйти") }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Анонимный режим — прогресс хранится только на этом устройстве.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { navigator.open(Routes.LOGIN) }) { Text("Войти") }
            }
        }
    }
}

@Composable
private fun ThemeOption(value: ThemeChoice, label: String, current: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    FilterChip(selected = current == value, onClick = { onSelect(value) }, label = { Text(label) })
}

/**
 * Перезапускает процесс приложения целиком, чтобы новый адрес backend из [SettingsPreferences]
 * подхватился при следующей сборке [ru.vsm.mobile.di.AppContainer] в [ru.vsm.mobile.VsmApp.onCreate]
 * (он собирается один раз за жизнь процесса, обычного пересоздания Activity недостаточно).
 */
private fun restartApp(context: android.content.Context) {
    val packageManager = context.packageManager
    val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
    context.startActivity(restartIntent)
    Runtime.getRuntime().exit(0)
}
