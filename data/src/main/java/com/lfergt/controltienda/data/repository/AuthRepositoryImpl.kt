package com.lfergt.controltienda.data.repository

import com.lfergt.controltienda.data.supabase.SupabaseAuth
import com.lfergt.controltienda.data.supabase.supabaseCall
import com.lfergt.controltienda.data.system.CurrentUser
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.port.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(private val auth: SupabaseAuth, private val currentUser: CurrentUser) : AuthRepository {
    override val session = auth.session
    override fun currentSession() = auth.currentUser
    override suspend fun signInWithEmail(email: String, password: String) = supabaseCall { auth.signIn(email,password) }
    override suspend fun registerWithEmail(displayName: String, email: String, password: String) = supabaseCall {
        if(displayName.isBlank()) throw DomainError.Validation("name","Ingresa tu nombre")
        auth.register(displayName,email,password)
    }
    override suspend fun signInWithGoogle(idToken: String) = supabaseCall { auth.google(idToken) }
    override suspend fun sendPasswordReset(email: String) = supabaseCall { auth.recover(email) }
    override suspend fun refreshSession() = supabaseCall { if(auth.currentUser!=null) auth.accessToken(force=true); auth.currentUser }
    override suspend fun signOut() { currentUser.clear(); auth.signOut() }
}
