package com.lfergt.controltienda.feature.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.BuildConfig
import com.lfergt.controltienda.domain.model.AppUpdate
import com.lfergt.controltienda.domain.model.LocalFile
import com.lfergt.controltienda.domain.port.AppUpdates
import com.lfergt.controltienda.ui.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val percent: Int) : UpdateState
    data class Ready(val update: AppUpdate, val file: LocalFile) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: AppUpdates,
) : BaseViewModel() {

    val enabled = updates.enabled
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()
    private var checkedOnStart = false

    /** Al abrir la app: si no hay conexión o falla la consulta no se molesta al usuario. */
    fun checkOnStart() {
        if (!enabled || checkedOnStart) return
        checkedOnStart = true
        viewModelScope.launch {
            try {
                updates.findNewer()?.let { _state.value = UpdateState.Available(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("AppUpdate", "No se pudo consultar la última versión", e)
            }
        }
    }

    /** Consulta pedida por el usuario desde Configuración. */
    fun check() {
        if (_state.value != UpdateState.Idle) return
        _state.value = UpdateState.Checking
        launchSafe(onError = { _state.value = UpdateState.Idle }) {
            val update = updates.findNewer()
            _state.value = update?.let(UpdateState::Available) ?: UpdateState.Idle
            if (update == null) message("Ya tienes la última versión (${BuildConfig.VERSION_NAME}).")
        }
    }

    fun download() {
        val update = (_state.value as? UpdateState.Available)?.update ?: return
        _state.value = UpdateState.Downloading(update, 0)
        launchSafe(onError = { _state.value = UpdateState.Available(update) }) {
            val file = updates.download(update) { _state.value = UpdateState.Downloading(update, (it * 100).toInt()) }
            _state.value = UpdateState.Ready(update, file)
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }
}

/** Diálogos del flujo: hay versión nueva → descargando → instalar. */
@Composable
fun UpdateDialogs(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    when (val current = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Nueva versión ${current.update.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tienes la ${BuildConfig.VERSION_NAME}. La actualización conserva tu sesión y tus datos.")
                    if (current.update.notes.isNotBlank()) {
                        Text(current.update.notes.take(1_500), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::download) { Text("Descargar${current.update.sizeLabel()}") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Más tarde") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargando ${current.update.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${current.percent} %")
                }
            },
            confirmButton = {},
        )
        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("Actualización lista") },
            text = {
                Text(
                    "Pulsa Instalar y confirma en Android. La primera vez Android pide permitir instalar apps " +
                        "desde Control Tienda: actívalo, vuelve y pulsa Instalar otra vez.",
                )
            },
            confirmButton = { TextButton(onClick = { installApk(context, current.file) }) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Más tarde") } },
        )
        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

private fun AppUpdate.sizeLabel(): String =
    if (sizeBytes > 0) " (%.1f MB)".format(sizeBytes / 1_048_576.0) else ""

/** Abre el instalador del sistema; si aún no hay permiso, lleva a la pantalla donde se concede. */
private fun installApk(context: Context, file: LocalFile) {
    val intent = if (context.packageManager.canRequestPackageInstalls()) {
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(file.uri.toUri(), file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
    }
    context.startActivity(intent)
}
