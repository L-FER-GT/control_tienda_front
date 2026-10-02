package com.lfergt.controltienda.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lfergt.controltienda.domain.port.SyncMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

/*
 * Los listeners de Firestore son la "sala en tiempo real": se registran cuando una pantalla
 * de la tienda empieza a observar datos y se quitan al salir. Primero entregan la caché local
 * (funciona sin conexión) y luego los cambios del servidor.
 */

fun Query.observe(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val changes = if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) close(error.toDomainError())
        else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun DocumentReference.observe(includeMetadata: Boolean = false): Flow<DocumentSnapshot> = callbackFlow {
    val changes = if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE
    val registration = addSnapshotListener(changes) { snapshot, error ->
        if (error != null) close(error.toDomainError())
        else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * Escritura "offline-first": no se espera la confirmación del servidor (sin conexión nunca llegaría).
 * La caché local se actualiza al instante y, si el servidor rechaza el cambio, se avisa por [SyncMonitor].
 */
fun Task<Void>.commitOffline(sync: SyncMonitor) {
    addOnFailureListener { sync.report(it.toDomainError()) }
}

fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

fun Timestamp?.millis(): Long = this?.toDate()?.time ?: 0L
