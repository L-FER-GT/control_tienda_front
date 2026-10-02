package com.lfergt.controltienda.data.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Último token de FCM de este dispositivo (llega por FirebaseMessagingService.onRegistered).
 * Se guarda para poder borrarlo de users/{uid}/devices al cerrar sesión.
 */
@Singleton
class PushTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY, null)
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
        }

    private companion object {
        const val KEY = "fcm_token"
    }
}
