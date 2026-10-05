package com.lfergt.controltienda.data.repository

import com.lfergt.controltienda.data.supabase.SupabaseAuth
import com.lfergt.controltienda.data.supabase.FieldValue
import com.lfergt.controltienda.data.supabase.DocumentStore
import com.lfergt.controltienda.data.supabase.Source
import com.lfergt.controltienda.data.supabase.RpcClient
import com.lfergt.controltienda.data.supabase.commitOffline
import com.lfergt.controltienda.data.supabase.supabaseCall
import com.lfergt.controltienda.data.supabase.observe
import com.lfergt.controltienda.data.supabase.toPublicProfile
import com.lfergt.controltienda.data.supabase.toUserProfile
import com.lfergt.controltienda.data.media.MediaUploader
import com.lfergt.controltienda.data.system.CurrentUser
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val auth: SupabaseAuth,
    private val authRepository: AuthRepository,
    private val database: DocumentStore,
    private val functions: RpcClient,
    private val uploader: MediaUploader,
    private val currentUser: CurrentUser,
    private val sync: SyncMonitor,
) : UserRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMe(): Flow<UserProfile?> = authRepository.session.flatMapLatest { session ->
        if (session == null) flowOf(null)
        else database.document("users/${session.uid}").observe()
            .map { if (it.exists()) it.toUserProfile() else null }
            .onEach { profile -> profile?.let(currentUser::remember) }
            .catch { emit(null) }
    }

    override suspend fun ensureProfile(displayName: String?): UserProfile {
        val uid = currentUser.uid()
        val cached = runCatching { database.document("users/$uid").get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) return cached.toUserProfile().also(currentUser::remember)

        val data = supabaseCall {
            functions.getCallable("bootstrapUser")
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
        database.batch()
            .update(database.document("users/$uid"), mapOf("displayName" to name, "phone" to phone?.trim()?.ifEmpty { null }, "updatedAt" to now))
            .update(database.document("publicProfiles/$uid"), mapOf("displayName" to name, "displayNameLower" to name.lowercase(), "updatedAt" to now))
            .commit()
            .commitOffline(sync)
        currentUser.clear()
    }

    override suspend fun updatePhoto(photo: LocalFile) {
        val uid = currentUser.uid()
        val path = uploader.enqueue(photo, "users/$uid")
        val now = FieldValue.serverTimestamp()
        database.batch()
            .update(database.document("users/$uid"), mapOf("photoPath" to path, "updatedAt" to now))
            .update(database.document("publicProfiles/$uid"), mapOf("photoPath" to path, "updatedAt" to now))
            .commit()
            .commitOffline(sync)
        currentUser.clear()
    }

    override suspend fun findByCode(code: String): PublicProfile? = supabaseCall {
        val clean = code.filter(Char::isDigit)
        if (!UserCode.isValid(clean)) throw DomainError.Validation("code", "El código debe tener 10 dígitos")
        val uid = database.document("userCodes/$clean").get().await().getString("uid") ?: return@supabaseCall null
        database.document("publicProfiles/$uid").get().await().takeIf { it.exists() }?.toPublicProfile()
    }

    override suspend fun searchByName(query: String, limit: Int): List<PublicProfile> = supabaseCall {
        val q = query.trim().lowercase()
        if (q.length < 2) return@supabaseCall emptyList()
        database.collection("publicProfiles")
            .orderBy("displayNameLower")
            .startAt(q)
            .endAt(q + "")
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .map { it.toPublicProfile() }
    }


    override suspend fun deleteAccount() {
        supabaseCall { functions.getCallable("deleteAccount").call().await() }
        currentUser.clear()
        auth.signOut()
    }
}
