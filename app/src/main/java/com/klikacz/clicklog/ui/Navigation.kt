package com.klikacz.clicklog.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.klikacz.clicklog.diagnostics.DiagnosticScreen
import com.klikacz.clicklog.diagnostics.SetupWizardScreen

object Routes {
    const val MAIN = "main"
    const val EVENTS = "events"
    const val SETTINGS = "settings"
    const val WIZARD = "wizard"
    const val DIAGNOSTICS = "diagnostics"
}

@Composable
fun ClickLogNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = hiltViewModel(),
                onEvents = { navController.navigate(Routes.EVENTS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onWizard = { navController.navigate(Routes.WIZARD) },
            )
        }
        composable(Routes.EVENTS) {
            EventsScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onWizard = { navController.navigate(Routes.WIZARD) },
                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.WIZARD) {
            SetupWizardScreen(
                viewModel = hiltViewModel(),
                onFinished = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
    }
}
