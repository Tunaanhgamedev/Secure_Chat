package com.example.securechat.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.securechat.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SecureChatFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"] ?: "MESSAGE" 
        val title = data["title"] ?: remoteMessage.notification?.title ?: "SecureChat"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Bạn có thông báo mới"
        val senderId = data["senderId"]

        when (type) {
            "CALL" -> showCallNotification(title, body, senderId)
            else -> showMessageNotification(title, body, senderId)
        }
    }

    private fun showMessageNotification(title: String, body: String, senderId: String?) {
        val channelId = "messages_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (senderId != null) {
                putExtra("navigate_to", "chat/$senderId?peerName=$title")
                putExtra("target_id", senderId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notifyApp(channelId, "Tin nhắn mới", notification, 1001)
    }

    private fun showCallNotification(title: String, body: String, callerId: String?) {
        val channelId = "calls_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (callerId != null) {
                putExtra("navigate_to", "home") 
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(defaultSoundUri)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notifyApp(channelId, "Cuộc gọi đến", notification, 2002)
    }

    private fun notifyApp(channelId: String, channelName: String, notification: android.app.Notification, id: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (channelId == "calls_channel") NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                if (channelId == "calls_channel") {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(id, notification)
    }
}
