package com.lfergt.controltienda.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val GENERAL = "general"

    fun create(context: Context) {
        val channel = NotificationChannel(GENERAL, "Notificaciones", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Invitaciones, respuestas y alertas del sistema"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** Recibe las notificaciones push (invitaciones, respuestas, alertas de consumo). */
