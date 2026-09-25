package com.abdullah.wifibridge.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abdullah.wifibridge.R
import com.abdullah.wifibridge.engine.NetworkInterfaceScanner
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine

class BridgeForegroundService : android.app.Service() {

    companion object {
        const val TAG = "BridgeForegroundService"
        const val CHANNEL_ID = "WifiBridgeRootChannel"
        const val NOTIFICATION_ID = 1001
    }

    // نحتفظ بمرجع لواجهة البث النشطة لضمان تنظيفها بدقة عند التوقف (onDestroy)
    @Volatile
    private var activeHotspotInterface: String = "ap0"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // قراءة المتغيرات أو تطبيق التدوير العشوائي (Dynamic Subnet Rotation) في حال لم يتم تحديدها
        val useDynamicSubnet = intent?.getBooleanExtra("USE_DYNAMIC_SUBNET", true) ?: true
        
        val subnetX: Int
        val gatewayY: Int
        
        if (useDynamicSubnet) {
            val dynamicPair = RootNetworkMasterEngine.generateDynamicSubnet()
            subnetX = dynamicPair.first
            gatewayY = dynamicPair.second
            Log.i(TAG, "Dynamic Subnet Rotation Applied -> Subnet: 192.168.$subnetX.$gatewayY")
        } else {
            subnetX = intent?.getIntExtra("SUBNET_X", 50) ?: 50
            gatewayY = intent?.getIntExtra("GATEWAY_Y", 1) ?: 1
        }

        val ssidName = intent?.getStringExtra("SSID_NAME") ?: "Abdullah_Bridge_Root"
        val ssidPassword = intent?.getStringExtra("SSID_PASSWORD") ?: "12345678"
        val dnsServer = intent?.getStringExtra("DNS_SERVER") ?: "1.1.1.1"

        // بدء الخدمة الأمامية فوراً بإشعار أولي للحفاظ على استقرار الـ Foreground Lifecycle
        val initialNotification = createNotification("جاري تهيئة بيئة البث وعزل الشبكة...")
        startForeground(NOTIFICATION_ID, initialNotification)

