package com.abdullah.wifibridge.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class HotspotService : Service() {

    private lateinit var wifiManager: WifiManager

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ssid = intent?.getStringExtra("SSID") ?: "Abdullah-WiFi-Bridge"
        val password = intent?.getStringExtra("PASSWORD") ?: "12345678"

        startForegroundServiceNotification()
        enableHotspotProgrammatically(ssid, password)

        return START_STICKY
    }

    /**
     * تفعيل بث الـ Hotspot بالاسم وكلمة المرور المحددة حقيقياً
     */
    private fun enableHotspotProgrammatically(ssid: String, pass: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // لأجهزة Android 8.0 و 8.1
                val wifiConfig = WifiConfiguration().apply {
                    SSID = ssid
                    preSharedKey = pass
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                }

                // استدعاء دالة النظام المخفية (Reflection) لتشغيل الـ Hotspot بالـ SSID المخصص
                val setWifiApEnabledMethod = wifiManager.javaClass.getMethod(
                    "setWifiApEnabled",
                    WifiConfiguration::class.java,
                    Boolean::class.javaPrimitiveType
                )
                setWifiApEnabledMethod.invoke(wifiManager, wifiConfig, true)

            } else {
                // للأجهزة الأقدم
                val wifiConfig = WifiConfiguration().apply {
                    SSID = ssid
                    preSharedKey = pass
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
                val method = wifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(wifiManager, wifiConfig, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // إذا فشل الـ Reflection في الإصدارات الحديثة بسبب قيود النظام، يتم استخدام LocalOnlyHotspot
            startLocalOnlyHotspot()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocalOnlyHotspot() {
        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                super.onStarted(reservation)
                val config = reservation?.wifiConfiguration
                // هنا نحصل على الـ SSID الحقيقي والكلمة التي ينشئها النظام تلقائياً
                val actualSsid = config?.SSID
                val actualPassword = config?.preSharedKey
                
                // إرسال البيانات الحقيقية للواجهة لتحدث الباركود
                val broadcastIntent = Intent("ACTION_HOTSPOT_STARTED").apply {
                    putExtra("ACTUAL_SSID", actualSsid)
                    putExtra("ACTUAL_PASSWORD", actualPassword)
                }
                sendBroadcast(broadcastIntent)
            }

            override fun onFailed(reason: Int) {
                super.onFailed(reason)
            }
        }, null)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "wifi_bridge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "WiFi Bridge Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("خادم خيارات البث يعمل")
            .setContentText("جاري بث شبكة الواي فاي والجسر...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
