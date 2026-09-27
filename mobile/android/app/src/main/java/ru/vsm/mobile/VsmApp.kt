package ru.vsm.mobile

import android.app.Application
import ru.vsm.mobile.di.AppContainer

/**
 * Точка входа процесса — держит единственный экземпляр [AppContainer] на всё время жизни
 * приложения. Экраны и навигация получают его через [ru.vsm.mobile.ui.common.appContainer].
 */
class VsmApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(context = this)
    }
}
