package com.lfergt.controltienda.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.feature.auth.LoginScreen
import com.lfergt.controltienda.feature.catalog.CategoriesScreen
import com.lfergt.controltienda.feature.catalog.CategoryDetailScreen
import com.lfergt.controltienda.feature.catalog.ManageProductsScreen
import com.lfergt.controltienda.feature.catalog.ProductEditorScreen
import com.lfergt.controltienda.feature.catalog.ViewCategoriesScreen
import com.lfergt.controltienda.feature.catalog.ViewProductsScreen
import com.lfergt.controltienda.feature.home.HomeScreen
import com.lfergt.controltienda.feature.master.MasterScreen
import com.lfergt.controltienda.feature.members.MembersScreen
import com.lfergt.controltienda.feature.notifications.NotificationsScreen
import com.lfergt.controltienda.feature.orders.CreateOrderScreen
import com.lfergt.controltienda.feature.orders.MySalesScreen
import com.lfergt.controltienda.feature.orders.SaleDetailScreen
import com.lfergt.controltienda.feature.profile.SettingsScreen
import com.lfergt.controltienda.feature.purchasing.ReceptionEditorScreen
import com.lfergt.controltienda.feature.purchasing.ReceptionsScreen
import com.lfergt.controltienda.feature.purchasing.SupplierEditorScreen
import com.lfergt.controltienda.feature.purchasing.SuppliersScreen
import com.lfergt.controltienda.feature.reports.ReportDetailScreen
import com.lfergt.controltienda.feature.reports.ReportsScreen
import com.lfergt.controltienda.feature.stock.StockAlertsScreen
import com.lfergt.controltienda.feature.store.StoreEditorScreen
import com.lfergt.controltienda.feature.store.StoreLandingScreen

/** Ruta de cada opción del landing de la tienda. */
fun StoreOption.route(storeId: String): Any = when (this) {
    StoreOption.VIEW_PRODUCTS -> ViewCategoriesRoute(storeId)
    StoreOption.CREATE_ORDER -> CreateOrderRoute(storeId)
    StoreOption.MY_SALES -> MySalesRoute(storeId)
    StoreOption.MANAGE_PRODUCTS -> ManageProductsRoute(storeId)
    StoreOption.CATEGORIES -> CategoriesRoute(storeId)
    StoreOption.REPORTS -> ReportsRoute(storeId)
    StoreOption.STOCK_ALERTS -> StockAlertsRoute(storeId)
    StoreOption.SUPPLIERS -> SuppliersRoute(storeId)
    StoreOption.RECEPTIONS -> ReceptionsRoute(storeId)
    StoreOption.MEMBERS -> MembersRoute(storeId)
}

/*
 * Transición entre pantallas: la nueva entra desde la derecha por encima de la anterior, que se corre un poco
 * a la izquierda; al volver (botón, tecla o gesto de atrás predictivo), al revés. Ninguna pantalla se vuelve
 * transparente: el fundido cruzado por defecto de Navigation (700 ms) mezclaba las dos pantallas y dejaba ver
 * el fondo de la ventana, y eso se veía como un parpadeo.
 */
private val navSpec: FiniteAnimationSpec<IntOffset> = tween(300, easing = FastOutSlowInEasing)
private val slideInFromRight = slideInHorizontally(navSpec) { width -> width }
private val slideOutToRight = slideOutHorizontally(navSpec) { width -> width }
private val pushAside = slideOutHorizontally(navSpec) { width -> -width / 4 }
private val returnFromAside = slideInHorizontally(navSpec) { width -> -width / 4 }

@Composable
fun AppNavHost(navController: NavHostController, startDestination: Any) {
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = { slideInFromRight },
        exitTransition = { pushAside },
        popEnterTransition = { returnFromAside },
        popExitTransition = { slideOutToRight },
        // Por defecto el gesto usa otra animación (la anterior aparece con fundido y la actual se encoge).
        predictivePopEnterTransition = { returnFromAside },
        predictivePopExitTransition = { slideOutToRight },
    ) {
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
                onOption = { option -> navController.navigate(option.route(storeId)) },
            )
        }
        composable<MembersRoute> { MembersScreen(onBack = back) }

        // Catálogo
        composable<ViewCategoriesRoute> { entry ->
            val storeId = entry.toRoute<ViewCategoriesRoute>().storeId
            ViewCategoriesScreen(onBack = back, onCategory = { navController.navigate(ViewProductsRoute(storeId, it)) })
        }
        composable<ViewProductsRoute> { ViewProductsScreen(onBack = back) }
        composable<ManageProductsRoute> { entry ->
            val storeId = entry.toRoute<ManageProductsRoute>().storeId
            ManageProductsScreen(
                onBack = back,
                onEdit = { productId, code -> navController.navigate(ProductEditorRoute(storeId, productId, code)) },
            )
        }
        composable<ProductEditorRoute> { ProductEditorScreen(onBack = back) }
        composable<CategoriesRoute> { entry ->
            val storeId = entry.toRoute<CategoriesRoute>().storeId
            CategoriesScreen(onBack = back, onCategory = { navController.navigate(CategoryDetailRoute(storeId, it)) })
        }
        composable<CategoryDetailRoute> { CategoryDetailScreen(onBack = back) }

        // Ventas
        composable<CreateOrderRoute> { entry ->
            val storeId = entry.toRoute<CreateOrderRoute>().storeId
            CreateOrderScreen(
                onBack = back,
                onRegisterProduct = { code -> navController.navigate(ProductEditorRoute(storeId, null, code)) },
            )
        }
        composable<MySalesRoute> { entry ->
            val storeId = entry.toRoute<MySalesRoute>().storeId
            MySalesScreen(onBack = back, onDetail = { navController.navigate(SaleDetailRoute(storeId, it)) })
        }
        composable<SaleDetailRoute> { SaleDetailScreen(onBack = back) }

        // Inventario y compras
        composable<StockAlertsRoute> { entry ->
            val storeId = entry.toRoute<StockAlertsRoute>().storeId
            StockAlertsScreen(onBack = back, onProduct = { navController.navigate(ProductEditorRoute(storeId, it)) })
        }
        composable<SuppliersRoute> { entry ->
            val storeId = entry.toRoute<SuppliersRoute>().storeId
            SuppliersScreen(onBack = back, onEdit = { navController.navigate(SupplierEditorRoute(storeId, it)) })
        }
        composable<SupplierEditorRoute> { SupplierEditorScreen(onBack = back) }
        composable<ReceptionsRoute> { entry ->
            val storeId = entry.toRoute<ReceptionsRoute>().storeId
            ReceptionsScreen(onBack = back, onEdit = { navController.navigate(ReceptionEditorRoute(storeId, it)) })
        }
        composable<ReceptionEditorRoute> { ReceptionEditorScreen(onBack = back) }

        // Reportes
        composable<ReportsRoute> { entry ->
            val storeId = entry.toRoute<ReportsRoute>().storeId
            ReportsScreen(onBack = back, onReport = { navController.navigate(ReportDetailRoute(storeId, it.name)) })
        }
        composable<ReportDetailRoute> { ReportDetailScreen(onBack = back) }
    }
}
