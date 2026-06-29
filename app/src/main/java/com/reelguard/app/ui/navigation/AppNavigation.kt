package com.reelguard.app.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reelguard.app.ui.dashboard.DashboardScreen
import com.reelguard.app.ui.onboarding.OnboardingScreen
import com.reelguard.app.ui.settings.SettingsScreen
import com.reelguard.app.ui.stats.StatsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val STATS = "stats"
}

@Composable
fun AppNavigation(showOnboarding: Boolean) {
    val navController = rememberNavController()
    val startDest = if (showOnboarding) Routes.ONBOARDING else Routes.DASHBOARD

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToStats = { navController.navigate(Routes.STATS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
