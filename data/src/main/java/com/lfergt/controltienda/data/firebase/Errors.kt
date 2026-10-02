package com.lfergt.controltienda.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException
import com.lfergt.controltienda.domain.error.DomainError
import kotlinx.coroutines.CancellationException

/** Traduce las excepciones de Firebase a errores del dominio con mensajes en español. */
fun Throwable.toDomainError(): Throwable = when (this) {
    is CancellationException, is DomainError -> this
    is FirebaseNetworkException -> DomainError.Network(cause = this)
    is FirebaseTooManyRequestsException ->
        DomainError.Auth("Demasiados intentos. Espera unos minutos e inténtalo de nuevo.", this)
    is FirebaseAuthWeakPasswordException -> DomainError.Validation("password", "La contraseña debe tener al menos 6 caracteres")
    is FirebaseAuthUserCollisionException -> DomainError.Auth("Ya existe una cuenta con ese correo.", this)
    is FirebaseAuthInvalidUserException -> when (errorCode) {
        "ERROR_USER_DISABLED" -> DomainError.Auth("Tu cuenta está deshabilitada. Contacta al administrador.", this)
        else -> DomainError.Auth("No existe una cuenta con ese correo.", this)
    }
    is FirebaseAuthInvalidCredentialsException -> DomainError.Auth("Correo o contraseña incorrectos.", this)
    is FirebaseAuthException -> DomainError.Auth(message ?: "No se pudo iniciar sesión.", this)
    is FirebaseFunctionsException -> when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> DomainError.Network(cause = this)
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> DomainError.PermissionDenied(message ?: "Sin permiso.")
        FirebaseFunctionsException.Code.NOT_FOUND -> DomainError.NotFound(message ?: "No encontrado.")
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> DomainError.Auth("Tu sesión expiró. Vuelve a iniciar sesión.", this)
        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        FirebaseFunctionsException.Code.ALREADY_EXISTS,
        -> DomainError.Validation(null, message ?: "Operación no válida.")
        else -> DomainError.Unknown(message ?: "Error del servidor.", this)
    }
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> DomainError.PermissionDenied()
        FirebaseFirestoreException.Code.UNAVAILABLE -> DomainError.Network(cause = this)
        FirebaseFirestoreException.Code.NOT_FOUND -> DomainError.NotFound("El registro ya no existe.")
        else -> DomainError.Unknown(cause = this)
    }
    is StorageException -> when (errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED -> DomainError.PermissionDenied("No tienes permiso para subir este archivo.")
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> DomainError.Network(cause = this)
        else -> DomainError.Unknown("No se pudo subir el archivo.", this)
    }
    else -> DomainError.Unknown(message ?: "Ocurrió un error inesperado.", this)
}

/** Ejecuta [block] traduciendo cualquier excepción de Firebase. */
suspend inline fun <T> firebaseCall(crossinline block: suspend () -> T): T =
    try {
        block()
    } catch (e: Throwable) {
        throw e.toDomainError()
    }
