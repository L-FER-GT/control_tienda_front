package com.lfergt.controltienda.data.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.lfergt.controltienda.data.supabase.SupabaseAuth
import com.lfergt.controltienda.data.supabase.DocumentStore
import com.lfergt.controltienda.data.supabase.Source
import com.lfergt.controltienda.data.supabase.supabaseCall
import com.lfergt.controltienda.data.supabase.toUserProfile
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.ConnectivityMonitor
import com.lfergt.controltienda.domain.port.SyncMonitor
import com.lfergt.controltienda.domain.port.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncMonitorImpl @Inject constructor() : SyncMonitor {
    private val current = MutableStateFlow(SyncStatus())
    override val status: Flow<SyncStatus> = current
    override fun queueChanged(pending: Int) { current.update { it.copy(pending = pending) } }
    override fun syncing(active: Boolean) { current.update { it.copy(syncing = active) } }
    override fun completed(at: Long) { current.update { it.copy(lastSyncedAt = at) } }
    override fun reset() { current.value = SyncStatus() }
    override fun acknowledgeFailure() { current.update { it.copy(lastFailure = null) } }
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val failures: Flow<String> = events

    override fun report(error: Throwable) {
        Log.w("Sync", "Cambio rechazado por el servidor", error)
        current.update { it.copy(lastFailure = error.userMessage()) }
        events.tryEmit("No se pudo sincronizar un cambio: ${error.userMessage()}")
    }
}

@Singleton
class ConnectivityMonitorImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onUnavailable() { trySend(false) }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/** Datos del usuario en sesión que se copian en los documentos (p. ej. quién creó una venta). */
@Singleton
class CurrentUser @Inject constructor(
    private val auth: SupabaseAuth,
    private val database: DocumentStore,
) {
    @Volatile private var cached: UserProfile? = null

    fun uid(): String = auth.currentUser?.uid ?: throw DomainError.Auth("Debes iniciar sesión.")

    fun remember(profile: UserProfile) {
        cached = profile
    }

    /** Lee primero de la caché local (gratis y sin conexión) y luego del servidor. */
    suspend fun profile(): UserProfile {
        val uid = uid()
        cached?.takeIf { it.uid == uid }?.let { return it }
        val ref = database.document("users/$uid")
        val snapshot = runCatching { ref.get(Source.CACHE).await() }.getOrNull()?.takeIf { it.exists() }
            ?: supabaseCall { ref.get().await() }
        if (!snapshot.exists()) throw DomainError.NotFound("No se encontró tu perfil")
        return snapshot.toUserProfile().also { cached = it }
    }

    fun clear() {
        cached = null
    }
}
