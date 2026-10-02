package com.lfergt.controltienda.navigation

import kotlinx.serialization.Serializable

/*
 * Rutas tipadas de la app. Dentro de una tienda, cada acción abre su propia ruta y muestra arriba
 * el nombre de la tienda con la flecha para volver; el segundo nivel muestra el nombre del módulo.
 */

@Serializable data object LoginRoute
@Serializable data object HomeRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object MasterRoute
@Serializable data class StoreEditorRoute(val storeId: String? = null)

@Serializable data class StoreLandingRoute(val storeId: String)
@Serializable data class ViewCategoriesRoute(val storeId: String)
@Serializable data class ViewProductsRoute(val storeId: String, val categoryId: String)
@Serializable data class CreateOrderRoute(val storeId: String)
@Serializable data class MySalesRoute(val storeId: String)
@Serializable data class SaleDetailRoute(val storeId: String, val orderId: String)
@Serializable data class ManageProductsRoute(val storeId: String)
@Serializable data class ProductEditorRoute(val storeId: String, val productId: String? = null, val code: String? = null)
@Serializable data class CategoriesRoute(val storeId: String)
@Serializable data class CategoryDetailRoute(val storeId: String, val categoryId: String)
@Serializable data class ReportsRoute(val storeId: String)
@Serializable data class ReportDetailRoute(val storeId: String, val type: String)
@Serializable data class StockAlertsRoute(val storeId: String)
@Serializable data class SuppliersRoute(val storeId: String)
@Serializable data class SupplierEditorRoute(val storeId: String, val supplierId: String? = null)
@Serializable data class ReceptionsRoute(val storeId: String)
@Serializable data class ReceptionEditorRoute(val storeId: String, val receptionId: String? = null)
@Serializable data class MembersRoute(val storeId: String)
