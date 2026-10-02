package com.lfergt.controltienda.feature.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lfergt.controltienda.domain.model.Invitation
import com.lfergt.controltienda.domain.model.Membership
import com.lfergt.controltienda.domain.model.Permission
import com.lfergt.controltienda.domain.model.PublicProfile
import com.lfergt.controltienda.domain.model.StoreRole
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.common.description
import com.lfergt.controltienda.ui.common.label
import com.lfergt.controltienda.ui.components.Avatar
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.EmptyState
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.NoAccess

@Composable
fun MembersScreen(onBack: () -> Unit, viewModel: MembersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invite by viewModel.invite.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Membership?>(null) }
    val role = if (tab == 0) StoreRole.EMPLOYEE else StoreRole.CLIENT
    CollectMessages(viewModel)

    BackScaffold(
        title = state.header.name,
        onBack = onBack,
        floatingActionButton = {
            if (state.header.access.can(Permission.MEMBERS)) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openInvite(role) },
                    icon = { Icon(Icons.Outlined.PersonAdd, null) },
                    text = { Text(if (tab == 0) "Invitar empleado" else "Invitar cliente") },
                )
            }
        },
    ) { padding ->
        when {
            !state.header.loaded -> LoadingBox(Modifier.padding(padding))
            !state.header.access.can(Permission.MEMBERS) -> NoAccess(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Empleados") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Clientes") })
                }
                val list = state.members.filter { it.role == StoreRole.OWNER || it.role == role }
                val pending = state.pending.filter { it.role == role }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pending.isNotEmpty()) {
                        item { Text("Invitaciones pendientes", style = MaterialTheme.typography.titleSmall) }
                        items(pending, key = { "inv-" + it.id }) { PendingInvitationRow(it, onCancel = { viewModel.cancelInvitation(it) }) }
                        item { Text(if (tab == 0) "Empleados" else "Clientes", style = MaterialTheme.typography.titleSmall) }
                    }
                    items(list, key = { it.uid }) { member ->
                        MemberRow(
                            member = member,
                            isMe = member.uid == state.myUid,
                            onActive = { viewModel.setActive(member, it) },
                            onPermissions = { editing = member },
                        )
                    }
                    if (list.none { it.role == role } && pending.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.Outlined.Groups,
                                if (tab == 0) "Aún no tienes empleados" else "Aún no tienes clientes",
                                "Invítalos con su código de 10 dígitos o buscándolos por nombre.",
                                Modifier.heightIn(min = 280.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (invite.open) {
        InviteDialog(
            state = invite,
            onDismiss = viewModel::closeInvite,
            onRole = viewModel::setInviteRole,
            onMode = viewModel::setByCode,
            onQuery = viewModel::onQuery,
            onSend = viewModel::sendInvite,
        )
    }
    editing?.let { member ->
        PermissionsSheet(
            member = member,
            onDismiss = { editing = null },
            onSave = { viewModel.setPermissions(member, it); editing = null },
        )
    }
}

@Composable
private fun MemberRow(member: Membership, isMe: Boolean, onActive: (Boolean) -> Unit, onPermissions: () -> Unit) {
    val editable = member.role != StoreRole.OWNER && !isMe
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = { Avatar(member.displayName, member.photoPath) },
            headlineContent = { Text(member.displayName + if (isMe) " (tú)" else "") },
            supportingContent = {
                Column {
                    Text("${member.role.label} · ${UserCode.pretty(member.code)}")
                    if (member.role == StoreRole.EMPLOYEE) {
                        Text(
                            if (member.permissions.isEmpty()) "Acceso base: ver productos, crear orden y mis ventas"
                            else "Extra: " + member.permissions.joinToString { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (!member.active) Text("Deshabilitado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                if (editable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (member.role == StoreRole.EMPLOYEE) {
                            IconButton(onClick = onPermissions) { Icon(Icons.Outlined.Tune, contentDescription = "Permisos") }
                        }
                        Switch(checked = member.active, onCheckedChange = onActive)
                    }
                }
            },
        )
    }
}

@Composable
private fun PendingInvitationRow(invitation: Invitation, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        ListItem(
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = { Icon(Icons.Outlined.Schedule, null) },
            headlineContent = { Text(invitation.toName) },
            supportingContent = { Text("Esperando respuesta · ${UserCode.pretty(invitation.toCode)}") },
            trailingContent = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteDialog(
    state: InviteState,
    onDismiss: () -> Unit,
    onRole: (StoreRole) -> Unit,
    onMode: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onSend: (PublicProfile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("Invitar a la tienda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(StoreRole.EMPLOYEE, StoreRole.CLIENT).forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = state.role == r,
                            onClick = { onRole(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(r.label) }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = state.byCode, onClick = { onMode(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Por código") }
                    SegmentedButton(selected = !state.byCode, onClick = { onMode(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Por nombre") }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    label = { Text(if (state.byCode) "Código de 10 dígitos" else "Nombre de la persona") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (state.byCode) KeyboardType.Number else KeyboardType.Text),
                    trailingIcon = {
                        if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Limpiar") }
                    },
                    supportingText = { if (state.byCode) Text("${state.query.length}/10") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.heightIn(max = 280.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.results, key = { it.uid }) { profile ->
                            Card(onClick = { if (!state.sending) onSend(profile) }) {
                                ListItem(
                                    leadingContent = { Avatar(profile.displayName, profile.photoPath) },
                                    headlineContent = { Text(profile.displayName) },
                                    supportingContent = { Text(UserCode.pretty(profile.code)) },
                                    trailingContent = { Text("Invitar", color = MaterialTheme.colorScheme.primary) },
                                )
                            }
                        }
                        if (state.searched && state.results.isEmpty()) {
                            item { Text("No se encontró a nadie.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                Text(
                    "La persona recibirá una notificación y deberá aceptar la invitación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsSheet(member: Membership, onDismiss: () -> Unit, onSave: (Set<Permission>) -> Unit) {
    var selected by remember(member.uid) { mutableStateOf(member.permissions) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Permisos de ${member.displayName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Todo empleado puede ver productos, crear órdenes y ver sus ventas. Activa los módulos de administrador que quieras darle. " +
                    "Para tener otro administrador, dale todos los permisos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { selected = if (selected.size == Permission.entries.size) emptySet() else Permission.entries.toSet() }) {
                    Text(if (selected.size == Permission.entries.size) "Quitar todos" else "Dar todos")
                }
            }
            Permission.entries.forEach { permission ->
                ListItem(
                    headlineContent = { Text(permission.label) },
                    supportingContent = { Text(permission.description) },
                    trailingContent = {
                        Checkbox(
                            checked = permission in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + permission else selected - permission },
                        )
                    },
                )
            }
            Button(onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text("Guardar permisos")
            }
        }
    }
}
