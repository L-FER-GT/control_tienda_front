package com.lfergt.controltienda.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lfergt.controltienda.feature.auth.LoginScreen
import com.lfergt.controltienda.feature.home.HomeScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: Any) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<LoginRoute> {
            LoginScreen(onLoggedIn = {
                navController.navigate(HomeRoute) { popUpTo(LoginRoute) { inclusive = true } }
            })
        }
        composable<HomeRoute> {
            HomeScreen()
        }
    }
}
