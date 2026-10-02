package com.lfergt.controltienda.domain.usecase

import com.lfergt.controltienda.domain.listItem
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.domain.model.StoreRelation
import com.lfergt.controltienda.domain.model.StoreRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRulesTest {

    @Test
    fun `sin tiendas propias ni empleos solo existe el filtro Todas`() {
        val items = listOf(listItem("1", StoreRelation.PUBLIC))
        assertEquals(listOf(StoreListFilter.ALL), StoreListFilters.available(items))
        assertEquals(StoreListFilter.ALL, StoreListFilters.default(items))
    }

    @Test
    fun `con una tienda propia el filtro por defecto es Mis tiendas`() {
        val items = listOf(listItem("1", StoreRelation.OWNER), listItem("2", StoreRelation.EMPLOYEE))
        assertEquals(
            listOf(StoreListFilter.MY_STORES, StoreListFilter.WORKPLACES, StoreListFilter.ALL),
            StoreListFilters.available(items),
        )
        assertEquals(StoreListFilter.MY_STORES, StoreListFilters.default(items))
    }

    @Test
    fun `si desaparece la tienda propia el filtro vuelve a Todas`() {
        val items = listOf(listItem("2", StoreRelation.PUBLIC))
        assertEquals(StoreListFilter.ALL, StoreListFilters.resolve(StoreListFilter.MY_STORES, items))
    }

    @Test
    fun `un empleado deshabilitado no ve el filtro Puestos de trabajo`() {
        val items = listOf(listItem("2", StoreRelation.EMPLOYEE, active = false))
        assertFalse(StoreListFilter.WORKPLACES in StoreListFilters.available(items))
        assertTrue(StoreListFilters.apply(items, StoreListFilter.ALL).isEmpty())
    }

    @Test
    fun `la búsqueda filtra por nombre o dirección`() {
        val items = listOf(listItem("1", StoreRelation.PUBLIC, name = "Bodega Rosita"), listItem("2", StoreRelation.PUBLIC, name = "Ferretería"))
        assertEquals(1, StoreListFilters.apply(items, StoreListFilter.ALL, "rosi").size)
    }

    @Test
    fun `cliente solo ve productos`() {
        val access = StoreAccess(StoreRole.CLIENT, emptySet(), active = true)
        assertEquals(listOf(StoreOption.VIEW_PRODUCTS), access.availableOptions())
        assertEquals(listOf(StoreOption.VIEW_PRODUCTS), StoreAccess.VISITOR.availableOptions())
    }

    @Test
    fun `empleado base ve productos crea orden y mis ventas, más lo que le delegan`() {
        val base = StoreAccess(StoreRole.EMPLOYEE, emptySet(), active = true)
        assertEquals(
            listOf(StoreOption.VIEW_PRODUCTS, StoreOption.CREATE_ORDER, StoreOption.MY_SALES),
            base.availableOptions(),
        )
        val withReports = base.copy(permissions = setOf(Permission.REPORTS))
        assertTrue(StoreOption.REPORTS in withReports.availableOptions())
        assertFalse(withReports.can(Permission.EDIT_STORE))
    }

    @Test
    fun `el dueño tiene todas las opciones y un miembro inactivo ninguna`() {
        val owner = StoreAccess(StoreRole.OWNER, emptySet(), active = true)
        assertEquals(StoreOption.entries.toList(), owner.availableOptions())
        assertTrue(Permission.entries.all(owner::can))
        val inactive = StoreAccess(StoreRole.EMPLOYEE, Permission.entries.toSet(), active = false)
        assertTrue(inactive.availableOptions().isEmpty())
    }
}
