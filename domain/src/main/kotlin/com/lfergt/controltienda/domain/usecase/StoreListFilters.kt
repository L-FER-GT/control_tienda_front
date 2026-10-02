package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRelation

/**
 * Reglas de los filtros de la lista de tiendas:
 * - "Mi(s) tienda(s)" solo aparece si el usuario es dueño de al menos una, y en ese caso es el filtro por defecto.
 * - "Puestos de trabajo" solo aparece si es empleado (activo) en al menos una tienda.
 * - "Todas" siempre aparece: públicas + aquellas a las que le dieron acceso.
 */
object StoreListFilters {

    fun available(items: List<StoreListItem>): List<StoreListFilter> = buildList {
        if (items.any { it.relation == StoreRelation.OWNER }) add(StoreListFilter.MY_STORES)
        if (items.any { it.relation == StoreRelation.EMPLOYEE && it.membershipActive }) add(StoreListFilter.WORKPLACES)
        add(StoreListFilter.ALL)
    }

    fun default(items: List<StoreListItem>): StoreListFilter = available(items).first()

    /** Si el filtro elegido dejó de estar disponible (p. ej. se eliminó la única tienda), vuelve al por defecto. */
    fun resolve(selected: StoreListFilter?, items: List<StoreListItem>): StoreListFilter {
        val available = available(items)
        return if (selected != null && selected in available) selected else available.first()
    }

    fun apply(items: List<StoreListItem>, filter: StoreListFilter, query: String = ""): List<StoreListItem> {
        val q = query.trim().lowercase()
        return items
            .filter { item ->
                when (filter) {
                    StoreListFilter.MY_STORES -> item.relation == StoreRelation.OWNER
                    StoreListFilter.WORKPLACES -> item.relation == StoreRelation.EMPLOYEE && item.membershipActive
                    StoreListFilter.ALL -> item.membershipActive || item.relation == StoreRelation.PUBLIC
                }
            }
            .filter { q.isEmpty() || it.store.name.lowercase().contains(q) || it.store.address.lowercase().contains(q) }
            .sortedBy { it.store.name.lowercase() }
    }
}
