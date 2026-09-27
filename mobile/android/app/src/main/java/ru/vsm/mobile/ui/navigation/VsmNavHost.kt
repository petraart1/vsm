package ru.vsm.mobile.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.NotificationBell
import ru.vsm.mobile.ui.components.VsmAvatar
import ru.vsm.mobile.ui.components.VsmAvatarTone
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.components.VsmButtonVariant
import ru.vsm.mobile.ui.components.VsmMotion
import ru.vsm.mobile.ui.components.pressScale
import ru.vsm.mobile.ui.theme.VsmPalette
import ru.vsm.mobile.ui.screens.auth.LoginScreen
import ru.vsm.mobile.ui.screens.auth.RegisterScreen
import ru.vsm.mobile.ui.screens.awards.AchievementsScreen
import ru.vsm.mobile.ui.screens.exam.ExamScreen
import ru.vsm.mobile.ui.screens.profile.ChallengesScreen
import ru.vsm.mobile.ui.screens.profile.LeaderboardScreen
import ru.vsm.mobile.ui.screens.profile.NotificationsScreen
import ru.vsm.mobile.ui.screens.profile.ProfileScreen
import ru.vsm.mobile.ui.screens.scenarios.DebriefScreen
import ru.vsm.mobile.ui.screens.scenarios.ScenarioListScreen
import ru.vsm.mobile.ui.screens.scenarios.ScenarioPlayScreen
import ru.vsm.mobile.ui.screens.settings.SettingsPreferences
import ru.vsm.mobile.ui.screens.settings.SettingsScreen
import ru.vsm.mobile.ui.screens.shift.ShiftScreen
import ru.vsm.mobile.ui.screens.today.TodayScreen
import androidx.compose.ui.platform.LocalContext

/** Реализация [AppNavigator] поверх androidx.navigation NavController. */
private class NavControllerNavigator(private val navController: NavHostController) : AppNavigator {
    override fun open(route: String) {
        navController.navigate(route)
    }

    override fun back() {
        navController.popBackStack()
    }

    override fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().first().uppercaseChar()
    val second = parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()
    return if (second != null) "$first$second" else first.toString()
}

