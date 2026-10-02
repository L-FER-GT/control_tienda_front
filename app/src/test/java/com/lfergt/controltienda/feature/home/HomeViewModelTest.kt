package com.lfergt.controltienda.feature.home

import app.cash.turbine.test
import com.lfergt.controltienda.domain.model.StoreListFilter
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRelation
import com.lfergt.controltienda.testing.FakeAuthRepository
import com.lfergt.controltienda.testing.FakeNotificationRepository
import com.lfergt.controltienda.testing.FakeStoreRepository
import com.lfergt.controltienda.testing.FakeUserRepository
import com.lfergt.controltienda.testing.MainDispatcherRule
import com.lfergt.controltienda.testing.testStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val stores = FakeStoreRepository()
    private val notifications = FakeNotificationRepository(unread = 3)
    private val vm by lazy { HomeViewModel(FakeAuthRepository(), stores, FakeUserRepository(), notifications) }

    private fun item(id: String, relation: StoreRelation, name: String = "Tienda $id") =
        StoreListItem(testStore(id, name), relation)

    @Test
    fun `con tienda propia el filtro por defecto es Mis tiendas`() = runTest {
        stores.list.value = listOf(item("1", StoreRelation.OWNER), item("2", StoreRelation.EMPLOYEE), item("3", StoreRelation.PUBLIC))
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(StoreListFilter.MY_STORES, state.selectedFilter)
            assertEquals(listOf("1"), state.items.map { it.store.id })
            assertEquals(3, state.unread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cambiar a Puestos de trabajo y buscar por nombre`() = runTest {
        stores.list.value = listOf(
            item("1", StoreRelation.OWNER),
            item("2", StoreRelation.EMPLOYEE, "Ferretería Lucho"),
            item("3", StoreRelation.EMPLOYEE, "Panadería"),
        )
        vm.state.test {
            vm.selectFilter(StoreListFilter.WORKPLACES)
            vm.onQuery("ferre")
            val state = expectMostRecentItem()
            assertEquals(listOf("2"), state.items.map { it.store.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `si ya no tiene tiendas, el filtro Mis tiendas desaparece`() = runTest {
        stores.list.value = listOf(item("1", StoreRelation.OWNER))
        vm.state.test {
            assertEquals(StoreListFilter.MY_STORES, expectMostRecentItem().selectedFilter)
            stores.list.value = listOf(item("9", StoreRelation.PUBLIC))
            val state = expectMostRecentItem()
            assertFalse(StoreListFilter.MY_STORES in state.filters)
            assertEquals(StoreListFilter.ALL, state.selectedFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
