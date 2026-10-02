package com.lfergt.controltienda.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lfergt.controltienda.MainActivity
import com.lfergt.controltienda.R
import com.lfergt.controltienda.domain.port.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

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
@AndroidEntryPoint
class PushMessagingService : FirebaseMessagingService() {

    @Inject lateinit var users: UserRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** FCM 25.1+: el token llega aquí después de FirebaseMessaging.register(). */
    override fun onRegistered(token: String) {
        scope.launch { runCatching { users.registerDeviceToken(token) } }
    }

    @Deprecated("FCM 25.1 lo reemplaza por onRegistered; se mantiene por compatibilidad")
    override fun onNewToken(token: String) {
        onRegistered(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"] ?: ""
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, NotificationChannels.GENERAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
