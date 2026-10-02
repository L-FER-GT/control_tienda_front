package com.lfergt.controltienda.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.lfergt.controltienda.data.firebase.commitOffline
import com.lfergt.controltienda.data.firebase.firebaseCall
import com.lfergt.controltienda.data.firebase.observe
import com.lfergt.controltienda.data.firebase.toPublicProfile
import com.lfergt.controltienda.data.firebase.toUserProfile
import com.lfergt.controltienda.data.media.MediaUploader
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.data.system.PushTokenStore
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import com.lfergt.controltienda.domain.port.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val uploader: MediaUploader,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
    private val pushTokens: PushTokenStore,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMe(): Flow<UserProfile?> = authRepository.session.flatMapLatest { session ->
        if (session == null) flowOf(null)
        else firestore.document("users/${session.uid}").observe()
            .map { if (it.exists()) it.toUserProfile() else null }
            .onEach { profile -> profile?.let(currentUser::remember) }
            .catch { emit(null) }
    }

    override suspend fun ensureProfile(displayName: String?): UserProfile {
        val uid = currentUser.uid()
        val cached = runCatching { firestore.document("users/$uid").get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) return cached.toUserProfile().also(currentUser::remember)

        val data = firebaseCall {
            functions.getHttpsCallable("bootstrapUser")
                .call(mapOf("displayName" to (displayName ?: auth.currentUser?.displayName)))
                .await()
                .data as? Map<*, *>
        } ?: throw DomainError.Unknown("El servidor no devolvió el perfil")
        return UserProfile(
            uid = data["uid"] as? String ?: uid,
            displayName = data["displayName"] as? String ?: "Usuario",
            email = data["email"] as? String,
            phone = data["phone"] as? String,
            photoPath = data["photoPath"] as? String,
            code = data["code"] as? String ?: "",
            isSuperadmin = data["isSuperadmin"] == true,
            disabled = data["disabled"] == true,
        ).also(currentUser::remember)
    }

    override suspend fun updateProfile(displayName: String, phone: String?) {
        val name = displayName.trim()
        if (name.isEmpty()) throw DomainError.Validation("name", "El nombre es obligatorio")
        val uid = currentUser.uid()
        val now = FieldValue.serverTimestamp()
        firestore.batch()
            .update(firestore.document("users/$uid"), mapOf("displayName" to name, "phone" to phone?.trim()?.ifEmpty { null }, "updatedAt" to now))
            .update(firestore.document("publicProfiles/$uid"), mapOf("displayName" to name, "displayNameLower" to name.lowercase(), "updatedAt" to now))
            .commit()
            .commitOffline(sync)
        currentUser.clear()
    }

    override suspend fun updatePhoto(photo: LocalFile) {
        val uid = currentUser.uid()
        val path = uploader.enqueue(photo, "users/$uid")
        val now = FieldValue.serverTimestamp()
        firestore.batch()
            .update(firestore.document("users/$uid"), mapOf("photoPath" to path, "updatedAt" to now))
            .update(firestore.document("publicProfiles/$uid"), mapOf("photoPath" to path, "updatedAt" to now))
            .commit()
            .commitOffline(sync)
        currentUser.clear()
    }

    override suspend fun findByCode(code: String): PublicProfile? = firebaseCall {
        val clean = code.filter(Char::isDigit)
        if (!UserCode.isValid(clean)) throw DomainError.Validation("code", "El código debe tener 10 dígitos")
        val uid = firestore.document("userCodes/$clean").get().await().getString("uid") ?: return@firebaseCall null
        firestore.document("publicProfiles/$uid").get().await().takeIf { it.exists() }?.toPublicProfile()
    }

    override suspend fun searchByName(query: String, limit: Int): List<PublicProfile> = firebaseCall {
        val q = query.trim().lowercase()
        if (q.length < 2) return@firebaseCall emptyList()
        firestore.collection("publicProfiles")
            .orderBy("displayNameLower")
            .startAt(q)
            .endAt(q + "")
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .map { it.toPublicProfile() }
    }

    override suspend fun registerDeviceToken(token: String) {
        pushTokens.token = token
        val uid = auth.currentUser?.uid ?: return
        firestore.document("users/$uid/devices/$token")
            .set(mapOf("token" to token, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp()))
            .commitOffline(sync)
    }

    override suspend fun deleteAccount() {
        firebaseCall { functions.getHttpsCallable("deleteAccount").call().await() }
        currentUser.clear()
        auth.signOut()
    }
}
