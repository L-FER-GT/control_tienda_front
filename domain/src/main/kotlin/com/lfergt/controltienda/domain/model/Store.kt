package com.lfergt.controltienda.domain.model

data class Store(
    val id: String,
    val name: String,
    val address: String,
    val photoPath: String?,
    val isPublic: Boolean,
    val currency: String,
    val ownerId: String,
    val ownerName: String,
    val disabledBySystem: Boolean = false,
    val updatedAt: Long = 0,
)

data class StoreDraft(
    val name: String,
    val address: String,
    val isPublic: Boolean,
    val currency: String = Money.DEFAULT_CURRENCY,
)

/** Los roles son por tienda: un usuario puede ser dueño en una, empleado en otra y cliente en otra. */
enum class StoreRole(val key: String) {
    OWNER("owner"),
    EMPLOYEE("employee"),
    CLIENT("client");

    companion object {
        fun fromKey(key: String?): StoreRole? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Módulos de administrador que el dueño puede delegar a un empleado.
 * Ver productos, crear orden y mis ventas son la base de todo empleado y no requieren permiso.
 */
enum class Permission(val key: String) {
    VIEW_STOCK("view_stock"),
    MANAGE_PRODUCTS("manage_products"),
    MANAGE_CATEGORIES("manage_categories"),
    REPORTS("reports"),
    STOCK_ALERTS("stock_alerts"),
    SUPPLIERS("suppliers"),
    RECEPTIONS("receptions"),
    MEMBERS("members"),
    EDIT_STORE("edit_store");

    companion object {
        fun fromKey(key: String): Permission? = entries.firstOrNull { it.key == key }
        fun fromKeys(keys: Collection<String>): Set<Permission> = keys.mapNotNull(::fromKey).toSet()
    }
}

data class Membership(
    val storeId: String,
    val uid: String,
    val role: StoreRole,
    val permissions: Set<Permission>,
    val active: Boolean,
    val displayName: String,
    val photoPath: String?,
    val code: String,
    val joinedAt: Long,
)

/** Acceso efectivo del usuario actual a una tienda. */
data class StoreAccess(
    val role: StoreRole?,
    val permissions: Set<Permission>,
    val active: Boolean,
) {
    val isOwner: Boolean get() = active && role == StoreRole.OWNER
    val isStaff: Boolean get() = active && (role == StoreRole.OWNER || role == StoreRole.EMPLOYEE)

    fun can(permission: Permission): Boolean =
        active && (role == StoreRole.OWNER || (role == StoreRole.EMPLOYEE && permission in permissions))

    /** Opciones del landing de la tienda, en el orden en que se muestran. */
    fun availableOptions(): List<StoreOption> = StoreOption.entries.filter { option ->
        when (option) {
            StoreOption.VIEW_PRODUCTS -> active
            StoreOption.CREATE_ORDER, StoreOption.MY_SALES -> isStaff
            else -> option.permission?.let(::can) ?: false
        }
    }

    companion object {
        /** Visitante de una tienda pública, sin membresía. */
        val VISITOR = StoreAccess(role = null, permissions = emptySet(), active = true)
        val NONE = StoreAccess(role = null, permissions = emptySet(), active = false)
    }
}

enum class StoreOption(val permission: Permission?) {
    VIEW_PRODUCTS(null),
    CREATE_ORDER(null),
    MY_SALES(null),
    MANAGE_PRODUCTS(Permission.MANAGE_PRODUCTS),
    CATEGORIES(Permission.MANAGE_CATEGORIES),
    REPORTS(Permission.REPORTS),
    STOCK_ALERTS(Permission.STOCK_ALERTS),
    SUPPLIERS(Permission.SUPPLIERS),
    RECEPTIONS(Permission.RECEPTIONS),
    MEMBERS(Permission.MEMBERS),
}

/** Relación del usuario con una tienda de la lista principal. */
enum class StoreRelation { OWNER, EMPLOYEE, CLIENT, PUBLIC }

data class StoreListItem(
    val store: Store,
    val relation: StoreRelation,
    val membershipActive: Boolean = true,
)

enum class StoreListFilter { MY_STORES, WORKPLACES, ALL }
