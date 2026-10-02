package com.lfergt.controltienda.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lfergt.controltienda.data.di.AppScope
import com.lfergt.controltienda.data.firebase.commitOffline
import com.lfergt.controltienda.data.firebase.firebaseCall
import com.lfergt.controltienda.data.firebase.observe
import com.lfergt.controltienda.data.firebase.toCategory
import com.lfergt.controltienda.data.firebase.toPriceHistory
import com.lfergt.controltienda.data.firebase.toProduct
import com.lfergt.controltienda.data.media.MediaUploader
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.model.Category
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.PriceChangeSource
import com.lfergt.controltienda.domain.model.PriceHistoryEntry
import com.lfergt.controltienda.domain.model.Product
import com.lfergt.controltienda.domain.model.ProductDraft
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catálogo de la tienda. Los listeners de categorías y productos se comparten entre todas
 * las pantallas de la misma tienda (una sola "sala" por tienda) y se cierran 5 s después
 * de que el usuario sale de ella.
 */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val uploader: MediaUploader,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
    @param:AppScope private val scope: CoroutineScope,
) : CatalogRepository {

    private val productFlows = ConcurrentHashMap<String, Flow<List<Product>>>()
    private val categoryFlows = ConcurrentHashMap<String, Flow<List<Category>>>()

    override fun observeCategories(storeId: String): Flow<List<Category>> = categoryFlows.getOrPut(storeId) {
        firestore.collection("stores/$storeId/categories").observe()
            .map { snap -> snap.documents.map { it.toCategory() }.sortedBy { it.name.lowercase() } }
            .catch { emit(emptyList()) }
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    override fun observeProducts(storeId: String): Flow<List<Product>> = productFlows.getOrPut(storeId) {
        firestore.collection("stores/$storeId/products").observe()
            .map { snap -> snap.documents.map { it.toProduct() }.sortedBy { it.name.lowercase() } }
            .catch { emit(emptyList()) }
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    override fun observePriceHistory(storeId: String, productId: String): Flow<List<PriceHistoryEntry>> =
        firestore.collection("stores/$storeId/products/$productId/priceHistory")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(50)
            .observe()
            .map { snap -> snap.documents.map { it.toPriceHistory() } }
            .catch { emit(emptyList()) }

    override suspend fun saveCategory(storeId: String, categoryId: String?, name: String, photo: LocalFile?): String {
        val ref = categoryId?.let { firestore.document("stores/$storeId/categories/$it") }
            ?: firestore.collection("stores/$storeId/categories").document()
        val data = mutableMapOf<String, Any?>(
            "name" to name.trim(),
            "nameLower" to name.trim().lowercase(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        photo?.let { data["photoPath"] = uploader.enqueue(it, "stores/$storeId/categories") }
        if (categoryId == null) {
            data["createdAt"] = FieldValue.serverTimestamp()
            data.putIfAbsent("photoPath", null)
            ref.set(data).commitOffline(sync)
        } else {
            ref.update(data).commitOffline(sync)
        }
        return ref.id
    }

    override suspend fun deleteCategory(storeId: String, categoryId: String) {
        val uid = currentUser.uid()
        val products = firebaseCall {
            firestore.collection("stores/$storeId/products").whereEqualTo("categoryId", categoryId).get().await()
        }
        val batch = firestore.batch()
        products.documents.forEach {
            batch.update(it.reference, mapOf("categoryId" to null, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid))
        }
        batch.delete(firestore.document("stores/$storeId/categories/$categoryId"))
        batch.commit().commitOffline(sync)
    }

    override suspend fun assignCategory(storeId: String, productIds: List<String>, categoryId: String?) {
        val uid = currentUser.uid()
        // Un batch admite 500 operaciones.
        productIds.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { id ->
                batch.update(
                    firestore.document("stores/$storeId/products/$id"),
                    mapOf("categoryId" to categoryId, "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid),
                )
            }
            batch.commit().commitOffline(sync)
        }
    }

    override suspend fun saveProduct(storeId: String, draft: ProductDraft, photo: LocalFile?): String {
        val me = currentUser.profile()
        val isNew = draft.id == null
        val ref = draft.id?.let { firestore.document("stores/$storeId/products/$it") }
            ?: firestore.collection("stores/$storeId/products").document()
        val now = FieldValue.serverTimestamp()
        val data = mutableMapOf<String, Any?>(
            "name" to draft.name,
            "nameLower" to draft.name.lowercase(),
            "categoryId" to draft.categoryId,
            "salePriceCents" to draft.salePriceCents,
            "purchaseCostCents" to draft.purchaseCostCents,
            "unit" to draft.unit.key,
            "stock" to draft.stock,
            "stockAlert" to draft.stockAlert,
            "barcode" to draft.barcode,
            "qrCode" to draft.qrCode,
            "updatedAt" to now,
            "updatedBy" to me.uid,
        )
        photo?.let { data["photoPath"] = uploader.enqueue(it, "stores/$storeId/products") }

        val batch = firestore.batch()
        if (isNew) {
            data["createdAt"] = now
            data.putIfAbsent("photoPath", null)
            batch.set(ref, data)
        } else {
            batch.update(ref, data)
        }

        // Historial de precios: al crear y cada vez que cambia el precio de venta o el costo.
        val previous = draft.previous
        val priceChanged = previous == null ||
            previous.salePriceCents != draft.salePriceCents ||
            previous.purchaseCostCents != draft.purchaseCostCents
        if (priceChanged) {
            batch.set(
                ref.collection("priceHistory").document(),
                mapOf(
                    "salePriceCents" to draft.salePriceCents,
                    "purchaseCostCents" to draft.purchaseCostCents,
                    "source" to (if (isNew) PriceChangeSource.CREATED else PriceChangeSource.MANUAL).key,
                    "refId" to null,
                    "changedBy" to me.uid,
                    "changedByName" to me.displayName,
                    "at" to now,
                ),
            )
        }
        batch.commit().commitOffline(sync)
        return ref.id
    }

    override suspend fun deleteProduct(storeId: String, productId: String) {
        firestore.document("stores/$storeId/products/$productId").delete().commitOffline(sync)
    }
}
