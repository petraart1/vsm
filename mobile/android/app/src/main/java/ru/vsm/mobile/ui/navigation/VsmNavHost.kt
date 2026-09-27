package ru.vsm.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.screens.auth.LoginScreen
import ru.vsm.mobile.ui.screens.awards.AchievementsScreen
import ru.vsm.mobile.ui.screens.exam.ExamScreen
import ru.vsm.mobile.ui.screens.profile.ChallengesScreen
import ru.vsm.mobile.ui.screens.profile.LeaderboardScreen
import ru.vsm.mobile.ui.screens.profile.NotificationsScreen
import ru.vsm.mobile.ui.screens.profile.ProfileScreen
import ru.vsm.mobile.ui.screens.scenarios.DebriefScreen
import ru.vsm.mobile.ui.screens.scenarios.ScenarioListScreen
import ru.vsm.mobile.ui.screens.scenarios.ScenarioPlayScreen
import ru.vsm.mobile.ui.screens.settings.SettingsScreen
import ru.vsm.mobile.ui.screens.shift.ShiftScreen
import ru.vsm.mobile.ui.screens.today.TodayScreen

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

/** Корневой навграф приложения: Scaffold с нижним таб-баром и шапкой поверх NavHost. */
@Composable
fun VsmNavHost() {
    val navController = rememberNavController()
    val navigator = remember(navController) { NavControllerNavigator(navController) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isFullScreen = currentRoute == null || fullScreenRoutes.contains(currentRoute)

    val container = appContainer()
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(currentRoute) {
        val playerId = container.playerRepository.getOrCreatePlayerId()
        container.gamificationRepository.getNotifications(playerId, unreadOnly = true)
            .onSuccess { unreadCount = it.size }
    }

    Scaffold(
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = { Text("ВСМ") },
                    actions = {
                        IconButton(onClick = { navigator.open(Routes.NOTIFICATIONS) }) {
                            BadgedBox(
                                badge = { if (unreadCount > 0) Badge { Text(unreadCount.toString()) } },
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Уведомления")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isFullScreen) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navigator.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
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
