package com.lfergt.controltienda.ui.common

import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.StoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Tienda + acceso del usuario actual. Lo usan todas las pantallas dentro de una tienda. */
data class StoreHeader(
    val store: Store? = null,
    val access: StoreAccess = StoreAccess.NONE,
    val loaded: Boolean = false,
) {
    val name: String get() = store?.name ?: ""
    val currency: String get() = store?.currency ?: "PEN"
}

class StoreContext @Inject constructor(
    private val stores: StoreRepository,
    private val auth: AuthRepository,
) {
    val uid: String? get() = auth.currentSession()?.uid

    fun header(storeId: String): Flow<StoreHeader> {
        val uid = uid ?: return flowOf(StoreHeader(loaded = true))
        return combine(stores.observeStore(storeId), stores.observeAccess(storeId, uid)) { store, access ->
            StoreHeader(store, if (store?.disabledBySystem == true) StoreAccess.NONE else access, loaded = true)
        }
    }
}
