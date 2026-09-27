package ru.vsm.mobile.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Вход / регистрация, демо-вход через Госуслуги и анонимное продолжение. После успешного входа
 * или отказа от него — переход на главную вкладку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: AuthViewModel = viewModel { AuthViewModel(container.authRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) navigator.openTab(Routes.TODAY)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Вход") }) }) { padding ->
        if (state.esiaOpen) {
            EsiaDemoPicker(
                busy = state.busy,
                error = state.error,
                onPick = viewModel::confirmEsiaDemo,
                onCancel = viewModel::closeEsiaDemo,
                modifier = Modifier.padding(padding),
            )
        } else if (state.esiaVerifiedName != null) {
            EsiaVerified(name = state.esiaVerifiedName!!, onContinue = viewModel::dismissEsiaVerified, modifier = Modifier.padding(padding))
        } else {
            AuthForm(state, viewModel, navigator, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun AuthForm(state: AuthUiState, viewModel: AuthViewModel, navigator: AppNavigator, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabRow(selectedTabIndex = if (state.tab == AuthTab.LOGIN) 0 else 1) {
            Tab(selected = state.tab == AuthTab.LOGIN, onClick = { viewModel.selectTab(AuthTab.LOGIN) }, text = { Text("Вход") })
            Tab(selected = state.tab == AuthTab.REGISTER, onClick = { viewModel.selectTab(AuthTab.REGISTER) }, text = { Text("Регистрация") })
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VsmPalette.success)
                Text("Подтверждённый аккаунт", fontWeight = FontWeight.Medium)
                Text(
                    "Очки без понижающего коэффициента, официальные награды и учёт результатов.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedButton(onClick = viewModel::openEsiaDemo, modifier = Modifier.fillMaxWidth()) {
            Text("Войти через Госуслуги (демо)")
        }

        if (state.tab == AuthTab.REGISTER) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::setDisplayName,
                label = { Text("Имя и фамилия (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.login,
            onValueChange = viewModel::setLogin,
            label = { Text("Логин") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.tab == AuthTab.REGISTER) {
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                label = { Text("Почта") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { if (state.tab == AuthTab.LOGIN) viewModel.submitLogin() else viewModel.submitRegister() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.busy && state.tab == AuthTab.LOGIN -> "Входим…"
                    state.busy -> "Создаём…"
                    state.tab == AuthTab.LOGIN -> "Войти"
                    else -> "Создать учётную запись"
                }
            )
        }

        OutlinedButton(onClick = { navigator.openTab(Routes.TODAY) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Text(" Продолжить без входа")
        }
        Text(
            "Без входа прогресс хранится только на этом устройстве, а очки начисляются с понижающим коэффициентом.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EsiaDemoPicker(
    busy: Boolean,
    error: String?,
    onPick: (EsiaDemoCitizen) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Госуслуги · демо", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Демонстрационный вход: выберите тестового гражданина. Это не настоящий портал и не проверка реальных учётных записей.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(ESIA_DEMO_CITIZENS) { citizen ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(citizen.fullName, fontWeight = FontWeight.Medium)
                    Text("СНИЛС ${citizen.snils}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onPick(citizen) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Проверяем…" else "Выбрать")
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
        }
    }
}

@Composable
private fun EsiaVerified(name: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VsmPalette.success)
        Text("Личность подтверждена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("$name — теперь полноценный участник: очки без коэффициента и официальные награды.")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Продолжить") }
    }
}
