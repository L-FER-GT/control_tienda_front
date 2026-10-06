package com.lfergt.controltienda.feature.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Escáner a pantalla completa que se cierra con el primer código leído (con pitido y vibración). */
@Composable
fun ScannerDialog(mode: ScanMode, onResult: (ScannedCode) -> Unit, onDismiss: () -> Unit) {
    val delivered = remember(mode) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            if (!manual) CodeScanner(mode = mode, onDetected = { code ->
                if (delivered.compareAndSet(false, true)) {
                    ScanSounds.ok()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResult(code)
                }
            })
            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
            }
            Button(onClick = { manual = true }, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                Text("Ingresar código manualmente")
            }
        }
    }
    if (manual) AlertDialog(
        onDismissRequest = { manual = false },
        title = { Text(if (mode == ScanMode.QR) "Contenido del QR" else "Código de barras") },
        text = { OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Código") }, singleLine = true) },
        confirmButton = { TextButton(enabled = code.isNotBlank(), onClick = {
            if (delivered.compareAndSet(false, true)) onResult(ScannedCode(code.trim(), mode == ScanMode.QR))
        }) { Text("Usar código") } },
        dismissButton = { TextButton(onClick = { manual = false }) { Text("Volver a cámara") } },
    )
}
