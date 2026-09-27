package ru.vsm.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector

/** Один пункт нижнего таб-бара. */
data class BottomTab(val route: String, val label: String, val icon: ImageVector)

/** Порядок и состав вкладок нижнего таб-бара (Сегодня, Тренировки, Смена, Награды, Профиль). */
val bottomTabs = listOf(
    BottomTab(Routes.TODAY, "Сегодня", Icons.Filled.Today),
    BottomTab(Routes.SCENARIOS, "Тренировки", Icons.Filled.PlayArrow),
    BottomTab(Routes.SHIFT, "Смена", Icons.Filled.Train),
    BottomTab(Routes.AWARDS, "Награды", Icons.Filled.EmojiEvents),
    BottomTab(Routes.PROFILE, "Профиль", Icons.Filled.Person),
)

/** Маршруты, которые занимают весь экран — без таб-бара и верхней шапки. */
val fullScreenRoutes = setOf(
    Routes.PLAY_TEMPLATE,
    Routes.DEBRIEF_TEMPLATE,
    Routes.EXAM,
    Routes.LOGIN,
    Routes.SHIFT,
)
