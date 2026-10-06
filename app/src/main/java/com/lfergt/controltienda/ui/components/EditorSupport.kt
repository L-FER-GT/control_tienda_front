package com.lfergt.controltienda.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** La flecha y el gesto de atrás comparten la misma protección. */
@Composable
fun rememberGuardedBack(dirty: Boolean, busy: Boolean = false, onBack: () -> Unit): () -> Unit {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = { if (!busy) { if (dirty) confirming = true else onBack() } }
    BackHandler(enabled = dirty || busy, onBack = back)
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("¿Descartar los cambios?") },
        text = { Text("Tienes cambios sin guardar. Puedes seguir editando para conservarlos.") },
        confirmButton = { TextButton(onClick = { confirming = false; onBack() }, enabled = !busy) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Seguir editando") } },
    )
    return back
}
