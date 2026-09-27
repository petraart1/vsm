package ru.vsm.mobile

import android.app.Application
import ru.vsm.mobile.di.AppContainer
import ru.vsm.mobile.ui.screens.settings.SettingsPreferences

/**
 * Точка входа процесса — держит единственный экземпляр [AppContainer] на всё время жизни
 * приложения. Экраны и навигация получают его через [ru.vsm.mobile.ui.common.appContainer].
 */
class VsmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Адрес backend, сохранённый на экране настроек (демо-стенды/реальное устройство),
        // приоритетнее значения из сборки ([BuildConfig.BACKEND_BASE_URL]) — но применяется только
        // на следующем создании процесса (DI-контейнер собирается один раз здесь), поэтому смена
        // адреса на экране настроек требует перезапуска приложения (см. `SettingsScreen`).
        val overrideBaseUrl = SettingsPreferences.apiBaseUrlOverride(this)
        container = if (overrideBaseUrl != null) {
            AppContainer(context = this, baseUrl = overrideBaseUrl)
        } else {
            AppContainer(context = this)
        }
    }
}
