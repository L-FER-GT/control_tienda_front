package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lfergt.controltienda.ui.image.StoragePath

/**
 * Imagen guardada en el servidor. Mientras carga, si falla o si no tiene foto,
 * muestra un ícono sobre un fondo suave.
 */
@Composable
fun StorageImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Outlined.Image,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (path.isNullOrBlank()) {
        ImagePlaceholder(placeholderIcon, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = StoragePath(path),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ImagePlaceholder(placeholderIcon, Modifier.fillMaxSize()) },
        error = { ImagePlaceholder(placeholderIcon, Modifier.fillMaxSize()) },
    )
}

@Composable
fun ImagePlaceholder(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
    }
}

/** Avatar circular: foto de perfil o iniciales. */
@Composable
fun Avatar(name: String, photoPath: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
        if (!photoPath.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = StoragePath(photoPath),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
