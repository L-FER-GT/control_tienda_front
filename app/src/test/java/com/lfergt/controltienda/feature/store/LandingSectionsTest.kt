package com.lfergt.controltienda.feature.store

import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreOption
import com.lfergt.controltienda.domain.model.StoreRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandingSectionsTest {

    private fun sections(access: StoreAccess) = landingSections(access.availableOptions())

    @Test
    fun `el administrador ve dos grupos y un solo acceso de productos`() {
        val result = sections(StoreAccess(StoreRole.OWNER, emptySet(), active = true))
        assertEquals(listOf("Día a día", "Administración"), result.map { it.title })
        assertEquals(listOf(StoreOption.MY_SALES, StoreOption.MANAGE_PRODUCTS, StoreOption.STOCK_ALERTS), result[0].options)
        assertEquals(listOf(StoreOption.RECEPTIONS, StoreOption.SUPPLIERS, StoreOption.REPORTS, StoreOption.MEMBERS), result[1].options)
        val all = result.flatMap { it.options }
        assertTrue(StoreOption.CREATE_ORDER !in all && StoreOption.CATEGORIES !in all && StoreOption.VIEW_PRODUCTS !in all)
    }

    @Test
    fun `un empleado sin permisos ve solo el día a día con productos de consulta`() {
        val result = sections(StoreAccess(StoreRole.EMPLOYEE, emptySet(), active = true))
        assertEquals(1, result.size)
        assertEquals(listOf(StoreOption.MY_SALES, StoreOption.VIEW_PRODUCTS), result.single().options)
    }

    @Test
    fun `un empleado con permisos de compras suma el grupo de administración`() {
        val result = sections(StoreAccess(StoreRole.EMPLOYEE, setOf(Permission.RECEPTIONS, Permission.MANAGE_PRODUCTS), active = true))
        assertEquals(listOf(StoreOption.MY_SALES, StoreOption.MANAGE_PRODUCTS), result[0].options)
        assertEquals(listOf(StoreOption.RECEPTIONS), result[1].options)
    }

    @Test
    fun `un visitante solo ve productos`() {
        val result = sections(StoreAccess.VISITOR)
        assertEquals(listOf(StoreOption.VIEW_PRODUCTS), result.single().options)
    }
}
