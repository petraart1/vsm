package ru.vsm.mobile.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import ru.vsm.mobile.R
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.NotificationBell
import ru.vsm.mobile.ui.components.VsmAvatar
import ru.vsm.mobile.ui.components.VsmAvatarTone
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.components.VsmButtonVariant
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
                TopAppBar(
                    title = {
                        Text(
                            text = "ВСМ · Тренажёр",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    },
                    actions = {
                        NotificationBell(
                            unreadCount = unreadCount,
                            onClick = { navigator.open(Routes.NOTIFICATIONS) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (currentRoute == Routes.SETTINGS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                )
                                .clickable { navigator.open(Routes.SETTINGS) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Настройки",
                                tint = if (currentRoute == Routes.SETTINGS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val name = displayName
                        if (name != null) {
                            Box(
                                modifier = Modifier
                                    .clickable { navigator.open(Routes.PROFILE) }
                                    .padding(end = 12.dp),
                            ) {
                                VsmAvatar(initials = initialsOf(name), size = 32.dp, tone = VsmAvatarTone.Solid)
                            }
                        } else {
                            VsmButton(
                                text = "Войти",
                                onClick = { navigator.open(Routes.LOGIN) },
                                variant = VsmButtonVariant.Secondary,
                                size = VsmButtonSize.Small,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isFullScreen) {
                VsmBottomBar(currentRoute = currentRoute, onSelect = navigator::openTab)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
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
 * Плавающая капсула снизу поверх контента — как `TabBar` из `Chrome.jsx`: активный пункт залит
 * `primaryContainer`, остальные — прозрачны. Полупрозрачная «стеклянная» заливка вместо blur.
 */
@Composable
private fun VsmBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(999.dp))
                .height(58.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomTabs.forEach { tab ->
                val selected = tab.route == currentRoute
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onSelect(tab.route) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
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
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
