package com.lfergt.controltienda.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lfergt.controltienda.data.firebase.commitOffline
import com.lfergt.controltienda.data.firebase.firebaseCall
import com.lfergt.controltienda.data.firebase.observe
import com.lfergt.controltienda.data.firebase.toInvitation
import com.lfergt.controltienda.data.firebase.toMembership
import com.lfergt.controltienda.data.firebase.toNotification
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.model.AppNotification
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.InvitationStatus
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.MemberRepository
import com.lfergt.controltienda.domain.port.NotificationRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
) : MemberRepository {

    override fun observeMembers(storeId: String): Flow<List<Membership>> =
        firestore.collection("stores/$storeId/members").observe()
            .map { snap ->
                snap.documents.map { it.toMembership() }
                    .sortedWith(compareBy<Membership> { it.role != StoreRole.OWNER }.thenBy { it.displayName.lowercase() })
            }
            .catch { emit(emptyList()) }

    override fun observePendingInvitations(storeId: String): Flow<List<Invitation>> =
        firestore.collection("stores/$storeId/invitations")
            .whereEqualTo("status", InvitationStatus.PENDING.key)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .observe()
            .map { snap -> snap.documents.map { it.toInvitation() } }
            .catch { emit(emptyList()) }

    /** Sin conexión también funciona: la función notifica al invitado cuando la invitación llega al servidor. */
    override suspend fun invite(store: Store, target: PublicProfile, role: StoreRole) {
        val me = currentUser.profile()
        firestore.collection("stores/${store.id}/invitations").document()
            .set(
                mapOf(
                    "storeId" to store.id,
                    "storeName" to store.name,
                    "storePhotoPath" to store.photoPath,
                    "fromUid" to me.uid,
                    "fromName" to me.displayName,
                    "toUid" to target.uid,
                    "toName" to target.displayName,
                    "toCode" to target.code,
                    "role" to role.key,
                    "status" to InvitationStatus.PENDING.key,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commitOffline(sync)
    }

    override suspend fun cancelInvitation(storeId: String, invitationId: String) {
        firestore.document("stores/$storeId/invitations/$invitationId")
            .update(mapOf("status" to InvitationStatus.CANCELLED.key, "respondedAt" to FieldValue.serverTimestamp()))
            .commitOffline(sync)
    }

    override suspend fun setActive(storeId: String, uid: String, active: Boolean) {
        firestore.document("stores/$storeId/members/$uid")
            .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
            .commitOffline(sync)
    }

    override suspend fun setPermissions(storeId: String, uid: String, permissions: Set<Permission>) {
        firestore.document("stores/$storeId/members/$uid")
            .update(mapOf("permissions" to permissions.map { it.key }.sorted(), "updatedAt" to FieldValue.serverTimestamp()))
            .commitOffline(sync)
    }
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: AuthRepository,
    private val sync: SyncMonitor,
) : NotificationRepository {

    private fun collectionFor(uid: String) = firestore.collection("users/$uid/notifications")

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNotifications(): Flow<List<AppNotification>> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList())
        else collectionFor(session.uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(100).observe()
            .map { snap -> snap.documents.map { it.toNotification() } }
            .catch { emit(emptyList()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadCount(): Flow<Int> = auth.session.flatMapLatest { session ->
        if (session == null) flowOf(0)
        else collectionFor(session.uid).whereEqualTo("read", false).observe()
            .map { it.size() }
            .catch { emit(0) }
    }

    override suspend fun markRead(notificationId: String) {
        val uid = auth.currentSession()?.uid ?: return
        collectionFor(uid).document(notificationId).update("read", true).commitOffline(sync)
    }

    override suspend fun markAllRead() {
        val uid = auth.currentSession()?.uid ?: return
        val unread = firebaseCall { collectionFor(uid).whereEqualTo("read", false).get().await() }
        if (unread.isEmpty) return
        val batch = firestore.batch()
        unread.documents.forEach { batch.update(it.reference, "read", true) }
        batch.commit().commitOffline(sync)
    }

    override suspend fun delete(notificationId: String) {
        val uid = auth.currentSession()?.uid ?: return
        collectionFor(uid).document(notificationId).delete().commitOffline(sync)
    }

    override suspend fun respondInvitation(storeId: String, invitationId: String, accept: Boolean) {
        firebaseCall {
            functions.getHttpsCallable("respondInvitation")
                .call(mapOf("storeId" to storeId, "invitationId" to invitationId, "accept" to accept))
                .await()
        }
    }
}
