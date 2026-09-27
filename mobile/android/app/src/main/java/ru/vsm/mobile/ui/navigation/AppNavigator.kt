package ru.vsm.mobile.ui.navigation

/** Абстракция над навстеком — экраны не зависят от androidx.navigation напрямую. */
interface AppNavigator {
    /** Обычный переход вперёд (кладёт назначение в back stack). */
    fun open(route: String)

    /** Возврат на предыдущий экран. */
    fun back()

    /** Переход между вкладками нижнего таб-бара — без накопления back stack между вкладками. */
    fun openTab(route: String)
}
