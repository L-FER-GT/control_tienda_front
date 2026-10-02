package com.lfergt.controltienda.domain.error

/** Errores del dominio con mensajes listos para mostrar al usuario. */
sealed class DomainError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Validation(val field: String?, message: String) : DomainError(message)
    class NotFound(message: String) : DomainError(message)
    class PermissionDenied(message: String = "No tienes permiso para realizar esta acción.") : DomainError(message)
    class Network(message: String = "Se necesita conexión a internet para esta acción.", cause: Throwable? = null) :
        DomainError(message, cause)
    class Auth(message: String, cause: Throwable? = null) : DomainError(message, cause)
    class Conflict(message: String) : DomainError(message)
    class Unknown(message: String = "Ocurrió un error inesperado.", cause: Throwable? = null) : DomainError(message, cause)
}

/** Mensaje amigable para cualquier excepción. */
fun Throwable.userMessage(): String = when (this) {
    is DomainError -> message ?: "Ocurrió un error."
    else -> message?.takeIf { it.isNotBlank() } ?: "Ocurrió un error inesperado."
}
