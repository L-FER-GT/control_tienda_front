package com.lfergt.controltienda.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Close
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.lfergt.controltienda.domain.model.Money
import java.io.File

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (singleLine) imeAction else ImeAction.Default),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { focus.clearFocus() }),
        trailingIcon = trailing,
        prefix = prefix?.let { { Text("$it ") } },
        enabled = enabled,
    )
}

/** Campo de dinero: acepta "12.5" o "12,50". */
@Composable
fun MoneyInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
) {
    TextInput(
        value = value,
        onValueChange = { text -> if (text.isEmpty() || text.matches(Regex("""\d{0,9}([.,]\d{0,2})?"""))) onValueChange(text) },
        label = label,
        modifier = modifier,
        error = error,
        keyboardType = KeyboardType.Decimal,
        supporting = supporting,
        prefix = Money.currencySymbol(currency),
    )
}

/** Campo numérico con decimales opcionales (cantidades, stock). */
@Composable
fun DecimalInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    error: String? = null,
    supporting: String? = null,
) {
    val pattern = if (allowNegative) Regex("""-?\d{0,9}([.,]\d{0,3})?""") else Regex("""\d{0,9}([.,]\d{0,3})?""")
    TextInput(
        value = value,
        onValueChange = { text -> if (text.isEmpty() || text.matches(pattern)) onValueChange(text) },
        label = label,
        modifier = modifier,
        error = error,
        keyboardType = KeyboardType.Decimal,
        supporting = supporting,
    )
}

fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun SearchInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, "Limpiar búsqueda") } },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Selector desplegable genérico. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Seleccionar",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Aceptar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------------------------------------------------------------- fotos

private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Permite elegir una foto de la galería o tomarla con la cámara. */
class PhotoPickerState(
    val openGallery: () -> Unit,
    val openCamera: () -> Unit,
)

@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): PhotoPickerState {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            currentOnPicked(it)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) currentOnPicked(Uri.parse(uri))
    }
    val openCamera = {
        try {
            val uri = newCameraUri(context)
            pendingCameraUri = uri.toString()
            camera.launch(uri)
        } catch (_: Exception) {
            pendingCameraUri = null
            Toast.makeText(context, "No se pudo abrir la cámara. Puedes elegir una foto de la galería.", Toast.LENGTH_LONG).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera()
        else Toast.makeText(context, "Habilita el permiso de cámara en Ajustes o elige una foto de la galería.", Toast.LENGTH_LONG).show()
    }
    return remember {
        PhotoPickerState(
            openGallery = {
                try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch (_: Exception) { Toast.makeText(context, "No hay una aplicación disponible para elegir fotos.", Toast.LENGTH_LONG).show() }
            },
            openCamera = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
                else permission.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onDismiss: () -> Unit, picker: PhotoPickerState) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Agregar foto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
        ListItem(
            headlineContent = { Text("Tomar foto") },
            leadingContent = { Icon(Icons.Outlined.PhotoCamera, null) },
            modifier = Modifier.clickable { onDismiss(); picker.openCamera() },
        )
        ListItem(
            headlineContent = { Text("Elegir de la galería") },
            leadingContent = { Icon(Icons.Outlined.PhotoLibrary, null) },
            modifier = Modifier.clickable { onDismiss(); picker.openGallery() },
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * Recuadro de foto para formularios: muestra la foto nueva elegida (local) o la guardada en el servidor.
 */
@Composable
fun PhotoField(
    currentPath: String?,
    pickedUri: String?,
    onPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Foto",
    aspectRatio: Float = 16f / 9f,
) {
    var showSheet by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker(onPicked)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(
            onClick = { showSheet = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(MaterialTheme.shapes.large).semantics { contentDescription = "Cambiar $label" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    pickedUri != null -> AsyncImage(pickedUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    currentPath != null -> StorageImage(currentPath, null, Modifier.fillMaxSize())
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.AddAPhoto, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Toca para agregar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showSheet) PhotoSourceSheet(onDismiss = { showSheet = false }, picker = picker)
}

// ---------------------------------------------------------------- agregar productos

/** Reemplaza al botón +: código de barras, QR y a mano, siempre visibles. */
@Composable
fun AddActions(onBarcode: () -> Unit, onQr: () -> Unit, onManual: () -> Unit, manualLabel: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FloatingActionButton(onClick = onBarcode) { Icon(Icons.Outlined.ViewWeek, contentDescription = "Escanear código de barras") }
        FloatingActionButton(onClick = onQr) { Icon(Icons.Outlined.QrCode2, contentDescription = "Escanear QR") }
        FloatingActionButton(onClick = onManual) { Icon(Icons.Outlined.PanTool, contentDescription = manualLabel) }
    }
}

/** Parece un buscador; al tocarlo abre la búsqueda de productos. */
@Composable
fun SearchLauncher(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
