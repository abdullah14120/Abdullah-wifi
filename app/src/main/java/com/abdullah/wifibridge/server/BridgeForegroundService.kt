package com.abdullah.wifibridge.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abdullah.wifibridge.R
import com.abdullah.wifibridge.engine.NetworkInterfaceScanner
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine

class BridgeForegroundService : Service() {

    companion object {
        const val TAG = "BridgeForegroundService"
        const val CHANNEL_ID = "WifiBridgeRootChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // استقبال المتغيرات الممررة من واجهة المستخدم بدقة عالية
        val subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1
        val ssidName = intent?.getStringExtra("SSID_NAME") ?: "Abdullah_Bridge_Root"
        val ssidPassword = intent?.getStringExtra("SSID_PASSWORD") ?: "12345678"
        val dnsServer = intent?.getStringExtra("DNS_SERVER") ?: "1.1.1.1"

        // بدء الخدمة الأمامية مع إشعار حالة النظام
        val notification = createNotification("بث ($ssidName) | 192.168.$subnetX.$gatewayY | DNS: $dnsServer")
        startForeground(NOTIFICATION_ID, notification)

        Thread {
            try {
                Log.i(TAG, "=== Starting Wi-Bridge Full Pipeline ===")

                // الخطوة 1: تفعيل صلاحيات الروت وضمان عمل الـ IP Forwarding في النواة
                if (!RootNetworkMasterEngine.checkRootAccess()) {
                    Log.e(TAG, "Root access is denied or unavailable!")
                    stopSelf()
                    return@Thread
                }
                RootNetworkMasterEngine.enableIpForwarding()

                // الخطوة 2: تشغيل نقطة الاتصال (SoftAP) عبر النظام بالاسم وكلمة المرور
                RootNetworkMasterEngine.configureAndStartSoftAp(ssidName, ssidPassword)

                // الخطوة 3: الانتظار الذكي (Polling) لظهور واستقرار واجهة البث دون الاعتماد على وقت ثابت فقط
                var hotspotInterface: String? = null
                var attempts = 0
                val maxAttempts = 15 // محاولة لمدة تصل إلى 7.5 ثانية كحد أقصى

                while (attempts < maxAttempts) {
                    hotspotInterface = NetworkInterfaceScanner.getHotspotInterface()
                    if (!hotspotInterface.isNullOrEmpty()) {
                        Log.i(TAG, "Hotspot interface detected: $hotspotInterface on attempt ${attempts + 1}")
                        break
                    }
                    Thread.sleep(500)
                    attempts++
                }

                // إذا فشل النظام في توفير واجهة البث الافتراضية، نعتمد على القيمة الاحتياطية ap0
                val finalHotspotInterface = if (hotspotInterface.isNullOrEmpty()) {
                    Log.w(TAG, "Hotspot interface detection timed out. Falling back to default 'ap0'")
                    "ap0"
                } else {
                    hotspotInterface
                }

                // الخطوة 4: تغيير وتوليد عنوان MAC عشوائي لواجهة البث (MAC Spoofing / Randomization)
                Log.i(TAG, "Applying random MAC spoofing to interface: $finalHotspotInterface")
                RootNetworkMasterEngine.randomizeHotspotMac(finalHotspotInterface)

                // الخطوة 5: جلب واجهة الخروج للإنترنت (WAN Interface) ديناميكياً
                val wanInterface = NetworkInterfaceScanner.getActiveWanInterface() ?: "rmnet_data0"
                Log.i(TAG, "Outbound WAN interface resolved to: $wanInterface")

                // الخطوة 6: رفع وتطبيق الـ IP Subnet المخصص وإعداد قواعد الـ NAT والجدار الناري (Forwarding & Masquerading)
                val subnetSuccess = RootNetworkMasterEngine.setupCustomSubnetAndNat(
                    subnetX, gatewayY, wanInterface, finalHotspotInterface
                )
                if (!subnetSuccess) {
                    Log.e(TAG, "Failed to setup custom subnet and NAT rules!")
                }

                // الخطوة 7: تطبيق وحقن قواعد توجيه واعتراض الـ DNS قسرياً (DNS Hijacking) لمنع التسريب نهائياً
                val dnsSuccess = RootNetworkMasterEngine.applyForcedDnsRedirection(
                    finalHotspotInterface, dnsServer
                )
                if (dnsSuccess) {
                    Log.i(TAG, "Forced DNS redirection successfully applied to $dnsServer")
                } else {
                    Log.e(TAG, "Failed to apply forced DNS redirection rules!")
                }

                Log.i(TAG, "=== Wi-Bridge Pipeline Successfully Established ===")

            } catch (e: Exception) {
                Log.e(TAG, "Critical error in BridgeForegroundService execution thread", e)
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        // تنفيذ دورة التنظيف الشاملة وإزالة كافة قواعد الـ iptables وإيقاف الـ AP عند التوقف
        Thread {
            try {
                Log.i(TAG, "Flushing all network rules and shutting down SoftAP...")
                RootNetworkMasterEngine.flushAllRules()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up rules on service destroy", e)
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
            .setContentTitle("تطبيق عبدالله للشبكات (مروت - عزل تام)")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
