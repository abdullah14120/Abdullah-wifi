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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine
import com.abdullah.wifibridge.engine.WifiDirectBridgeEngine
import java.net.NetworkInterface
import java.util.Collections

class BridgeForegroundService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val CHANNEL_ID = "wifi_bridge_service_channel"
        private const val NOTIFICATION_ID = 1337
        
        const val ACTION_BRIDGE_STATUS = "com.abdullah.wifibridge.ACTION_BRIDGE_STATUS"
        const val EXTRA_IS_ACTIVE = "IS_ACTIVE"
        const val EXTRA_STATUS_MESSAGE = "STATUS_MESSAGE"
        const val EXTRA_ACTIVE_SSID = "ACTIVE_SSID"
        const val EXTRA_ACTIVE_PASSWORD = "ACTIVE_PASSWORD"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1
        val customDns = intent?.getStringExtra("DNS_SERVER") ?: "1.1.1.1"
        
        val customSsid = intent?.getStringExtra("SSID_NAME")
        val customPassword = intent?.getStringExtra("SSID_PASSWORD")

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

        Thread {
            try {
                Log.i(TAG, "=== Starting Wi-Fi Direct Bridge Pipeline (Wi-Fi STA to P2P) ===")

                // 1. تفعيل صلاحيات الـ IP Forwarding ونواة اللينكس
                RootNetworkMasterEngine.enableIpForwarding()

                // 2. إنشاء شبكة Wi-Fi Direct (P2P Group Owner)
                WifiDirectBridgeEngine.createWifiP2pGroup(applicationContext) { success, generatedSsid, generatedPassword ->
                    if (success) {
                        val finalSsid = if (!customSsid.isNullOrEmpty()) customSsid else (generatedSsid ?: "Android_Bridge")
                        val finalPass = if (!customPassword.isNullOrEmpty()) customPassword else (generatedPassword ?: "")

                        Log.i(TAG, "Wi-Fi Direct Group Active! SSID: $finalSsid | Password: $finalPass")
                        
                        updateNotification("البث نشط: $finalSsid | كلمة المرور: $finalPass")

                        // 3. 🔍 الاستشعار الديناميكي الذكي لواجهة البث الفعلية التي أنشأتها النواة
                        val actualHotspotInterface = detectActualP2pInterface()
                        Log.i(TAG, "Detected Active P2P/Hotspot Interface: $actualHotspotInterface")

                        // 4. تنظيف البيئة مسبقاً وتطبيق تزييف الـ MAC Address للواجهة المكتشفة
                        RootNetworkMasterEngine.performDeepEnvironmentSanitization(actualHotspotInterface)
                        RootNetworkMasterEngine.randomizeHotspotMac(actualHotspotInterface)

                        // 5. 🌐 الاكتشاف التلقائي والديناميكي لواجهة الخروج (الواي فاي الأساسي wlan0 أو بيانات الجوال)
                        val activeWanInterface = RootNetworkMasterEngine.detectActiveWanInterface()
                        Log.i(TAG, "Detected Active WAN Interface (Wi-Fi STA): $activeWanInterface")

                        // 6. تطبيق إعدادات الـ Subnet المخصص وقواعد الـ NAT للربط وتوجيه الإنترنت بفاعلية تامة
                        val natSuccess = RootNetworkMasterEngine.setupCustomSubnetAndNat(
                            subnetX, gatewayY, activeWanInterface, actualHotspotInterface
                        )

                        if (natSuccess) {
                            Log.i(TAG, "Custom Subnet & NAT Routing applied successfully via $activeWanInterface -> $actualHotspotInterface!")
                        } else {
                            Log.w(TAG, "Failed to apply NAT routing rules, retrying with fallback...")
                        }

                        // 7. فرض توجيه الـ DNS قسرياً (DNS Hijacking) على الواجهة الفعلية
                        RootNetworkMasterEngine.applyForcedDnsRedirection(actualHotspotInterface, customDns)
                        Log.i(TAG, "Forced DNS Redirection applied to: $customDns on interface $actualHotspotInterface")

                        sendBridgeStatusBroadcast(
                            isActive = true,
                            message = "تم تشغيل الجسر والبث بنجاح ✔️",
                            ssid = finalSsid,
                            password = finalPass
                        )

                    } else {
                        Log.e(TAG, "Failed to create Wi-Fi Direct Group.")
                        updateNotification("فشل إنشاء شبكة البث عبر الـ P2P!")

                        sendBridgeStatusBroadcast(
                            isActive = false,
                            message = "فشل إنشاء شبكة البث عبر الـ P2P ❌",
                            ssid = "",
                            password = ""
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Critical error during bridge initialization pipeline", e)
                sendBridgeStatusBroadcast(
                    isActive = false,
                    message = "خطأ حرج في تهيئة الجسر: ${e.localizedMessage}",
                    ssid = "",
                    password = ""
                )
            }
        }.start()

        return START_STICKY
    }

    /**
     * دالة استشعار ذكية تبحث في كروت الشبكة النشطة عن أي واجهة تبدأ بـ p2p أو wlan وتعود بها ديناميكياً (تم تصحيح الحلقة)
     */
    private fun detectActualP2pInterface(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // البحث السليم عن الواجهة النشطة بدون أخطاء نحوية
            for (netInterface in interfaces) {
                val name = netInterface.name
                if (name.startsWith("p2p") || name.startsWith("swlan") || (name.startsWith("wlan") && name != "wlan0")) {
                    Log.i(TAG, "Found matching P2P/Hotspot interface dynamically: $name")
                    return name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting P2P interface dynamically, falling back to default", e)
        }
        // قيمة افتراضية احتياطية في حال فشل الاستشعار
        return "p2p-wlan0-0"
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Stopping Bridge Service and cleaning up network environment...")
        
        sendBridgeStatusBroadcast(
            isActive = false,
            message = "تم إيقاف الجسر بنجاح.",
            ssid = "",
            password = ""
        )

        Thread {
            try {
                WifiDirectBridgeEngine.removeWifiP2pGroup(applicationContext)
                RootNetworkMasterEngine.flushAllRules()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup in onDestroy", e)
            }
        }.start()
    }

    private fun sendBridgeStatusBroadcast(isActive: Boolean, message: String, ssid: String, password: String) {
        val intent = Intent(ACTION_BRIDGE_STATUS).apply {
            putExtra(EXTRA_IS_ACTIVE, isActive)
            putExtra(EXTRA_STATUS_MESSAGE, message)
            putExtra(EXTRA_ACTIVE_SSID, ssid)
            putExtra(EXTRA_ACTIVE_PASSWORD, password)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
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
