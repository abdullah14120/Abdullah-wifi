package com.abdullah.wifibridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
.os.Build
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
        val subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1

        val notification = createNotification("البث يعمل على 192.168.$subnetX.$gatewayY وبدون قيود...")
        startForeground(NOTIFICATION_ID, notification)

        Thread {
            // 1. تفعيل IP Forwarding للنواة
            RootNetworkMasterEngine.enableIpForwarding()

            // 2. تفعيل نقطة اتصال افتراضية أو إجبار واجهة البث على الـ Subnet المخصص عبر أوامر الروت
            // مثال: تعيين الـ IP المخصص لبطاقة الواي فاي المحلية (ap0 أو wlan0)
            val hotspotInterface = NetworkInterfaceScanner.getHotspotInterface()
            val customGatewayIp = "192.168.$subnetX.$gatewayY"
            
            // تطبيق الـ IP المخصص على واجهة البث بواسطة أداة ip address المدمجة في أندرويد (صلاحية روت)
            RootNetworkMasterEngine.executeRootCommand("ip addr add $customGatewayIp/24 dev $hotspotInterface")

            // 3. جلب الواجهة النشطة للإنترنت (Data أو Wi-Fi الأساسي) وتطبيق قواعد NAT
            val wan = NetworkInterfaceScanner.getActiveWanInterface() ?: "rmnet_data0"
            RootNetworkMasterEngine.setupNatRules(wan, hotspotInterface)

        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
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
            )
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
