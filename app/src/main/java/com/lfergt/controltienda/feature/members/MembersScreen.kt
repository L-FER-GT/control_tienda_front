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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.lfergt.controltienda.ui.common.plural
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

@Composable
fun MembersScreen(onBack: () -> Unit, viewModel: MembersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val invite by viewModel.invite.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Membership?>(null) }
    val savingPermissions by viewModel.savingPermissions.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.permissionsSaved.collect { editing = null } }
    val role = if (tab == 0) StoreRole.EMPLOYEE else StoreRole.CLIENT
    CollectMessages(viewModel)

    BackScaffold(
        title = "Empleados y clientes", subtitle = state.header.name,
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
            onQuery = viewModel::onQuery,
            onSend = viewModel::sendInvite,
        )
    }
    editing?.let { member ->
        PermissionsSheet(
            member = member,
            onDismiss = { if (!savingPermissions) editing = null },
            busy = savingPermissions,
            onSave = { viewModel.setPermissions(member, it) },
        )
    }
}

@Composable
private fun MemberRow(member: Membership, isMe: Boolean, onActive: (Boolean) -> Unit, onPermissions: () -> Unit) {
    val editable = member.role != StoreRole.OWNER && !isMe
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Avatar(member.displayName, member.photoPath) },
            headlineContent = { Text(member.displayName + if (isMe) " (tú)" else "") },
            supportingContent = {
                Column {
                    val extra = if (member.role == StoreRole.EMPLOYEE && member.permissions.isNotEmpty()) {
                        " · " + plural(member.permissions.size, "permiso adicional", "permisos adicionales")
                    } else ""
                    Text(member.role.label + extra)
                    if (!member.active) Text("Sin acceso", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                if (editable) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Opciones de ${member.displayName}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (member.role == StoreRole.EMPLOYEE) {
                                DropdownMenuItem(text = { Text("Permisos") }, onClick = { menu = false; onPermissions() })
                            }
                            DropdownMenuItem(
                                text = { Text(if (member.active) "Quitar acceso" else "Dar acceso") },
                                onClick = { menu = false; onActive(!member.active) },
                            )
                        }
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
    onQuery: (String) -> Unit,
    onSend: (PublicProfile) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp),
        title = { Text("Invitar a la tienda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(StoreRole.EMPLOYEE, StoreRole.CLIENT).forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = state.role == r,
                            onClick = { onRole(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(r.label) }
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    label = { Text("Código o nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    trailingIcon = {
                        if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Limpiar") }
                    },
                    supportingText = { Text(if (state.byCode) "Código: ${state.query.length}/10 dígitos" else "Código de 10 dígitos o al menos 2 letras del nombre") },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.sending) Text("Enviando invitación…")
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
private fun PermissionsSheet(member: Membership, onDismiss: () -> Unit, busy: Boolean, onSave: (Set<Permission>) -> Unit) {
    var selected by remember(member.uid) { mutableStateOf(member.permissions) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Permisos de ${member.displayName}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Todo empleado puede ver productos, registrar ventas y ver las suyas. Activa los módulos de administrador que quieras darle. " +
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
                val checked = permission in selected
                ListItem(
                    modifier = Modifier.toggleable(value = checked, role = Role.Checkbox) {
                        selected = if (it) selected + permission else selected - permission
                    },
                    headlineContent = { Text(permission.label) },
                    supportingContent = { Text(permission.description) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                )
            }
            Button(enabled = !busy, onClick = { onSave(selected) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(if (busy) "Guardando…" else "Guardar permisos")
            }
        }
    }
}
