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

        // تشغيل المحرك تلقائياً باستخدام الكاشف الذكي وقواعد الـ NAT للروت
        Thread {
            // استخدام الأسماء الصحيحة والدقيقة المطابقة لـ NetworkInterfaceScanner
            val wan = NetworkInterfaceScanner.getActiveWanInterface() ?: "rmnet_data0"
            val lan = NetworkInterfaceScanner.getHotspotInterface()

            // تنفيذ أوامر الروت لتفعيل التوجيه والـ NAT
            RootNetworkMasterEngine.enableIpForwarding()
            RootNetworkMasterEngine.setupNatRules(wan, lan)
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        // تنظيف القواعد وإيقاف التوجيه وإلغاء الـ iptables عند إيقاف الخدمة نهائياً
        Thread {
            RootNetworkMasterEngine.flushAllRules()
        }.start()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Root Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة خدمة بث الشبكة عبر الروت"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تطبيق عبدالله للشبكات (مروت)")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
