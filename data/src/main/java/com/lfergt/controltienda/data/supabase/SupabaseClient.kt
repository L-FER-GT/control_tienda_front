package com.lfergt.controltienda.data.supabase

import android.content.Context
import android.net.Uri
import com.lfergt.controltienda.data.di.DataConfig
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class ApiException(val status: Int, message: String) : Exception(message) {
    val retryable get() = status == 408 || status == 429 || status >= 500
}

internal fun jsonValue(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), jsonValue(v)) } }
    is Iterable<*> -> JSONArray().apply { value.forEach { put(jsonValue(it)) } }
    else -> value
}
internal fun unpack(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.keys().asSequence().associateWith { unpack(value.get(it)) }
    is JSONArray -> (0 until value.length()).map { unpack(value.get(it)) }
    else -> value
}

/** Supabase REST transport. No privileged key is accepted by the Android build. */
@Singleton
class SupabaseAuth @Inject constructor(
    @param:ApplicationContext context: Context,
    val config: DataConfig,
) {
    private val prefs = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val refreshMutex = Mutex()
    @Volatile private var generation = 0L
    val session = MutableStateFlow(readSession())
    val currentUser get() = session.value

    private fun readSession(): AuthSession? {
        val uid = prefs.getString("uid", null) ?: return null
        return AuthSession(uid, prefs.getString("email", null), prefs.getString("name", null), prefs.getBoolean("super", false))
    }

    private fun save(result: JSONObject) {
        val user = result.getJSONObject("user")
        val metadata = user.optJSONObject("user_metadata") ?: JSONObject()
        prefs.edit().putString("access", result.getString("access_token"))
            .putString("refresh", result.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + result.optLong("expires_in", 3600) * 1000)
            .putString("uid", user.getString("id")).putString("email", user.optString("email"))
            .putString("name", metadata.optString("displayName", metadata.optString("full_name", "Usuario")))
            .putBoolean("super", user.optJSONObject("app_metadata")?.optBoolean("superadmin") == true).commit()
        session.value = readSession()
    }

    private fun configured() {
        if (config.supabaseUrl.isBlank() || config.supabaseAnonKey.isBlank())
            throw DomainError.Validation(null, "Configura SUPABASE_URL y SUPABASE_ANON_KEY y vuelve a compilar la app.")
    }

    private suspend fun raw(path: String, method: String, body: RequestBody?, token: String?): String = withContext(Dispatchers.IO) {
        configured()
        val builder = Request.Builder().url(config.supabaseUrl.trimEnd('/') + path)
            .header("apikey", config.supabaseAnonKey).method(method, body)
        token?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).let { it.optString("msg", it.optString("message", it.optString("error_description", "Error de Supabase"))) } }.getOrDefault("Error de Supabase (${response.code})")
                throw ApiException(response.code, message)
            }
            text
        }
    }

    suspend fun accessToken(force: Boolean = false): String = refreshMutex.withLock {
        val expectedGeneration = generation
        val refresh = prefs.getString("refresh", null) ?: throw DomainError.Auth("Debes iniciar sesión.")
        if (force || prefs.getLong("expires", 0) < System.currentTimeMillis() + 60_000) {
            try {
                val text = raw("/auth/v1/token?grant_type=refresh_token", "POST", body(mapOf("refresh_token" to refresh)), null)
                if (generation != expectedGeneration) throw DomainError.Auth("La sesión cambió. Vuelve a intentar.")
                save(JSONObject(text))
            } catch (e: ApiException) {
                if (generation == expectedGeneration && e.status in listOf(400, 401, 403)) clear()
                throw e
            }
        }
        prefs.getString("access", null) ?: throw DomainError.Auth("Tu sesión expiró.")
    }

    suspend fun request(path: String, method: String = "POST", data: Any? = emptyMap<String, Any>(), requestBody: RequestBody? = null, expectedUid: String? = currentUser?.uid): String {
        if(expectedUid == null || currentUser?.uid != expectedUid) throw DomainError.Auth("La sesión cambió")
        val token = accessToken()
        if(currentUser?.uid != expectedUid) throw DomainError.Auth("La sesión cambió")
        val b = if (method == "GET") null else requestBody ?: body(data)
        return try { raw(path, method, b, token) } catch (e: ApiException) {
            if (e.status != 401) throw e
            val refreshed = accessToken(force = true)
            if(currentUser?.uid != expectedUid) throw DomainError.Auth("La sesión cambió")
            raw(path, method, b, refreshed)
        }
    }

    suspend fun signIn(email: String, password: String) { generation++; save(JSONObject(raw("/auth/v1/token?grant_type=password", "POST", body(mapOf("email" to email.trim(), "password" to password)), null))) }
    suspend fun register(name: String, email: String, password: String) {
        generation++
        val result = JSONObject(raw("/auth/v1/signup", "POST", body(mapOf("email" to email.trim(), "password" to password, "data" to mapOf("displayName" to name))), null))
        if (result.has("access_token") && !result.isNull("access_token")) save(result)
        else throw DomainError.Auth("Cuenta creada. Confirma tu correo antes de iniciar sesión.")
    }
    suspend fun google(idToken: String) { generation++; save(JSONObject(raw("/auth/v1/token?grant_type=id_token", "POST", body(mapOf("provider" to "google", "id_token" to idToken)), null))) }
    suspend fun recover(email: String) { raw("/auth/v1/recover?redirect_to=" + Uri.encode("controltienda://auth/callback"), "POST", body(mapOf("email" to email.trim())), null) }
    suspend fun recoverySession(uri: Uri) {
        generation++
        require(uri.scheme == "controltienda" && uri.host == "auth" && uri.path == "/callback")
        val values=Uri.parse("https://local/?" + (uri.fragment ?: ""))
        require(values.getQueryParameter("type") == "recovery") { "Enlace de recuperación no válido" }
        val access=values.getQueryParameter("access_token") ?: error("Falta el token")
        val refresh=values.getQueryParameter("refresh_token") ?: error("Falta la sesión")
        val user=JSONObject(raw("/auth/v1/user","GET",null,access))
        save(JSONObject().put("user",user).put("access_token",access).put("refresh_token",refresh).put("expires_in",values.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600))
    }
    suspend fun updatePassword(password: String) {
        if(password.length<8) throw DomainError.Validation("password","Usa al menos 8 caracteres")
        request("/auth/v1/user",method="PUT",data=mapOf("password" to password))
    }
    suspend fun signOut() {
        val token=prefs.getString("access",null)
        clear()
        if(token!=null) try { raw("/auth/v1/logout?scope=local","POST",body(emptyMap<String,Any>()),token) } catch (_: IOException) { } catch (_: ApiException) { }
    }
    fun clear() { generation++; prefs.edit().clear().commit(); session.value = null }
    private fun body(value: Any?): RequestBody = jsonValue(value).toString().toRequestBody("application/json".toMediaType())
}

class ReadTask<T>(private val block: suspend () -> T) { suspend fun await(): T = block() }

@Singleton
class RpcClient @Inject constructor(private val auth: SupabaseAuth) {
    fun getCallable(action: String) = Callable(action)
    inner class Callable(private val action: String) {
        fun call(payload: Any? = emptyMap<String, Any>()) = ReadTask {
            val result = auth.request("/rest/v1/rpc/ct_call", data = mapOf("action" to action, "payload" to payload))
            Result(unpack(JSONObject(result)))
        }
    }
    data class Result(val data: Any?)
}
