package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import com.lfergt.controltienda.domain.port.SyncStatus
import com.lfergt.controltienda.ui.common.formatDateTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Snackbar compartido por toda la app (mensajes de pantallas, conexión y sincronización). */
val LocalSnackbar = compositionLocalOf { SnackbarHostState() }

/** true cuando no hay conexión: las barras superiores muestran un ícono de nube tachada. */
val LocalOffline = compositionLocalOf { false }
val LocalSyncStatus = compositionLocalOf { SyncStatus() }
val LocalAcknowledgeSync = compositionLocalOf<() -> Unit> { {} }

/**
 * Pantalla dentro de una tienda: arriba, anclado, solo el título (nombre de la tienda o del módulo)
 * con la flecha para volver.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    OfflineIcon()
                    actions()
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(LocalSnackbar.current, Modifier.imePadding()) },
        content = content,
    )
}

@Composable
fun OfflineIcon() {
    val offline = LocalOffline.current
    val status = LocalSyncStatus.current
    val acknowledge = LocalAcknowledgeSync.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = when {
        status.lastFailure != null -> "Revisar error de sincronización"
        offline -> "Sin conexión: ${status.pending} cambios pendientes"
        status.syncing -> "Sincronizando ${status.pending} cambios"
        status.pending > 0 -> "${status.pending} cambios pendientes"
        else -> "Sin cambios pendientes"
    }
    IconButton(onClick = { expanded = true }) {
        Icon(when { status.lastFailure != null -> Icons.Outlined.ErrorOutline; offline -> Icons.Outlined.CloudOff; status.pending > 0 -> Icons.Outlined.CloudUpload; else -> Icons.Outlined.CloudDone },
            label, tint = if (status.lastFailure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text("Conexión y cambios") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label)
            Text(if (offline) "Los cambios guardados en este dispositivo se enviarán cuando vuelva la conexión." else "Los cambios pendientes se envían automáticamente. Mantén la conexión para completar el envío.")
            status.lastSyncedAt?.let { Text("Último envío confirmado: ${formatDateTime(it)}") }
            status.lastFailure?.let { Text(it, color = MaterialTheme.colorScheme.error); Text("Un cambio rechazado puede haberse revertido. Revisa el registro antes de volver a guardarlo.") }
        } },
        confirmButton = { TextButton(onClick = { acknowledge(); expanded = false }) { Text("Entendido") } },
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun UnavailableState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        Icons.Outlined.CloudOff, "No pudimos abrir este registro",
        "Puede que ya no exista, no tengas acceso o aún no esté disponible en este dispositivo. Comprueba la conexión y vuelve a abrirlo.",
        modifier,
        action = { TextButton(onClick = onBack) { Text("Volver") } },
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun NoAccess(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Lock,
        title = "Sin acceso",
        message = "No tienes permiso para este módulo. Pide al administrador de la tienda que te lo habilite.",
        modifier = modifier,
    )
}

/** Formularios centrados con ancho máximo para que se lean bien en tablets y en horizontal. */
@Composable
fun FormColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
