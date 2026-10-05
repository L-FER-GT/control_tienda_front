package com.lfergt.controltienda.data.repository

import com.lfergt.controltienda.data.supabase.FieldValue
import com.lfergt.controltienda.data.supabase.DocumentStore
import com.lfergt.controltienda.data.supabase.commitOffline
import com.lfergt.controltienda.data.supabase.observe
import com.lfergt.controltienda.data.supabase.toMembership
import com.lfergt.controltienda.data.supabase.toStore
import com.lfergt.controltienda.data.media.MediaUploader
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreAccess
import com.lfergt.controltienda.domain.model.StoreDraft
import com.lfergt.controltienda.domain.model.StoreListItem
import com.lfergt.controltienda.domain.model.StoreRelation
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.port.StoreRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepositoryImpl @Inject constructor(
    private val database: DocumentStore,
    private val uploader: MediaUploader,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
) : StoreRepository {

    private val stores get() = database.collection("stores")

    private fun publicStores(): Flow<List<Store>> =
        stores.whereEqualTo("isPublic", true).limit(300).observe()
            .map { snap -> snap.documents.map { it.toStore() } }
            .catch { emit(emptyList()) }

    private fun myMemberships(uid: String): Flow<List<Membership>> =
        database.collectionGroup("members").whereEqualTo("uid", uid).observe()
            .map { snap -> snap.documents.map { it.toMembership() } }
            .catch { emit(emptyList()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStoreList(uid: String): Flow<List<StoreListItem>> {
        val memberStores: Flow<Pair<List<Membership>, List<Store>>> = myMemberships(uid).flatMapLatest { memberships ->
            val active = memberships.filter { it.active }
            if (active.isEmpty()) flowOf(memberships to emptyList())
            else combine(active.map { m -> observeStore(m.storeId) }) { docs ->
                memberships to docs.filterNotNull()
            }
        }
        return combine(publicStores(), memberStores) { publicList, (memberships, ownStores) ->
            val byId = LinkedHashMap<String, StoreListItem>()
            publicList.filterNot { it.disabledBySystem }.forEach { byId[it.id] = StoreListItem(it, StoreRelation.PUBLIC) }
            ownStores.forEach { store ->
                val membership = memberships.first { it.storeId == store.id }
                val relation = when (membership.role) {
                    StoreRole.OWNER -> StoreRelation.OWNER
                    StoreRole.EMPLOYEE -> StoreRelation.EMPLOYEE
                    StoreRole.CLIENT -> StoreRelation.CLIENT
                }
                byId[store.id] = StoreListItem(store, relation, membershipActive = true)
            }
            byId.values.toList()
        }
    }

    override fun observeStore(storeId: String): Flow<Store?> =
        database.document("stores/$storeId").observe()
            .map { if (it.exists()) it.toStore() else null }
            .catch { emit(null) }

    override fun observeAccess(storeId: String, uid: String): Flow<StoreAccess> {
        val member = database.document("stores/$storeId/members/$uid").observe()
            .map { if (it.exists()) it.toMembership() else null }
            .catch { emit(null) }
        return combine(member, observeStore(storeId)) { m, store ->
            when {
                m != null && m.active -> StoreAccess(m.role, m.permissions, active = true)
                store?.isPublic == true -> StoreAccess.VISITOR
                else -> StoreAccess.NONE
            }
        }.distinctUntilChanged()
    }

    override suspend fun createStore(draft: StoreDraft, photo: LocalFile?): String {
        val me = currentUser.profile()
        val ref = stores.document()
        val photoPath = photo?.let { uploader.enqueue(it, "stores/${ref.id}/store") }
        val now = FieldValue.serverTimestamp()
        database.batch()
            .set(
                ref,
                mapOf(
                    "name" to draft.name,
                    "nameLower" to draft.name.lowercase(),
                    "address" to draft.address,
                    "photoPath" to photoPath,
                    "isPublic" to draft.isPublic,
                    "currency" to draft.currency,
                    "ownerId" to me.uid,
                    "ownerName" to me.displayName,
                    "disabledBySystem" to false,
                    "createdAt" to now,
                    "updatedAt" to now,
                ),
            )
            .set(
                ref.collection("members").document(me.uid),
                mapOf(
                    "uid" to me.uid,
                    "role" to StoreRole.OWNER.key,
                    "permissions" to emptyList<String>(),
                    "active" to true,
                    "displayName" to me.displayName,
                    "photoPath" to me.photoPath,
                    "code" to me.code,
                    "joinedAt" to now,
                    "updatedAt" to now,
                ),
            )
            .commit()
            .commitOffline(sync)
        return ref.id
    }

    override suspend fun updateStore(storeId: String, draft: StoreDraft, photo: LocalFile?) {
        val data = mutableMapOf<String, Any?>(
            "name" to draft.name,
            "nameLower" to draft.name.lowercase(),
            "address" to draft.address,
            "isPublic" to draft.isPublic,
            "currency" to draft.currency,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        photo?.let { data["photoPath"] = uploader.enqueue(it, "stores/$storeId/store") }
        database.document("stores/$storeId").update(data).commitOffline(sync)
    }
}
