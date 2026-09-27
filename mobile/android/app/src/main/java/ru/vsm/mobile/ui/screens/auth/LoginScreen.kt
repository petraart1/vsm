package ru.vsm.mobile.ui.screens.auth

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.R
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes

/**
 * Вход по логину/паролю, вход через Госуслуги (демо) и анонимное продолжение — отдельный экран
 * (см. [RegisterScreen] для регистрации). После успешного входа — переход на главную вкладку.
 */
@Composable
fun LoginScreen(navigator: AppNavigator) {
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
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Вход", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Войдите, чтобы прогресс, награды и допуск сохранялись в учётной записи.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                EsiaEntryRow(onClick = viewModel::openEsia)

                AuthDivider(text = "ИЛИ ПО ЛОГИНУ")

                OutlinedTextField(
                    value = state.login,
                    onValueChange = viewModel::setLogin,
                    label = { Text("Логин") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(value = state.password, onValueChange = viewModel::setPassword, label = "Пароль")

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                VsmButton(
                    text = if (state.busy) "Входим…" else "Войти",
                    onClick = viewModel::submitLogin,
                    enabled = !state.busy,
                    size = VsmButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Нет учётной записи? Зарегистрироваться",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.open(Routes.REGISTER) }
                        .padding(vertical = 4.dp),
                )

                Text(
                    text = "Продолжить без входа",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.openTab(Routes.TODAY) }
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = "Без входа прогресс хранится только на этом устройстве, а очки начисляются с понижающим коэффициентом.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/** Мини-шапка immersive-экранов входа/регистрации: кружок-назад слева, без заголовка (как на сайте). */
@Composable
internal fun AuthTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "Назад",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Плашка входа через Госуслуги — знак+подпись+шеврон, как на сайте. */
@Composable
internal fun EsiaEntryRow(onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_gosuslugi_mark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(text = "Войти через Госуслуги", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Подтверждает личность · демо-стенд",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Разделитель «ИЛИ …» между входом через Госуслуги и формой по логину/паролю. */
@Composable
internal fun AuthDivider(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Поле пароля с переключателем видимости (иконка-глаз, как на сайте). */
@Composable
internal fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, supportingText: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(R.drawable.ic_eye),
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль",
                    tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Веб-вьюха демо-портала Госуслуг: грузит [url] от [ru.vsm.mobile.domain.repository.AuthRepository.esiaAuthorizeUrl]
 * и перехватывает редирект на схему приложения через [onRedirect] — сама страница дальше не
 * догружается, поверх показывается индикатор обмена кода на сессию.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EsiaWebView(
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