        Thread {
            try {
                Log.i(TAG, "=== Starting Wi-Bridge Full Pipeline with Deep Sanitization ===")

                // الخطوة 1: التحقق الحازم من صلاحيات الروت
                if (!RootNetworkMasterEngine.checkRootAccess()) {
                    Log.e(TAG, "Critical Error: Root access is denied or unavailable!")
                    stopSelf()
                    return@Thread
                }

                // الخطوة 2: الانتظار الذكي (Polling) المسبق لتحديد الواجهة المتوقعة أو البدء بـ ap0 لتنفيذ التنظيف العميق عليها
                val preliminaryInterface = NetworkInterfaceScanner.getHotspotInterface() ?: "ap0"
                activeHotspotInterface = preliminaryInterface

                // الخطوة 3: تنفيذ بروتوكول التنظيف العميق (Deep Environment Sanitization) لتفريغ جداول النواة القديمة
                Log.i(TAG, "Executing Deep Sanitization on interface: $activeHotspotInterface")
                RootNetworkMasterEngine.performDeepEnvironmentSanitization(activeHotspotInterface)

                // الخطوة 4: تفعيل الـ IP Forwarding في النواة
                RootNetworkMasterEngine.enableIpForwarding()

                // الخطوة 5: تشغيل نقطة الاتصال (SoftAP) إجبارياً وتجاوز عشوائية النظام بالاسم وكلمة المرور
                RootNetworkMasterEngine.forceConfigureAndStartSoftAp(ssidName, ssidPassword)

                // الخطوة 6: إعادة فحص واستقرار واجهة البث بعد تفعيل الـ SoftAP الفعلي
                var hotspotInterface: String? = null
                var attempts = 0
                val maxAttempts = 15 // محاولة لمدة تصل إلى 7.5 ثانية كحد أقصى

                while (attempts < maxAttempts) {
                    hotspotInterface = NetworkInterfaceScanner.getHotspotInterface()
                    if (!hotspotInterface.isNullOrEmpty()) {
                        Log.i(TAG, "Hotspot interface successfully stabilized: $hotspotInterface on attempt ${attempts + 1}")
                        break
                    }
                    Thread.sleep(500)
                    attempts++
                }

                if (!hotspotInterface.isNullOrEmpty()) {
                    activeHotspotInterface = hotspotInterface
                } else {
                    Log.w(TAG, "Hotspot interface detection timed out. Maintaining fallback: $activeHotspotInterface")
                }

                // الخطوة 6.1: التحقق الفوري الاختياري من نجاح الفرض عبر الـ Logcat
                val configVerificationResult = RootNetworkMasterEngine.verifyActiveSoftApConfig()
                Log.i(TAG, "SoftAP Active Config Verification:\n$configVerificationResult")

                // الخطوة 7: تطبيق بصمة الـ MAC العشوائية (MAC Spoofing) للطبقة الثانية (Layer 2)
                Log.i(TAG, "Applying random MAC spoofing to interface: $activeHotspotInterface")
                RootNetworkMasterEngine.randomizeHotspotMac(activeHotspotInterface)

                // الخطوة 8: جلب واجهة الخروج للإنترنت (WAN Interface) ديناميكياً
                val wanInterface = NetworkInterfaceScanner.getActiveWanInterface() ?: "rmnet_data0"
                Log.i(TAG, "Outbound WAN interface resolved to: $wanInterface")

                // الخطوة 9: رفع وتطبيق الـ Subnet المخصص (أو الديناميكي) وإعداد قواعد الـ NAT والجدار الناري
                val subnetSuccess = RootNetworkMasterEngine.setupCustomSubnetAndNat(
                    subnetX, gatewayY, wanInterface, activeHotspotInterface
                )
                if (!subnetSuccess) {
                    Log.e(TAG, "Failed to setup custom subnet and NAT forwarding rules!")
                }

                // الخطوة 10: تطبيق وحقن قواعد توجيه واعتراض الـ DNS قسرياً (DNS Hijacking) لمنع تسريب الطلبات
                val dnsSuccess = RootNetworkMasterEngine.applyForcedDnsRedirection(
                    activeHotspotInterface, dnsServer
                )
                if (dnsSuccess) {
                    Log.i(TAG, "Forced DNS redirection successfully locked to $dnsServer")
                } else {
                    Log.e(TAG, "Failed to apply forced DNS redirection rules!")
                }

                // تحديث الإشعار النهائي ليدل على حالة العمل النشطة مع تفاصيل الشبكة الحالية
                updateNotification("بث ($ssidName) | 192.168.$subnetX.$gatewayY | DNS: $dnsServer")
                Log.i(TAG, "=== Wi-Bridge Pipeline Successfully Established & Secured ===")

            } catch (e: Exception) {
                Log.e(TAG, "Critical exception in BridgeForegroundService execution pipeline", e)
                stopSelf()
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        // تنفيذ دورة التنظيف الشاملة وإزالة كافة قواعد الـ iptables، تفريغ الـ ARP، وإيقاف الـ AP عند التوقف
        Thread {
            try {
                Log.i(TAG, "Initiating Clean Shutdown & Environment Sanitization...")
                // تشغيل التنظيف العميق للواجهة النشطة قبل الإغلاق التام
                RootNetworkMasterEngine.performDeepEnvironmentSanitization(activeHotspotInterface)
                // إغلاق النظام نهائياً
                RootNetworkMasterEngine.flushAllRules()
                Log.i(TAG, "Bridge service successfully stopped and environment sanitized.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during service cleanup and environment sanitization", e)
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
                description = "قناة مخصصة لخدمة جسر شبكة الواي فاي ذات صلاحيات الروت مع العزل التام"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("تطبيق عبدالله للشبكات (حماية متقدمة - روت)")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }
}
