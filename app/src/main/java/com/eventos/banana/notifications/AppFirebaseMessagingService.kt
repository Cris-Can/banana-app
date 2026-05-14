package com.eventos.banana.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eventos.banana.MainActivity
import com.eventos.banana.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: remoteMessage.data["body"]
        
        if (title != null || body != null) {
            // 🚫 Prevent sending notification to self (e.g. Creator of event)
            val senderId = remoteMessage.data["senderId"]
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            
            if (senderId != null && senderId == currentUid) {
                // Ignore notification
                return
            }

            sendNotification(title, body, remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        // 🔑 CRITICAL: Persist rotated FCM token to Firestore
        // Without this, push notifications stop working after token rotation
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    android.util.Log.d("FCM", "Token rotated and saved for $uid")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("FCM", "Failed to save rotated token: ${e.message}")
                }
        } else {
            android.util.Log.w("FCM", "Token rotated but no user logged in — will sync on next login")
        }
    }

    private fun sendNotification(title: String?, messageBody: String?, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        // Pass all data keys to intent
        data.forEach { (key, value) ->
            intent.putExtra(key, value)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val serverChannelId = data["channelId"] ?: "banana_channel_01"
        val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, serverChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            
        // No creamos el canal aquí, confiamos en NotificationHelper.createChannels() llamado en BananaApp
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
