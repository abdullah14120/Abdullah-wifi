package com.abdullah.wifibridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProxyForegroundService : Service() {

    private val CHANNEL_ID = "WiFiBridgeChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra("PORT", 8080) ?: 8080
        val ip = intent?.getStringExtra("IP") ?: "10.0.0.1"

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Bridge Active")
            .setContentText("Clean Session running on $ip:$port")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // يفضل لاحقاً تغييره لأيقونة تطبيقك
            .setPriority(NotificationCompat.PRIORITY_LOW) // لجعل الإشعار هادئاً في الإصدارات القديمة
            .build()

        // تشغيل الخدمة في المقدمة لحمايتها من الإغلاق
        startForeground(1, notification)
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        // التحقق من إصدار الأندرويد (أندرويد 8.0 فما فوق فقط يحتاج قنوات)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
