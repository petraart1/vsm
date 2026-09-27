package ru.vsm.mobile.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes

/**
 * Регистрация — отдельный экран (см. [LoginScreen] для входа), альтернатива — сразу через
 * Госуслуги. После успеха — переход на главную вкладку.
 */
@Composable
fun RegisterScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) navigator.openTab(Routes.TODAY)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AuthTopBar(onBack = navigator::back)
        if (state.esiaOpen) {
            EsiaWebView(
                url = state.esiaAuthorizeUrl,
                exchanging = state.esiaExchanging,
                onRedirect = viewModel::onEsiaRedirect,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Регистрация", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Уже пройденные смены и тренировки с этого устройства перейдут в новую учётную запись.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::setDisplayName,
                        label = { Text("Имя и фамилия") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Так вас увидят коллеги в рейтинге",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }

                OutlinedTextField(
                    value = state.login,
                    onValueChange = viewModel::setLogin,
                    label = { Text("Логин") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::setEmail,
                    label = { Text("Почта") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = "Пароль",
                    supportingText = "Минимум $MIN_PASSWORD_LENGTH символов",
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setConsentGiven(!state.consentGiven) },
                ) {
                    Checkbox(checked = state.consentGiven, onCheckedChange = viewModel::setConsentGiven)
                    Text(
                        text = "Согласен на обработку персональных данных для целей обучения (152-ФЗ)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                VsmButton(
                    text = if (state.busy) "Создаём…" else "Создать учётную запись",
                    onClick = viewModel::submitRegister,
                    enabled = !state.busy,
                    size = VsmButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )

                AuthDivider(text = "ИЛИ СРАЗУ С ПОДТВЕРЖДЕНИЕМ")

                EsiaEntryRow(onClick = viewModel::openEsia)

                Text(
                    text = "Уже есть учётная запись? Войти",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.open(Routes.LOGIN) }
                        .padding(vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
