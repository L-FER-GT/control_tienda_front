package com.lfergt.controltienda.domain.model

/** Sesión autenticada. [isSuperadmin] viene de un custom claim que solo asigna el backend. */
data class AuthSession(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isSuperadmin: Boolean,
)

/** Perfil privado del usuario (solo lo ve él mismo y el superadmin). */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val photoPath: String?,
    /** Código único de 10 dígitos para recibir invitaciones. */
    val code: String,
    val isSuperadmin: Boolean = false,
    val disabled: Boolean = false,
)

/** Datos mínimos y públicos de un usuario: lo que otros ven al buscarlo para invitarlo. */
data class PublicProfile(
    val uid: String,
    val displayName: String,
    val photoPath: String?,
    val code: String,
    val disabled: Boolean = false,
)

object UserCode {
    const val LENGTH = 10
    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it.isDigit() }

    /** "0123456789" -> "012 345 6789" para leerlo fácilmente. */
    fun pretty(code: String): String =
        if (code.length == LENGTH) "${code.substring(0, 3)} ${code.substring(3, 6)} ${code.substring(6)}" else code
}

/** Referencia a un archivo local elegido por el usuario (content:// o file://) pendiente de subir. */
data class LocalFile(val uri: String, val mimeType: String = "image/jpeg")
