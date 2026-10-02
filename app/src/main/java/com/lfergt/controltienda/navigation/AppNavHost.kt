package com.lfergt.controltienda.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.feature.auth.LoginScreen
import com.lfergt.controltienda.feature.home.HomeScreen
import com.lfergt.controltienda.feature.master.MasterScreen
import com.lfergt.controltienda.feature.members.MembersScreen
import com.lfergt.controltienda.feature.notifications.NotificationsScreen
import com.lfergt.controltienda.feature.profile.SettingsScreen
import com.lfergt.controltienda.feature.store.StoreEditorScreen
import com.lfergt.controltienda.feature.store.StoreLandingScreen

/** Ruta de cada opción del landing de la tienda (null = aún no disponible). */
fun StoreOption.route(storeId: String): Any? = when (this) {
    StoreOption.MEMBERS -> MembersRoute(storeId)
    else -> null
}

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
            HomeScreen(
                onOpenStore = { navController.navigate(StoreLandingRoute(it)) },
                onCreateStore = { navController.navigate(StoreEditorRoute()) },
                onNotifications = { navController.navigate(NotificationsRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onMaster = { navController.navigate(MasterRoute) },
            )
        }
        composable<StoreEditorRoute> {
            StoreEditorScreen(
                onBack = back,
                onSaved = { storeId, created ->
                    navController.popBackStack()
                    if (created) navController.navigate(StoreLandingRoute(storeId))
                },
            )
        }
        composable<NotificationsRoute> {
            NotificationsScreen(onBack = back, onOpenStore = { navController.navigate(StoreLandingRoute(it)) })
        }
        composable<SettingsRoute> { SettingsScreen(onBack = back) }
        composable<MasterRoute> { MasterScreen(onBack = back) }

        // ------------------------------------------------------------ dentro de una tienda
        composable<StoreLandingRoute> { entry ->
            val storeId = entry.toRoute<StoreLandingRoute>().storeId
            StoreLandingScreen(
                onBack = back,
                onEdit = { navController.navigate(StoreEditorRoute(storeId)) },
                onOption = { option -> option.route(storeId)?.let { navController.navigate(it) } },
            )
        }
        composable<MembersRoute> { MembersScreen(onBack = back) }
    }
}
