package com.lfergt.controltienda.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.domain.model.AppNotification
import com.lfergt.controltienda.domain.model.InvitationStatus
import com.lfergt.controltienda.domain.model.NotificationType
import com.lfergt.controltienda.domain.port.NotificationRepository
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.formatDateTime
import com.lfergt.controltienda.ui.components.Avatar
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.theme.StatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class NotificationsState(
    val loading: Boolean = true,
    val items: List<AppNotification> = emptyList(),
    val responding: Set<String> = emptySet(),
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notifications: NotificationRepository,
) : BaseViewModel() {

    private val responding = MutableStateFlow<Set<String>>(emptySet())

    val state = combine(notifications.observeNotifications(), responding) { items, busy ->
        NotificationsState(false, items, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsState())

    fun markAllRead() = launchSafe { notifications.markAllRead() }

    fun open(n: AppNotification) {
        if (!n.read) launchSafe { notifications.markRead(n.id) }
    }

    fun delete(n: AppNotification) = launchSafe { notifications.delete(n.id) }

    /** Aceptar crea la membresía en el servidor (requiere conexión). */
    fun respond(n: AppNotification, accept: Boolean) {
        val storeId = n.storeId ?: return
        val invitationId = n.invitationId ?: return
        responding.update { it + n.id }
        launchSafe(onError = { responding.update { it - n.id } }) {
            notifications.respondInvitation(storeId, invitationId, accept)
            responding.update { it - n.id }
            message(if (accept) "¡Listo! Ya formas parte de la tienda." else "Invitación rechazada")
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenStore: (String) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel)

    BackScaffold(
        title = "Notificaciones",
        onBack = onBack,
        actions = {
            if (state.items.any { !it.read }) {
                IconButton(onClick = viewModel::markAllRead) { Icon(Icons.Outlined.DoneAll, contentDescription = "Marcar todo como leído") }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(
                Icons.Outlined.NotificationsNone,
                "Sin notificaciones",
                "Aquí verás las invitaciones a tiendas y otros avisos.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(state.items, key = { it.id }) { n ->
                    NotificationCard(
                        n = n,
                        busy = n.id in state.responding,
                        onOpen = { viewModel.open(n) },
                        onAccept = { viewModel.respond(n, true) },
                        onReject = { viewModel.respond(n, false) },
                        onDelete = { viewModel.delete(n) },
                        onOpenStore = { n.storeId?.let(onOpenStore) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    n: AppNotification,
    busy: Boolean,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onOpenStore: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (n.read) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (n.type == NotificationType.USAGE_ALERT) {
                    Surface(shape = MaterialTheme.shapes.small, color = StatusColors.warning.copy(alpha = 0.2f)) {
                        Icon(Icons.Outlined.QueryStats, null, Modifier.padding(8.dp).size(24.dp), tint = StatusColors.warning)
                    }
                } else {
                    Avatar(n.fromName ?: "Sistema", n.fromPhotoPath)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold)
                    n.fromName?.let { Text("De: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    Text(n.body, style = MaterialTheme.typography.bodyMedium)
                    Text(formatDateTime(n.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar") }
            }
            if (n.type == NotificationType.INVITATION) {
                when (n.invitationStatus ?: InvitationStatus.PENDING) {
                    InvitationStatus.PENDING -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        OutlinedButton(onClick = onReject, enabled = !busy) { Text("Rechazar") }
                        Button(onClick = onAccept, enabled = !busy) { Text("Aceptar") }
                    }
                    InvitationStatus.ACCEPTED -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(onClick = {}, label = { Text("Aceptada") })
                        Button(onClick = onOpenStore) { Text("Ir a la tienda") }
                    }
                    InvitationStatus.REJECTED -> AssistChip(onClick = {}, label = { Text("Rechazada") })
                    InvitationStatus.CANCELLED -> AssistChip(onClick = {}, label = { Text("Cancelada por el administrador") })
                }
            }
        }
    }
}
