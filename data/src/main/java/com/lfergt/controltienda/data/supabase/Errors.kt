package com.lfergt.controltienda.data.supabase

import com.lfergt.controltienda.domain.error.DomainError
import kotlinx.coroutines.CancellationException
import java.io.IOException

fun Throwable.toDomainError(): Throwable = when (this) {
    is CancellationException, is DomainError -> this
    is IOException -> DomainError.Network(cause = this)
    is ApiException -> when (status) {
        401 -> DomainError.Auth(message ?: "Sesión expirada", this)
        403 -> DomainError.PermissionDenied(message ?: "Sin permiso")
        404 -> DomainError.NotFound(message ?: "No encontrado")
        else -> if (retryable) DomainError.Network(cause = this) else DomainError.Validation(null, message ?: "Operación no válida")
    }
    else -> DomainError.Unknown(message ?: "Ocurrió un error inesperado", this)
}
suspend inline fun <T> supabaseCall(crossinline block: suspend () -> T): T =
    try { block() } catch (e: Exception) { throw e.toDomainError() }
