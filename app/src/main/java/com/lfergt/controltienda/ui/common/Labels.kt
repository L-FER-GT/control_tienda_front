package com.lfergt.controltienda.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.domain.model.StoreRelation
import com.lfergt.controltienda.domain.model.StoreRole

/* Textos e íconos de la interfaz para los conceptos del dominio. */

val StoreOption.label: String
    get() = when (this) {
        StoreOption.VIEW_PRODUCTS -> "Ver productos"
        StoreOption.CREATE_ORDER -> "Crear orden"
        StoreOption.MY_SALES -> "Mis ventas"
        StoreOption.MANAGE_PRODUCTS -> "Gestionar productos"
        StoreOption.CATEGORIES -> "Categorías"
        StoreOption.REPORTS -> "Reportes"
        StoreOption.STOCK_ALERTS -> "Alertas de stock mínimo"
        StoreOption.SUPPLIERS -> "Proveedores"
        StoreOption.RECEPTIONS -> "Recepción de mercadería"
        StoreOption.MEMBERS -> "Empleados y clientes"
    }

val StoreOption.icon: ImageVector
    get() = when (this) {
        StoreOption.VIEW_PRODUCTS -> Icons.Outlined.Storefront
        StoreOption.CREATE_ORDER -> Icons.Outlined.AddShoppingCart
        StoreOption.MY_SALES -> Icons.Outlined.ReceiptLong
        StoreOption.MANAGE_PRODUCTS -> Icons.Outlined.Inventory2
        StoreOption.CATEGORIES -> Icons.Outlined.Category
        StoreOption.REPORTS -> Icons.Outlined.Assessment
        StoreOption.STOCK_ALERTS -> Icons.Outlined.WarningAmber
        StoreOption.SUPPLIERS -> Icons.Outlined.LocalShipping
        StoreOption.RECEPTIONS -> Icons.Outlined.MoveToInbox
        StoreOption.MEMBERS -> Icons.Outlined.Groups
    }

val Permission.label: String
    get() = when (this) {
        Permission.VIEW_STOCK -> "Ver inventario"
        Permission.MANAGE_PRODUCTS -> "Gestionar productos"
        Permission.MANAGE_CATEGORIES -> "Categorías"
        Permission.REPORTS -> "Reportes"
        Permission.STOCK_ALERTS -> "Alertas de stock mínimo"
        Permission.SUPPLIERS -> "Proveedores"
        Permission.RECEPTIONS -> "Recepción de mercadería"
        Permission.MEMBERS -> "Empleados y clientes"
        Permission.EDIT_STORE -> "Editar tienda"
    }

val Permission.description: String
    get() = when (this) {
        Permission.VIEW_STOCK -> "Ve el stock de cada producto"
        Permission.MANAGE_PRODUCTS -> "Crea y edita productos, precios, fotos y códigos"
        Permission.MANAGE_CATEGORIES -> "Crea categorías y asigna productos"
        Permission.REPORTS -> "Ve los reportes y las ventas de todos"
        Permission.STOCK_ALERTS -> "Ve los productos sin stock o bajo el mínimo"
        Permission.SUPPLIERS -> "Registra y edita proveedores"
        Permission.RECEPTIONS -> "Registra compras; suma stock y actualiza costos"
        Permission.MEMBERS -> "Invita, habilita y da permisos a otros"
        Permission.EDIT_STORE -> "Cambia nombre, foto, dirección y visibilidad"
    }

val StoreRole.label: String
    get() = when (this) {
        StoreRole.OWNER -> "Administrador"
        StoreRole.EMPLOYEE -> "Empleado"
        StoreRole.CLIENT -> "Cliente"
    }

val StoreRelation.label: String
    get() = when (this) {
        StoreRelation.OWNER -> "Administrador"
        StoreRelation.EMPLOYEE -> "Empleado"
        StoreRelation.CLIENT -> "Cliente"
        StoreRelation.PUBLIC -> "Pública"
    }

fun StoreListFilter.label(ownedCount: Int): String = when (this) {
    StoreListFilter.MY_STORES -> if (ownedCount == 1) "Mi tienda" else "Mis tiendas"
    StoreListFilter.WORKPLACES -> "Puestos de trabajo"
    StoreListFilter.ALL -> "Todas"
}