/** Корневой навграф приложения: Scaffold со стеклянной шапкой и плавающим таб-баром поверх NavHost. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VsmNavHost() {
    val navController = rememberNavController()
    val navigator = remember(navController) { NavControllerNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isFullScreen = currentRoute == null || fullScreenRoutes.contains(currentRoute)

    val context = LocalContext.current
    // Первый запуск устройства (ни один экран ещё не открывался) — стартуем с логина, с точкой
    // «Продолжить без входа» на нём; далее (после первого показа) — сразу «Сегодня», анонимная игра
    // доступна всегда.
    val startDestination = remember {
        if (SettingsPreferences.hasSeenLoginScreen(context)) Routes.TODAY else Routes.LOGIN
    }
    LaunchedEffect(Unit) { SettingsPreferences.markLoginScreenSeen(context) }

    val container = appContainer()
    var unreadCount by remember { mutableIntStateOf(0) }
    var displayName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        val playerId = container.playerRepository.getOrCreatePlayerId()
        container.gamificationRepository.getNotifications(playerId, unreadOnly = true)
            .onSuccess { unreadCount = it.size }
    }
    LaunchedEffect(Unit) {
        container.authRepository.currentUser.collect { user ->
            displayName = user?.displayName ?: user?.login
        }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                // Компактная шапка (56dp + системная строка состояния), без запаса высоты
                // Material3 TopAppBar. Справа — только колокольчик и аватар/«Войти»: настройки
                // переехали внутрь профиля.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ВСМ · Тренажёр",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    NotificationBell(
                        unreadCount = unreadCount,
                        onClick = { navigator.open(Routes.NOTIFICATIONS) },
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    val name = displayName
                    if (name != null) {
                        Box(modifier = Modifier.clickable { navigator.open(Routes.PROFILE) }) {
                            VsmAvatar(initials = initialsOf(name), size = 32.dp, tone = VsmAvatarTone.Solid)
                        }
                    } else {
                        VsmButton(
                            text = "Войти",
                            onClick = { navigator.open(Routes.LOGIN) },
                            variant = VsmButtonVariant.Secondary,
                            size = VsmButtonSize.Small,
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isFullScreen) {
                VsmBottomBar(currentRoute = currentRoute, onSelect = navigator::openTab)
            }
        },
    ) { innerPadding ->
        // Переходы между экранами — fade+slide как `.rv`/`transitions` на сайте (`motion.css`):
        // вперёд — новый экран въезжает снизу-сбоку с лёгким смещением по X и наплывом, старый
        // уходит в противоположную сторону; назад — зеркально. Единая кривая `easeOut`/`easeInOut`.
        val forwardEnter = fadeIn(tween(VsmMotion.DUR, easing = VsmMotion.easeOut)) +
            slideInHorizontally(tween(VsmMotion.DUR, easing = VsmMotion.easeOut)) { it / 6 }
        val forwardExit = fadeOut(tween(VsmMotion.DUR_FAST, easing = VsmMotion.easeInOut)) +
            slideOutHorizontally(tween(VsmMotion.DUR_FAST, easing = VsmMotion.easeInOut)) { -it / 8 }
        val backEnter = fadeIn(tween(VsmMotion.DUR, easing = VsmMotion.easeOut)) +
            slideInHorizontally(tween(VsmMotion.DUR, easing = VsmMotion.easeOut)) { -it / 8 }
        val backExit = fadeOut(tween(VsmMotion.DUR_FAST, easing = VsmMotion.easeInOut)) +
            slideOutHorizontally(tween(VsmMotion.DUR_FAST, easing = VsmMotion.easeInOut)) { it / 6 }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { forwardEnter },
            exitTransition = { forwardExit },
            popEnterTransition = { backEnter },
            popExitTransition = { backExit },
        ) {
            composable(Routes.TODAY) { TodayScreen(navigator) }
            composable(Routes.SCENARIOS) { ScenarioListScreen(navigator) }
            composable(Routes.SHIFT) { ShiftScreen(navigator) }
            composable(Routes.AWARDS) { AchievementsScreen(navigator) }
            composable(Routes.PROFILE) { ProfileScreen(navigator) }
            composable(Routes.LEADERBOARD) { LeaderboardScreen(navigator) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(navigator) }
            composable(Routes.CHALLENGES) { ChallengesScreen(navigator) }
            composable(Routes.EXAM) { ExamScreen(navigator) }
            composable(Routes.SETTINGS) { SettingsScreen(navigator) }
            composable(Routes.LOGIN) { LoginScreen(navigator) }
            composable(Routes.REGISTER) { RegisterScreen(navigator) }
            composable(
                route = Routes.PLAY_TEMPLATE,
                arguments = listOf(navArgument("scenarioId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                val scenarioId = entry.arguments?.getString("scenarioId").orEmpty()
                ScenarioPlayScreen(scenarioId, navigator)
            }
            composable(
                route = Routes.DEBRIEF_TEMPLATE,
                arguments = listOf(navArgument("progressId") { type = androidx.navigation.NavType.StringType }),
            ) { entry ->
                val progressId = entry.arguments?.getString("progressId").orEmpty()
                DebriefScreen(progressId, navigator)
            }
        }
    }
}

/**
 * Нижний таб-бар на всю ширину, без обводки/видимых границ блока — честное «стекло» edge-to-edge,
 * как `.tabbar`/`.tabInner` в `Chrome.jsx` (сайт), но растянутое по краям вместо плавающей капсулы:
 * так лучше держит фирменный стиль на узких экранах и не выглядит «обрезанным блоком». Полупрозрачная
 * заливка `VsmPalette.glass` без бордера, скруглены только верхние углы, тень вместо обводки отделяет
 * от контента. Индикатор активного пункта — плавающая «пилюля» (аналог `.pill` на сайте), которая
 * скользит между пунктами по X с пружинным перелётом; все пункты имеют одинаковый вес/высоту, иконка
 * и подпись у каждого строго центрированы по горизонтали и вертикали.
 */
@Composable
private fun VsmBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val selectedIndex = bottomTabs.indexOfFirst { it.route == currentRoute }.let { if (it >= 0) it else 0 }
    var barWidthPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tabCount = bottomTabs.size

    // Общая высота содержимого таб-бара — фиксированная; отступ под системную навигацию добавляется
    // ПОВЕРХ неё (windowInsetsPadding последним), а не откусывает от неё, иначе на жестовой навигации
    // пункты станут ниже нужного.
    val barContentHeight = 64.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), clip = false)
            .background(
                VsmPalette.glass,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .onSizeChanged { barWidthPx = it.width }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val tabWidthDp = if (barWidthPx > 0) with(density) { (barWidthPx / tabCount).toDp() } else 0.dp
        val pillOffset by animateDpAsState(
            targetValue = tabWidthDp * selectedIndex,
            animationSpec = tween(VsmMotion.DUR, easing = VsmMotion.easeSpring),
            label = "tabPillOffset",
        )
        if (tabWidthDp > 0.dp) {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .offset(x = pillOffset)
                    .width(tabWidthDp)
                    .height(barContentHeight - 16.dp)
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().height(barContentHeight), verticalAlignment = Alignment.CenterVertically) {
            bottomTabs.forEach { tab ->
                val selected = tab.route == currentRoute
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(pressedScale = 0.9f, interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onSelect(tab.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
