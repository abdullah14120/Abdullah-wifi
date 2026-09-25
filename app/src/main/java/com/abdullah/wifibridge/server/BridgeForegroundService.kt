package com.abdullah.wifibridge.server

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
        // استقبال المتغيرات الممررة من واجهة المستخدم بما فيها خادم الـ DNS
        val subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1
        val ssidName = intent?.getStringExtra("SSID_NAME") ?: "Abdullah_Bridge_Root"
        val ssidPassword = intent?.getStringExtra("SSID_PASSWORD") ?: "12345678"
        val dnsServer = intent?.getStringExtra("DNS_SERVER") ?: "1.1.1.1"

        // بدء الخدمة الأمامية مع رسالة واضحة تحتوي على اسم الشبكة، النطاق، وخادم الـ DNS الموثوق
        val notification = createNotification("بث ($ssidName) | 192.168.$subnetX.$gatewayY | DNS: $dnsServer")
        startForeground(NOTIFICATION_ID, notification)

        Thread {
            try {
                // 1. تفعيل IP Forwarding في نواة النظام (Kernel) لتمرير البيانات بين الواجهات
                RootNetworkMasterEngine.enableIpForwarding()

                // 2. ضبط إعدادات نقطة الاتصال (SoftAP) بالاسم وكلمة المرور وتفعيلها إجبارياً
                RootNetworkMasterEngine.configureAndStartSoftAp(ssidName, ssidPassword)

                // انتظار ثوانٍ معدودة حتى تقوم تعريفات الشريحة بتهيئة واستقرار واجهة البث (ap0 أو wlan0)
                Thread.sleep(2500)

                // 3. جلب واجهة البث والواجهة النشطة للإنترنت (WAN) ديناميكياً
                val hotspotInterface = NetworkInterfaceScanner.getHotspotInterface() ?: "ap0"
                val wan = NetworkInterfaceScanner.getActiveWanInterface() ?: "rmnet_data0"

                // 4. تطبيق الـ Subnet المخصص (الآي بي وجهاز التوجيه) وقواعد الجدار الناري NAT
                RootNetworkMasterEngine.setupCustomSubnetAndNat(subnetX, gatewayY, wan, hotspotInterface)

                // 5. تطبيق وقفل توجيه الـ DNS قسرياً عبر قواعد iptables للـ DNS المختار
                RootNetworkMasterEngine.applyForcedDnsRedirection(hotspotInterface, dnsServer)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        // تنظيف وإلغاء جميع قواعد الـ iptables وإيقاف توجيه الشبكة عند إغلاق الخدمة
        Thread {
            try {
                RootNetworkMasterEngine.flushAllRules()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                description = "قناة مخصصة لخدمة جسر شبكة الواي فاي ذات صلاحيات الروت"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تطبيق عبدالله للشبكات (مروت - DNS آمن)")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
