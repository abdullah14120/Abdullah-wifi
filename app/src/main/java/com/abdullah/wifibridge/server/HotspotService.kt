package com.abdullah.wifibridge.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class HotspotService : Service() {

    private lateinit var wifiManager: WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var proxyServer: SmartProxyServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_HOTSPOT_STATE = "com.abdullah.wifibridge.HOTSPOT_STATE"
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_SUBNET_X = "extra_subnet_x"
        const val EXTRA_HOST_Y = "extra_host_y"
        const val EXTRA_GATEWAY_Y = "extra_gateway_y"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wifi_bridge_channel"
        private const val TAG = "HotspotService"
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subnetX = intent?.getIntExtra(EXTRA_SUBNET_X, 10) ?: 10
        val hostY = intent?.getIntExtra(EXTRA_HOST_Y, 1) ?: 1
        val gatewayY = intent?.getIntExtra(EXTRA_GATEWAY_Y, 254) ?: 254

        // 1. بدء إشعار الخدمة بالخلفية
        startForegroundServiceNotification(subnetX, hostY)

        // 2. تشغيل خادم البروكسي المطور على البورت 8080 (أو البورت المخصص لديك)
        startProxyEngine()

        // 3. تشغيل نقطة البث الافتراضية للوايفاي
        startSystemLocalHotspot()

        return START_STICKY
    }

    private fun startProxyEngine() {
        try {
            if (proxyServer == null) {
                proxyServer = SmartProxyServer(applicationContext, 8080)
                proxyServer?.start()
                Log.d(TAG, "SmartProxyServer started successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SmartProxyServer: ${e.message}")
        }
    }

    private fun startSystemLocalHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation

                        var actualSsid = "Unknown"
                        var actualPassword = ""

                        // دعم استخراج بيانات الاتصال للأنظمة الحديثة (Android 11+) والقديمة
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val softApConfig = reservation?.softApConfiguration
                            actualSsid = softApConfig?.ssid ?: "Unknown"
                            actualPassword = softApConfig?.passphrase ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            val config = reservation?.wifiConfiguration
                            actualSsid = config?.SSID ?: "Unknown"
                            actualPassword = config?.preSharedKey ?: ""
                        }

                        Log.d(TAG, "Local Hotspot started: SSID=$actualSsid")

                        // إرسال البيانات الحقيقية لـ MainActivity لتحديث الـ QR Code والواجهة
                        sendHotspotBroadcast(true, actualSsid, actualPassword)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "Local Hotspot failed with reason: $reason")
                        sendHotspotBroadcast(false, "", "")
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting Local Hotspot: ${e.message}")
                e.printStackTrace()
                sendHotspotBroadcast(false, "", "")
            }
        }
    }

    private fun sendHotspotBroadcast(isRunning: Boolean, ssid: String, pass: String) {
        val intent = Intent(ACTION_HOTSPOT_STATE).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_SSID, ssid)
            putExtra(EXTRA_PASSWORD, pass)
        }
        sendBroadcast(intent)
    }

    private fun startForegroundServiceNotification(subnetX: Int, hostY: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة إدارة شبكة البث الافتراضية وخادم التحويل"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("خادم البث الافتراضي يعمل")
            .setContentText("IP الخادم: 192.168.$subnetX.$hostY | البورت: 8080")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        // معالجة قيود أندرويد 14 (API 34) للأنواع المختلفة للـ Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFiBridge::HotspotWakeLock")
        wakeLock?.acquire()
    }

    override fun onDestroy() {
        // 1. إيقاف خادم البروكسي
        proxyServer?.stop()
        proxyServer = null

        // 2. إغلاق البث الخاص بالنظام
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hotspotReservation?.close()
            hotspotReservation = null
        }

        // 3. تحرير WakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        // 4. إشعار بقية مكونات التطبيق بانتهاء البث
        sendHotspotBroadcast(false, "", "")

        Log.d(TAG, "HotspotService Destroyed successfully.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
