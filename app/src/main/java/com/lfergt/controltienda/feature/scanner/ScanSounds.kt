package com.lfergt.controltienda.feature.scanner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sonidos del escáner, generados en la app (no hay archivos de audio): un pitido agudo y corto como el de
 * un lector de caja en cada lectura exitosa, en cualquier módulo, y en Crear orden un tono grave cuando
 * el código no está registrado. Suenan con el volumen multimedia; el de notificaciones suele estar bajo
 * o en silencio y no se oían.
 *
 * Es único para toda la app y no se libera: el escáner de un solo código se cierra apenas lee, y el
 * pitido tiene que seguir sonando después de cerrarse.
 */
object ScanSounds {
    private const val SAMPLE_RATE = 44_100

    private val ok by lazy { tone(frequencyHz = 2_700, durationMs = 120) }
    private val unknown by lazy { tone(frequencyHz = 440, durationMs = 260) }

    fun ok() = play(ok)

    fun unknown() = play(unknown)

    private fun play(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            // Un AudioTrack estático se vuelve a reproducir desde el inicio deteniéndolo y recargando el audio.
            track.stop()
            track.reloadStaticData()
            track.play()
        }.onFailure { Log.w("ScanSounds", "No se pudo reproducir el sonido", it) }
    }

    private fun tone(frequencyHz: Int, durationMs: Int): AudioTrack? = runCatching {
        val samples = SAMPLE_RATE * durationMs / 1_000
        val fade = SAMPLE_RATE * 5 / 1_000 // 5 ms de subida y bajada para que no chasquee
        val pcm = ShortArray(samples) { i ->
            val envelope = minOf(1.0, i / fade.toDouble(), (samples - 1 - i) / fade.toDouble())
            (sin(2 * PI * frequencyHz * i / SAMPLE_RATE) * envelope * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }.onFailure { Log.w("ScanSounds", "No se pudo preparar el sonido", it) }.getOrNull()
}
