package com.lfergt.controltienda.feature.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Escáner a pantalla completa que se cierra con el primer código leído. */
@Composable
fun ScannerDialog(mode: ScanMode, onResult: (ScannedCode) -> Unit, onDismiss: () -> Unit) {
    val delivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            CodeScanner(mode = mode, onDetected = { code -> if (delivered.compareAndSet(false, true)) onResult(code) })
            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
            }
        }
    }
}
