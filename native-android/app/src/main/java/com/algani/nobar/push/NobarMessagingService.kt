package com.algani.nobar.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.algani.nobar.MainActivity
import com.algani.nobar.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NobarMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch { runCatching { FcmRegistrar.refreshToken(this@NobarMessagingService, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "Nobar"
        val body = message.notification?.body ?: message.data["body"] ?: "Ada aktivitas baru"
        showNotification(title, body, message.data["url"] ?: "/", message.data["room"], message.data["pw"])
    }

    private fun showNotification(title: String, body: String, url: String, room: String?, roomPassword: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Nobar", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("nobar_push_url", url)
            if (!room.isNullOrBlank()) putExtra("nobar_push_room", room)
            if (!roomPassword.isNullOrBlank()) putExtra("nobar_push_pw", roomPassword)
        }
        val pending = PendingIntent.getActivity(this, (room ?: url).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(this).notify((room ?: body).hashCode(), notification) }
    }

    companion object { private const val CHANNEL = "nobar" }
}
