package com.lfergt.controltienda.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lfergt.controltienda.data.firebase.commitOffline
import com.lfergt.controltienda.data.firebase.firebaseCall
import com.lfergt.controltienda.data.firebase.observe
import com.lfergt.controltienda.data.firebase.toMap
import com.lfergt.controltienda.data.firebase.toOrder
import com.lfergt.controltienda.data.firebase.toReception
import com.lfergt.controltienda.data.firebase.toSupplier
import com.lfergt.controltienda.data.firebase.toTimestamp
import com.lfergt.controltienda.data.media.MediaUploader
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.Order
import com.lfergt.controltienda.domain.model.OrderDraft
import com.lfergt.controltienda.domain.model.Reception
import com.lfergt.controltienda.domain.model.ReceptionDraft
import com.lfergt.controltienda.domain.model.Supplier
import com.lfergt.controltienda.domain.port.OrderRepository
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.domain.port.SupplierRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
) : OrderRepository {

    private fun orders(storeId: String) = firestore.collection("stores/$storeId/orders")

    /**
     * La venta se guarda al instante en la base local con número null.
     * Al sincronizar, la función onOrderCreated le asigna el correlativo y descuenta el stock.
     */
    override suspend fun createOrder(storeId: String, draft: OrderDraft): String {
        val me = currentUser.profile()
        val ref = orders(storeId).document()
        ref.set(
            mapOf(
                "number" to null,
                "status" to "pending",
                "stockApplied" to false,
                "createdBy" to me.uid,
                "createdByName" to me.displayName,
                "createdAt" to draft.createdAt.toTimestamp(),
                "syncedAt" to null,
                "paymentMethod" to draft.paymentMethod.key,
                "currency" to draft.currency,
                "items" to draft.items.map { it.toMap() },
                "totalCents" to draft.totalCents,
            ),
        ).commitOffline(sync)
        return ref.id
    }

    override fun observeMyOrders(storeId: String, uid: String): Flow<List<Order>> =
        orders(storeId)
            .whereEqualTo("createdBy", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(300)
            .observe(includeMetadata = true)
            .map { snap -> snap.documents.map { it.toOrder() } }
            .catch { emit(emptyList()) }

    override fun observeOrder(storeId: String, orderId: String): Flow<Order?> =
        orders(storeId).document(orderId).observe(includeMetadata = true)
            .map { if (it.exists()) it.toOrder() else null }
            .catch { emit(null) }

    override suspend fun getOrders(storeId: String, fromMillis: Long, toMillis: Long): List<Order> = firebaseCall {
        orders(storeId)
            .whereGreaterThanOrEqualTo("createdAt", fromMillis.toTimestamp())
            .whereLessThan("createdAt", toMillis.toTimestamp())
            .orderBy("createdAt")
            .get()
            .await()
            .documents
            .map { it.toOrder() }
    }
}

@Singleton
class SupplierRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sync: SyncMonitor,
) : SupplierRepository {

    private fun suppliers(storeId: String) = firestore.collection("stores/$storeId/suppliers")

    override fun observeSuppliers(storeId: String): Flow<List<Supplier>> =
        suppliers(storeId).observe()
            .map { snap -> listOf(Supplier.OTHERS) + snap.documents.map { it.toSupplier() }.sortedBy { it.companyName.lowercase() } }
            .catch { emit(listOf(Supplier.OTHERS)) }

    override suspend fun saveSupplier(storeId: String, supplier: Supplier): String {
        if (supplier.isOthers) throw DomainError.Validation(null, "El proveedor \"Otros\" no se puede modificar")
        val ref = if (supplier.id.isBlank()) suppliers(storeId).document() else suppliers(storeId).document(supplier.id)
        ref.set(
            mapOf(
                "companyName" to supplier.companyName.trim(),
                "ruc" to supplier.ruc?.trim()?.ifEmpty { null },
                "phone" to supplier.phone?.trim()?.ifEmpty { null },
                "contactName" to supplier.contactName?.trim()?.ifEmpty { null },
                "email" to supplier.email?.trim()?.ifEmpty { null },
                "address" to supplier.address?.trim()?.ifEmpty { null },
                "notes" to supplier.notes?.trim()?.ifEmpty { null },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).commitOffline(sync)
        return ref.id
    }

    override suspend fun deleteSupplier(storeId: String, supplierId: String) {
        if (supplierId == Supplier.OTHERS_ID) return
        suppliers(storeId).document(supplierId).delete().commitOffline(sync)
    }
}

@Singleton
class ReceptionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val uploader: MediaUploader,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
) : ReceptionRepository {

    private fun receptions(storeId: String) = firestore.collection("stores/$storeId/receptions")

    override fun observeReceptions(storeId: String): Flow<List<Reception>> =
        receptions(storeId).orderBy("receivedAt", Query.Direction.DESCENDING).limit(300)
            .observe(includeMetadata = true)
            .map { snap -> snap.documents.map { it.toReception() } }
            .catch { emit(emptyList()) }

    override fun observeReception(storeId: String, receptionId: String): Flow<Reception?> =
        receptions(storeId).document(receptionId).observe(includeMetadata = true)
            .map { if (it.exists()) it.toReception() else null }
            .catch { emit(null) }

    /** El stock y el costo de compra los ajusta la función onReceptionWritten (también al editar). */
    override suspend fun saveReception(storeId: String, draft: ReceptionDraft): String {
        val me = currentUser.profile()
        val newPaths = draft.newPhotos.map { uploader.enqueue(it, "stores/$storeId/receptions") }
        val ref = draft.id?.let { receptions(storeId).document(it) } ?: receptions(storeId).document()
        val now = FieldValue.serverTimestamp()
        val data = mutableMapOf<String, Any?>(
            "supplierId" to draft.supplierId,
            "supplierName" to draft.supplierName,
            "lines" to draft.lines.map { it.toMap() },
            "invoiceTotalCents" to draft.invoiceTotalCents,
            "invoicePhotos" to (draft.keptPhotos + newPaths).take(10),
            "notes" to draft.notes?.trim()?.ifEmpty { null },
            "receivedAt" to draft.receivedAt.toTimestamp(),
            "updatedAt" to now,
            "updatedBy" to me.uid,
            "updatedByName" to me.displayName,
        )
        if (draft.id == null) {
            data["createdBy"] = me.uid
            data["createdByName"] = me.displayName
            data["createdAt"] = now
            ref.set(data).commitOffline(sync)
        } else {
            ref.update(data).commitOffline(sync)
        }
        return ref.id
    }

    override suspend fun getReceptions(storeId: String, fromMillis: Long, toMillis: Long): List<Reception> = firebaseCall {
        receptions(storeId)
            .whereGreaterThanOrEqualTo("receivedAt", fromMillis.toTimestamp())
            .whereLessThan("receivedAt", toMillis.toTimestamp())
            .orderBy("receivedAt")
            .get()
            .await()
            .documents
            .map { it.toReception() }
    }
}
