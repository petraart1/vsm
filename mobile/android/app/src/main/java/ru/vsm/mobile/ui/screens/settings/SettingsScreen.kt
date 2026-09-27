package ru.vsm.mobile.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import ru.vsm.mobile.ui.components.VsmAvatar
import ru.vsm.mobile.ui.components.VsmAvatarTone
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.components.VsmButtonVariant
import ru.vsm.mobile.ui.components.VsmPageHeader
import ru.vsm.mobile.ui.components.VsmSegmentedControl
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.today.TodayStreak

/**
 * Настройки: учётная запись, оформление (тема), адрес backend для демо-стендов и локальные
 * данные устройства. Порядок разделов и состав — как на сайте (см. Settings.jsx), кроме блока
 * симуляции рейса — на мобильном клиенте у «Смены» нет отдельного локального движка, который бы
 * читал эти параметры. Тема хранится в [SettingsPreferences] — общий контракт с корневым экраном
 * приложения, который применяет её к теме Compose при следующем запуске процесса.
 */
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
    var confirmReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { VsmPageHeader(title = "Настройки") }

        item {
            AccountSection(
                currentUser = currentUser,
                navigator = navigator,
                onLogout = { scope.launch { container.authRepository.logout() } },
            )
        }

        item {
            SectionCard(title = "Оформление") {
                Text(text = "Тема", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                VsmSegmentedControl(
                    options = listOf(
                        ThemeChoice.SYSTEM to "Как в системе",
                        ThemeChoice.LIGHT to "Светлая",
                        ThemeChoice.DARK to "Тёмная",
                    ),
                    selected = theme,
                    onSelect = {
                        theme = it
                        SettingsPreferences.setThemeChoice(context, it)
                    },
                )
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
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VsmButton(
                            text = "Проверить соединение",
                            variant = VsmButtonVariant.Secondary,
                            size = VsmButtonSize.Small,
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
                        )
                        if (checking) CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
                    }
                    checkResult?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VsmButton(text = "Сохранить", size = VsmButtonSize.Small, onClick = {
                            SettingsPreferences.setApiBaseUrlOverride(context, apiBaseUrl)
                            apiUrlSaved = true
                        })
                        VsmButton(text = "Сбросить", variant = VsmButtonVariant.Secondary, size = VsmButtonSize.Small, onClick = {
                            apiBaseUrl = ""
                            SettingsPreferences.setApiBaseUrlOverride(context, null)
                            apiUrlSaved = true
                        })
                        VsmButton(text = "Перезапустить", variant = VsmButtonVariant.Ghost, size = VsmButtonSize.Small, onClick = { restartApp(context) })
                    }
                    if (apiUrlSaved) {
                        Text(
                            "Сохранено. Нажмите «Перезапустить» (или перезапустите приложение вручную), чтобы адрес начал использоваться.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Данные") {
                Text(
                    text = "История входов на этом устройстве и адрес сервера хранятся локально; прохождения и очки — на сервере.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (confirmReset) {
                    Text(
                        text = "Удалить локальные данные: серию входов и адрес сервера? Прогресс на сервере останется.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VsmButton(text = "Отмена", variant = VsmButtonVariant.Secondary, size = VsmButtonSize.Small, onClick = { confirmReset = false })
                        VsmButton(text = "Удалить", size = VsmButtonSize.Small, onClick = {
                            TodayStreak.clear(context)
                            SettingsPreferences.setApiBaseUrlOverride(context, null)
                            apiBaseUrl = ""
                            confirmReset = false
                            resetDone = true
                        })
                    }
                } else {
                    VsmButton(
                        text = "Очистить данные на устройстве",
                        variant = VsmButtonVariant.Secondary,
                        size = VsmButtonSize.Small,
                        onClick = { confirmReset = true; resetDone = false },
                    )
                }
                if (resetDone) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Готово — локальные данные очищены.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Text(
                text = "Тренажёр проводника ВСМ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                VsmAvatar(initials = initialsOf(currentUser.displayName ?: currentUser.login), size = 56.dp, tone = VsmAvatarTone.Solid)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(currentUser.displayName ?: currentUser.login, fontWeight = FontWeight.Medium)
                    Text(currentUser.login, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            VsmButton(text = "Выйти", variant = VsmButtonVariant.Secondary, size = VsmButtonSize.Small, onClick = onLogout)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Анонимный режим — прогресс хранится только на этом устройстве.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VsmButton(text = "Войти", size = VsmButtonSize.Small, onClick = { navigator.open(Routes.LOGIN) })
                    VsmButton(text = "Регистрация", variant = VsmButtonVariant.Secondary, size = VsmButtonSize.Small, onClick = { navigator.open(Routes.REGISTER) })
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" }

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
