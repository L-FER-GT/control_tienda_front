package com.lfergt.controltienda.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.model.LocalFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** Límite por archivo acordado: 5 MB (también lo exigen las reglas de Storage). */
const val MAX_FILE_BYTES = 5L * 1024 * 1024

/**
 * Prepara un archivo antes de subirlo:
 *  - Imágenes: corrige la rotación, reduce a 1600 px como máximo y comprime a JPEG.
 *  - PDF / Excel: se copian tal cual si pesan 5 MB o menos.
 */
@Singleton
class ImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val maxSide = 1600

    suspend fun prepare(source: LocalFile, target: File): String = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val uri = Uri.parse(source.uri)
        if (source.mimeType.startsWith("image/")) {
            compressImage(uri, target)
            "image/jpeg"
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { input.copyTo(it) }
            } ?: throw DomainError.Validation(null, "No se pudo leer el archivo")
            if (target.length() > MAX_FILE_BYTES) {
                target.delete()
                throw DomainError.Validation(null, "El archivo supera el límite de 5 MB")
            }
            source.mimeType
        }
    }

    private fun compressImage(uri: Uri, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw DomainError.Validation(null, "La imagen no es válida")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw DomainError.Validation(null, "No se pudo leer la imagen")

        val rotated = rotateIfNeeded(uri, decoded)
        val scaled = scaleDown(rotated)

        var quality = 85
        do {
            FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            quality -= 15
        } while (target.length() > MAX_FILE_BYTES && quality > 20)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        if (target.length() > MAX_FILE_BYTES) {
            target.delete()
            throw DomainError.Validation(null, "La imagen supera el límite de 5 MB")
        }
    }

    private fun rotateIfNeeded(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}
