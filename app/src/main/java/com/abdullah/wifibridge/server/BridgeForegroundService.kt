package com.abdullah.wifibridge.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine
import com.abdullah.wifibridge.engine.WifiDirectBridgeEngine

class BridgeForegroundService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val CHANNEL_ID = "wifi_bridge_service_channel"
        private const val NOTIFICATION_ID = 1337
    }

    private var outboundInterface = "rmnet_data0" // واجهة بيانات الجوال الافتراضية للخروج (WAN)
    private var hotspotInterface = "p2p-wlan0-0"  // واجهة الـ Wi-Fi Direct الافتراضية

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1
        val customDns = intent?.getStringExtra("DNS_SERVER") ?: "1.1.1.1"

        // بدء الخدمة في الواجهة الأمامية لمنع النظام من قتلها
        val notification = buildNotification("جاري تهيئة بيئة الجسر وتفعيل Wi-Fi Direct...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // تنفيذ دورة التشغيل المتكاملة عبر مسار مستقل (Background Thread)
        Thread {
            try {
                Log.i(TAG, "=== Starting Wi-Fi Direct Bridge Pipeline ===")

                // 1. تفعيل صلاحيات الـ IP Forwarding ونواة اللينكس
                RootNetworkMasterEngine.enableIpForwarding()

                // 2. تنظيف البيئة مسبقاً لإزالة أي تعارض سابق في قواعد الـ iptables
                RootNetworkMasterEngine.performDeepEnvironmentSanitization(hotspotInterface)

                // 3. إنشاء شبكة Wi-Fi Direct (P2P Group Owner) المماثلة للتطبيق الناجح
                WifiDirectBridgeEngine.createWifiP2pGroup(applicationContext) { success, ssid, password ->
                    if (success) {
                        Log.i(TAG, "Wi-Fi Direct Group Active! SSID: $ssid | Password: $password")
                        
                        // تحديث الإشعار باسم الشبكة المنشأة
                        updateNotification("البث نشط: $ssid | كلمة المرور: $password")

                        // 4. تطبيق تزييف الـ MAC Address لحماية الخصوصية على الواجهة النشطة
                        RootNetworkMasterEngine.randomizeHotspotMac(hotspotInterface)

                        // 5. تطبيق إعدادات الـ Subnet المخصص وقواعد الـ NAT للربط بالإنترنت (WAN)
                        val natSuccess = RootNetworkMasterEngine.setupCustomSubnetAndNat(
                            subnetX, gatewayY, outboundInterface, hotspotInterface
                        )

                        if (natSuccess) {
                            Log.i(TAG, "Custom Subnet & NAT Routing applied successfully!")
                        } else {
                            Log.w(TAG, "Failed to apply NAT routing rules, retrying with fallback...")
                        }

                        // 6. فرض توجيه الـ DNS قسرياً (DNS Hijacking) للسرعة والأمان
                        RootNetworkMasterEngine.applyForcedDnsRedirection(hotspotInterface, customDns)
                        Log.i(TAG, "Forced DNS Redirection applied to: $customDns")

                    } else {
                        Log.e(TAG, "Failed to create Wi-Fi Direct Group.")
                        updateNotification("فشل إنشاء شبكة البث عبر الـ P2P!")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Critical error during bridge initialization pipeline", e)
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Stopping Bridge Service and cleaning up network environment...")
        
        // تنظيف شامل وإيقاف مجموعة الـ P2P وقواعد الحماية عند إغلاق التطبيق
        Thread {
            WifiDirectBridgeEngine.removeWifiP2pGroup(applicationContext)
            RootNetworkMasterEngine.flushAllRules()
        }.start()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wi-Fi Bridge Engine (Root)")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wi-Fi Bridge Engine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
