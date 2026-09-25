package com.abdullah.wifibridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.abdullah.wifibridge.R
import com.abdullah.wifibridge.engine.NetworkInterfaceScanner
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine

class BridgeForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "WifiBridgeRootChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("جسر الروت الشفاف يعمل بكفاءة عالية وبدون قيود...")
        startForeground(NOTIFICATION_ID, notification)

        // تشغيل المحرك تلقائياً باستخدام الكاشف الذكي
        Thread {
            val wan = NetworkInterfaceScanner.findActiveWanInterface() ?: "rmnet_data0"
            val lan = NetworkInterfaceScanner.findHotspotInterface()
            RootNetworkMasterEngine.startUnlimitedBridge(wan, lan, "192.168.50.1")
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        // تنظيف القواعد وإيقاف التوجيه عند إيقاف الخدمة نهائياً
        Thread {
            RootNetworkMasterEngine.stopUnlimitedBridge()
        }.start()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(message: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Root Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تطبيق عبدالله للشبكات (مروت)")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
