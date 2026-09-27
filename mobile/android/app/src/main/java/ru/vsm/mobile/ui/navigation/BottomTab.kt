package ru.vsm.mobile.ui.navigation

import androidx.annotation.DrawableRes
import ru.vsm.mobile.R

/** Один пункт нижнего таб-бара — иконка ic_* сайта. */
data class BottomTab(val route: String, val label: String, @DrawableRes val iconRes: Int)

/** Порядок и состав вкладок нижнего таб-бара — как `Chrome.jsx` (TABS): Сегодня/Смена/Тренировки/
 * Награды/Рейтинг. Профиль на мобильной ширине сайта достижим через аватар в шапке, не таб-бар. */
val bottomTabs = listOf(
    BottomTab(Routes.TODAY, "Сегодня", R.drawable.ic_today),
    BottomTab(Routes.SHIFT, "Смена", R.drawable.ic_train),
    BottomTab(Routes.SCENARIOS, "Тренировки", R.drawable.ic_list),
    BottomTab(Routes.AWARDS, "Награды", R.drawable.ic_medal),
    BottomTab(Routes.LEADERBOARD, "Рейтинг", R.drawable.ic_podium),
)

/**
 * Маршруты без таб-бара/шапки — иммерсивные (вход/регистрация, прохождение сценария/экзамена,
 * разбор). «Смена» — НЕ здесь: по требованию таб-бар остаётся виден на всех её этапах, мини-шапку
 * этапа рисует сам экран поверх обычной.
 */
val fullScreenRoutes = setOf(
    Routes.PLAY_TEMPLATE,
    Routes.DEBRIEF_TEMPLATE,
    Routes.EXAM,
    Routes.LOGIN,
    Routes.REGISTER,
)
