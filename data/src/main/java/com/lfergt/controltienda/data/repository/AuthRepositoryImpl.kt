package com.lfergt.controltienda.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.lfergt.controltienda.data.firebase.firebaseCall
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.AuthSession
import com.lfergt.controltienda.domain.port.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication guarda el token en el dispositivo: el login solo se pide la primera vez.
 * El claim "superadmin" se cachea para que las opciones maestras funcionen sin conexión.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val currentUser: CurrentUser,
) : AuthRepository {

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override val session: Flow<AuthSession?> = callbackFlow {
        val listener = FirebaseAuth.IdTokenListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) trySend(null) else launch { trySend(toSession(user, forceRefresh = false)) }
        }
        auth.addIdTokenListener(listener)
        awaitClose { auth.removeIdTokenListener(listener) }
    }.distinctUntilChanged()

    override fun currentSession(): AuthSession? = auth.currentUser?.let {
        AuthSession(it.uid, it.email, it.displayName, prefs.getBoolean(superKey(it.uid), false))
    }

    private suspend fun toSession(user: FirebaseUser, forceRefresh: Boolean): AuthSession {
        val claim = withTimeoutOrNull(8_000) {
            runCatching { user.getIdToken(forceRefresh).await().claims["superadmin"] == true }.getOrNull()
        }
        val isSuper = claim ?: prefs.getBoolean(superKey(user.uid), false)
        prefs.edit().putBoolean(superKey(user.uid), isSuper).apply()
        return AuthSession(user.uid, user.email, user.displayName, isSuper)
    }

    private fun superKey(uid: String) = "superadmin_$uid"

    override suspend fun signInWithEmail(email: String, password: String) {
        firebaseCall { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    }

    override suspend fun registerWithEmail(displayName: String, email: String, password: String) {
        if (displayName.isBlank()) throw DomainError.Validation("name", "Ingresa tu nombre")
        firebaseCall {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build())?.await()
        }
    }

    override suspend fun signInWithGoogle(idToken: String) {
        firebaseCall { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
    }

    override suspend fun sendPasswordReset(email: String) {
        firebaseCall { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    override suspend fun refreshSession(): AuthSession? = auth.currentUser?.let { toSession(it, forceRefresh = true) }

    override suspend fun signOut() {
        val uid = auth.currentUser?.uid
        // Deja de recibir notificaciones push en este dispositivo.
        if (uid != null) {
            runCatching {
                withTimeoutOrNull(3_000) {
                    val token = messaging.token.await()
                    firestore.document("users/$uid/devices/$token").delete()
                }
            }
        }
        currentUser.clear()
        auth.signOut()
    }
}
