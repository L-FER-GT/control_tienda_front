package com.lfergt.controltienda.data.repository

import com.lfergt.controltienda.data.supabase.DocumentStore
import com.lfergt.controltienda.data.supabase.RpcClient
import com.lfergt.controltienda.data.supabase.supabaseCall
import com.lfergt.controltienda.data.supabase.toPublicProfile
import com.lfergt.controltienda.data.supabase.toStore
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.Store
import com.lfergt.controltienda.domain.model.UsageMetric
import com.lfergt.controltienda.domain.model.UsagePeriod
import com.lfergt.controltienda.domain.model.UsageReport
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.domain.port.AdminRepository
import com.lfergt.controltienda.domain.port.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Opciones maestras del superadmin. El servidor vuelve a verificar el claim en cada llamada. */
@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val database: DocumentStore,
    private val functions: RpcClient,
    private val users: UserRepository,
) : AdminRepository {

    override suspend fun getUsage(): UsageReport = supabaseCall {
        val data = functions.getCallable("adminGetUsage").call().await().data as? Map<*, *> ?: emptyMap<Any, Any>()
        val metrics = (data["metrics"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { m ->
            UsageMetric(
                key = m["key"] as? String ?: "",
                label = m["label"] as? String ?: "",
                used = (m["used"] as? Number)?.toDouble() ?: 0.0,
                limit = (m["limit"] as? Number)?.toDouble() ?: 0.0,
                unit = m["unit"] as? String ?: "",
                period = runCatching { UsagePeriod.valueOf(m["period"] as String) }.getOrDefault(UsagePeriod.DAY),
            )
        }
        UsageReport(
            metrics = metrics,
            generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            source = data["source"] as? String ?: "monitoring",
        )
    }

    override suspend fun searchUsers(query: String): List<PublicProfile> {
        val clean = query.trim()
        val digits = clean.filter(Char::isDigit)
        if (digits.length == clean.length && UserCode.isValid(digits)) return listOfNotNull(users.findByCode(digits))
        return users.searchByName(clean, limit = 30)
    }

    override suspend fun setUserDisabled(uid: String, disabled: Boolean) {
        supabaseCall {
            functions.getCallable("adminSetUserDisabled").call(mapOf("uid" to uid, "disabled" to disabled)).await()
        }
    }

    override suspend fun searchStores(query: String): List<Store> = supabaseCall {
        val q = query.trim().lowercase()
        database.collection("stores")
            .orderBy("nameLower")
            .startAt(q)
            .endAt(q + "")
            .limit(50)
            .get()
            .await()
            .documents
            .map { it.toStore() }
    }

    override suspend fun setStoreDisabled(storeId: String, disabled: Boolean) {
        supabaseCall {
            functions.getCallable("adminSetStoreDisabled").call(mapOf("storeId" to storeId, "disabled" to disabled)).await()
        }
    }
}
