package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

@Composable
fun PhotoViewer(path: String, local: Boolean, onDismiss: () -> Unit) {
    var zoom by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { factor, pan, _ ->
        zoom = (zoom * factor).coerceIn(1f, 5f)
        offset = if (zoom == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White) }
                TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Text("Restablecer", color = Color.White) }
                TextButton(onClick = { zoom = (zoom + 1f).coerceAtMost(5f) }) { Text("Ampliar", color = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().transformable(transform), contentAlignment = Alignment.Center) {
                val imageModifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
                }
                if (local) AsyncImage(path, "Factura adjunta", modifier = imageModifier, contentScale = ContentScale.Fit)
                else StorageImage(path, "Factura adjunta", imageModifier, contentScale = ContentScale.Fit)
            }
            Text("Pellizca para ampliar y arrastra para leer", color = Color.White, modifier = Modifier.padding(16.dp))
        }
    }
}
