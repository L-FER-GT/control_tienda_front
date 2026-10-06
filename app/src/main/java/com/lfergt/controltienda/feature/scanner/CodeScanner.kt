package com.lfergt.controltienda.feature.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicReference

/** Qué códigos lee el escáner. Funciona sin internet (modelo de ML Kit incluido en la app). */
enum class ScanMode(val formats: IntArray) {
    BARCODE(
        intArrayOf(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF,
            Barcode.FORMAT_CODE_93, Barcode.FORMAT_CODABAR,
        ),
    ),
    QR(intArrayOf(Barcode.FORMAT_QR_CODE)),
    ANY(BARCODE.formats + QR.formats),
}

data class ScannedCode(val value: String, val isQr: Boolean) : java.io.Serializable

/**
 * El cuadro guía el enfoque. También acepta un único código fuera del cuadro.
 * [belowFrame] se dibuja debajo del cuadro, dejando un margen.
 */
@Composable
fun CodeScanner(
    mode: ScanMode,
    onDetected: (ScannedCode) -> Unit,
    modifier: Modifier = Modifier,
    belowFrame: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    if (!granted) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.NoPhotography, null, tint = Color.White)
                Text("Se necesita permiso de cámara para escanear", color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Dar permiso") }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Abrir ajustes de permisos", color = Color.White) }
            }
        }
        return
    }
    var attempt by remember { mutableStateOf(0) }
    key(attempt) { CameraWithFrame(mode, onDetected, modifier, belowFrame, onRetry = { attempt++ }) }
}

@Composable
private fun CameraWithFrame(
    mode: ScanMode,
    onDetected: (ScannedCode) -> Unit,
    modifier: Modifier,
    belowFrame: @Composable () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val currentOnDetected by rememberUpdatedState(onDetected)
    val frameRect = remember { AtomicReference<Rect?>(null) }
    val controllerResult = remember { runCatching { LifecycleCameraController(context.applicationContext) } }
    val cameraController = controllerResult.getOrNull()
    val preview = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }

    DisposableEffect(mode, lifecycleOwner, cameraController) {
        // All callbacks run on the main executor; closing the dialog cannot shut it down
        // while ML Kit is still delivering a frame.
        var active = true
        var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
        fun failed(error: Throwable) {
            Log.e("CodeScanner", "No se pudo iniciar la cámara", error)
            ready = false
            cameraError = "No se pudo abrir la cámara. Cierra otras apps que la estén usando y vuelve a intentar."
        }
        if (cameraController == null) {
            failed(controllerResult.exceptionOrNull() ?: IllegalStateException("Cámara no disponible"))
        } else {
            val initialization = cameraController.initializationFuture
            initialization.addListener({
                if (!active) return@addListener
                try {
                    initialization.get()
                    cameraController.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    cameraController.cameraSelector = when {
                        cameraController.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> throw IllegalStateException("Este dispositivo no tiene cámara disponible")
                    }
                    val detector = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(mode.formats.first(), *mode.formats.drop(1).toIntArray()).build())
                    scanner = detector
                    cameraController.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    cameraController.setImageAnalysisAnalyzer(executor,
                        MlKitAnalyzer(listOf(detector), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                            if (!active) return@MlKitAnalyzer
                            val codes = result.getValue(detector).orEmpty().filter { !it.rawValue.isNullOrBlank() }
                            val frame = frameRect.get()
                            val code = codes.firstOrNull { barcode ->
                                val box = barcode.boundingBox
                                frame != null && box != null && frame.contains(box.centerX(), box.centerY())
                            } ?: codes.singleOrNull() ?: return@MlKitAnalyzer
                            currentOnDetected(ScannedCode(code.rawValue!!.trim(), code.format == Barcode.FORMAT_QR_CODE))
                        })
                    preview.controller = cameraController
                    cameraController.bindToLifecycle(lifecycleOwner)
                    ready = true
                } catch (error: Exception) {
                    failed(error)
                }
            }, executor)
        }
        onDispose {
            active = false
            runCatching { cameraController?.clearImageAnalysisAnalyzer() }
            runCatching { preview.controller = null }
            runCatching { cameraController?.unbind() }
            scanner?.close()
        }
    }

    cameraError?.let { error ->
        Box(modifier.fillMaxSize().background(Color.Black).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(error, color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Reintentar cámara") }
            }
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val isQr = mode == ScanMode.QR
        val frameWidth = if (isQr) minOf(maxWidth, maxHeight) * 0.62f else minOf(maxWidth * 0.84f, 520.dp)
        val frameHeight = if (isQr) frameWidth else frameWidth * 0.48f
        val frameTop = ((maxHeight - frameHeight) / 2 - 56.dp).coerceAtLeast(24.dp)
        val frameLeft = (maxWidth - frameWidth) / 2

        LaunchedEffect(frameWidth, frameHeight, frameTop, frameLeft, density) {
            with(density) {
                frameRect.set(
                    Rect(
                        frameLeft.roundToPx(),
                        frameTop.roundToPx(),
                        (frameLeft + frameWidth).roundToPx(),
                        (frameTop + frameHeight).roundToPx(),
                    ),
                )
            }
        }

        AndroidView(
            factory = { preview },
            modifier = Modifier.fillMaxSize(),
        )

        // Oscurece todo menos el cuadro donde se enfoca el código.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val topLeft = Offset(frameLeft.toPx(), frameTop.toPx())
            val size = Size(frameWidth.toPx(), frameHeight.toPx())
            val radius = CornerRadius(18.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.55f))
            drawRoundRect(Color.Transparent, topLeft, size, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White, topLeft, size, radius, style = Stroke(width = 3.dp.toPx()))
        }

        Text(
            if (isQr) "Enfoca el código QR dentro del cuadro" else "Enfoca el código de barras dentro del cuadro",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = (frameTop - 36.dp).coerceAtLeast(4.dp)),
        )

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = frameTop + frameHeight + 16.dp)
                .width(frameWidth),
        ) { belowFrame() }

        FilledTonalIconButton(
            enabled = ready && cameraController?.cameraInfo?.hasFlashUnit() == true,
            onClick = {
                val desired = !torch
                runCatching { cameraController?.enableTorch(desired) }.getOrNull()?.let { future ->
                    future.addListener({
                        runCatching { future.get() }.onSuccess { torch = desired }
                    }, executor)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
        ) {
            Icon(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, contentDescription = "Linterna")
        }
    }
}
