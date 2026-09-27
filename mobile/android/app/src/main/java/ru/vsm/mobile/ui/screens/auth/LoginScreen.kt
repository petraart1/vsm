package ru.vsm.mobile.ui.screens.auth

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.esiaOpen) "Госуслуги" else "Вход") },
                navigationIcon = {
                    if (state.esiaOpen) {
                        IconButton(onClick = viewModel::closeEsia) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.esiaOpen) {
            EsiaWebView(
                url = state.esiaAuthorizeUrl,
                exchanging = state.esiaExchanging,
                onRedirect = viewModel::onEsiaRedirect,
                modifier = Modifier.padding(padding),
            )
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

        OutlinedButton(onClick = viewModel::openEsia, modifier = Modifier.fillMaxWidth()) {
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

/**
 * Веб-вьюха демо-портала Госуслуг: грузит [url] от [ru.vsm.mobile.domain.repository.AuthRepository.esiaAuthorizeUrl]
 * и перехватывает редирект на схему приложения через [onRedirect] — сама страница дальше не
 * догружается, поверх показывается индикатор обмена кода на сессию.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EsiaWebView(
    url: String?,
    exchanging: Boolean,
    onRedirect: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (url != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                onRedirect(request.url.toString())
                        }
                        loadUrl(url)
                    }
                },
            )
        }
        if (exchanging) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
