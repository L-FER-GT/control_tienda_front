package com.lfergt.controltienda.feature.profile

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.BuildConfig
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.model.UserCode
import com.lfergt.controltienda.domain.model.UserProfile
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.feature.update.UpdateDialogs
import com.lfergt.controltienda.feature.update.UpdateState
import com.lfergt.controltienda.feature.update.UpdateViewModel
import com.lfergt.controltienda.ui.common.BaseViewModel
import com.lfergt.controltienda.ui.common.CollectMessages
import com.lfergt.controltienda.ui.components.Avatar
import com.lfergt.controltienda.ui.components.BackScaffold
import com.lfergt.controltienda.ui.components.FormColumn
import com.lfergt.controltienda.ui.components.LoadingBox
import com.lfergt.controltienda.ui.components.PhotoSourceSheet
import com.lfergt.controltienda.ui.components.TextInput
import com.lfergt.controltienda.ui.components.rememberPhotoPicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lfergt.controltienda.feature.update.LocalUpdater
import com.lfergt.controltienda.ui.components.rememberGuardedBack

data class ProfileForm(val name: String = "", val phone: String = "", val saving: Boolean = false, val deleting: Boolean = false) : java.io.Serializable

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val users: UserRepository,
    private val savedState: androidx.lifecycle.SavedStateHandle,
) : BaseViewModel() {

    val me = users.observeMe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _form = MutableStateFlow(savedState.get<ProfileForm>("draft")?.copy(saving = false, deleting = false) ?: ProfileForm())
    val form = _form.asStateFlow()
    private var baseline: ProfileForm? = savedState["baseline"]
    val dirty: Boolean get() = baseline?.let { _form.value.copy(saving = false, deleting = false) != it } ?: false

    init {
        viewModelScope.launch {
            val profile = users.observeMe().filterNotNull().first()
            if (savedState.get<ProfileForm>("draft") == null) _form.update { it.copy(name = profile.displayName, phone = profile.phone ?: "") }
            if (baseline == null) baseline = ProfileForm(name = profile.displayName, phone = profile.phone ?: "")
            savedState["baseline"] = baseline
            _form.collect { savedState["draft"] = it.copy(saving = false, deleting = false) }
        }
    }

    fun onName(v: String) = _form.update { it.copy(name = v) }
    fun onPhone(v: String) = _form.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }) }

    fun save() {
        if (_form.value.saving) return
        val submitted = _form.value
        _form.update { it.copy(saving = true) }
        launchSafe(onError = { _form.update { it.copy(saving = false) } }) {
            users.updateProfile(submitted.name, submitted.phone)
            baseline = submitted.copy(saving = false, deleting = false)
            savedState["baseline"] = baseline
            _form.update { it.copy(saving = false) }
            message("Datos actualizados")
        }
    }

    fun updatePhoto(uri: String) = launchSafe {
        users.updatePhoto(LocalFile(uri))
        message("Foto actualizada")
    }

    fun deleteAccount() {
        _form.update { it.copy(deleting = true) }
        launchSafe(onError = { _form.update { it.copy(deleting = false) } }) { users.deleteAccount() }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updater: UpdateViewModel = LocalUpdater.current ?: hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val updateState by updater.state.collectAsStateWithLifecycle()
    var photoSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker { viewModel.updatePhoto(it.toString()) }
    CollectMessages(viewModel)
    if (LocalUpdater.current == null) CollectMessages(updater)

    BackScaffold(title = "Configuración", onBack = rememberGuardedBack(viewModel.dirty, form.saving || form.deleting, onBack)) { padding ->
        val profile = me
        if (profile == null) {
            LoadingBox(Modifier.padding(padding))
            return@BackScaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            FormColumn {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box {
                        Avatar(profile.displayName, profile.photoPath, size = 112.dp)
                        SmallFloatingActionButton(
                            onClick = { photoSheet = true },
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        ) { Icon(Icons.Outlined.CameraAlt, contentDescription = "Cambiar foto") }
                    }
                }
                MyCodeCard(profile)
                Text("Mis datos", style = MaterialTheme.typography.titleMedium)
                TextInput(form.name, viewModel::onName, "Nombre *")
                TextInput(form.phone, viewModel::onPhone, "Teléfono", keyboardType = KeyboardType.Phone)
                TextInput(profile.email ?: "", {}, "Correo", enabled = false)
                Button(onClick = viewModel::save, enabled = !form.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(if (form.saving) "Guardando…" else "Guardar cambios")
                }
                if (updater.enabled) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Aplicación", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = updater::check,
                        enabled = updateState != UpdateState.Checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(when (val update = updateState) {
                        UpdateState.Checking -> "Buscando…"
                        is UpdateState.Downloading -> "Descargando ${update.percent} % · Ver"
                        is UpdateState.Ready -> "Actualización lista · Instalar"
                        is UpdateState.Available -> "Ver actualización disponible"
                        else -> "Buscar actualizaciones"
                    }) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !form.deleting && !profile.isSuperadmin,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Eliminar mi cuenta") }
                Text(
                    "Control Tienda ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    if (LocalUpdater.current == null) UpdateDialogs(updater)
    if (photoSheet) PhotoSourceSheet(onDismiss = { photoSheet = false }, picker = picker)
    if (confirmDelete) DeleteAccountDialog(onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; viewModel.deleteAccount() })
}

@Composable
private fun MyCodeCard(profile: UserProfile) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mi código", style = MaterialTheme.typography.labelLarge)
            Column {
                Text(
                    UserCode.pretty(profile.code),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Código", profile.code))) } }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar")
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Mi código en Control Tienda es ${profile.code}. Úsalo para invitarme a tu tienda.")
                    }
                    context.startActivity(Intent.createChooser(send, "Compartir código"))
                }) { Icon(Icons.Outlined.Share, contentDescription = "Compartir código") }
                }
            }
            Text(
                "Compártelo para que te inviten como empleado o cliente de una tienda.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Eliminar tu cuenta?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borran tus datos personales y tus tiendas quedan deshabilitadas. No se puede deshacer y requiere conexión.\n\nEscribe ELIMINAR para confirmar.")
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.trim().equals("ELIMINAR", ignoreCase = true)) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
