package com.abdullah.wifibridge.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HotspotService : Service() {

    private lateinit var wifiManager: WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    companion object {
        const val ACTION_HOTSPOT_STATE = "com.abdullah.wifibridge.HOTSPOT_STATE"
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_IS_RUNNING = "extra_is_running"
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceNotification()
        startSystemLocalHotspot()
        return START_STICKY
    }

    private fun startSystemLocalHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation
                        
                        // استخراج اسم الشبكة وكلمة المرور الحقيقيين من النظام
                        val config = reservation?.wifiConfiguration
                        val actualSsid = config?.SSID ?: "Unknown"
                        val actualPassword = config?.preSharedKey ?: ""

                        // إرسال البيانات الحقيقية لـ MainActivity لتحديث الـ QR Code
                        sendHotspotBroadcast(true, actualSsid, actualPassword)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        sendHotspotBroadcast(false, "", "")
                    }
                }, null)
            } catch (e: Exception) {
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

    private fun startForegroundServiceNotification() {
        val channelId = "wifi_bridge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "WiFi Bridge", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("خادم نقطة البث الافتراضية")
            .setContentText("جاري إدارة الشبكة والتحويل...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        // إغلاق البث عند إيقاف الخدمة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hotspotReservation?.close()
        }
        sendHotspotBroadcast(false, "", "")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
