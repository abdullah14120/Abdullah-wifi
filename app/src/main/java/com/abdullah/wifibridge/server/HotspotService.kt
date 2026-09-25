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
    
    // أقفال الطاقة والواي فاي لمنع قطع الاتصال أثناء الخمول
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

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
        
        // منفذ البروكسي الافتراضي
        private const val PROXY_PORT = 8080
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subnetX = intent?.getIntExtra(EXTRA_SUBNET_X, 10) ?: 10
        val hostY = intent?.getIntExtra(EXTRA_HOST_Y, 1) ?: 1
        val gatewayY = intent?.getIntExtra(EXTRA_GATEWAY_Y, 254) ?: 254

        // 1. بدء إشعار الخدمة بالخلفية بأقصى درجات الحماية والحصانة
        startForegroundServiceNotification(subnetX, hostY)

        // 2. تشغيل خادم البروكسي الذكي
        startProxyEngine()

        // 3. تشغيل نقطة البث الافتراضية للواي فاي
        startSystemLocalHotspot()

        return START_STICKY
    }

    private fun startProxyEngine() {
        try {
            if (proxyServer == null) {
                proxyServer = SmartProxyServer(applicationContext, PROXY_PORT)
                proxyServer?.start()
                Log.d(TAG, "SmartProxyServer started successfully on port $PROXY_PORT.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SmartProxyServer: ${e.message}", e)
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

                        try {
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
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error parsing SoftAp/Wifi configuration: ${ex.message}")
                        }

                        Log.d(TAG, "Local Hotspot started successfully: SSID=$actualSsid")

                        // إرسال البيانات الحقيقية للواجهة لتحديث الـ QR Code والمعلومات
                        sendHotspotBroadcast(true, actualSsid, actualPassword)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "Local Hotspot failed with reason code: $reason")
                        sendHotspotBroadcast(false, "", "")
                    }
                }, null)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied starting Local Hotspot (Location/Nearby missing): ${e.message}")
                sendHotspotBroadcast(false, "", "")
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Wi-Fi state invalid or Hotspot already active: ${e.message}")
                sendHotspotBroadcast(false, "", "")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception starting Local Hotspot: ${e.message}", e)
                sendHotspotBroadcast(false, "", "")
            }
        } else {
            Log.e(TAG, "LocalOnlyHotspot requires Android 8.0 (API 26) or higher.")
            sendHotspotBroadcast(false, "", "")
        }
    }

    private fun sendHotspotBroadcast(isRunning: Boolean, ssid: String, pass: String) {
        try {
            val intent = Intent(ACTION_HOTSPOT_STATE).apply {
                putExtra(EXTRA_IS_RUNNING, isRunning)
                putExtra(EXTRA_SSID, ssid)
                putExtra(EXTRA_PASSWORD, pass)
                // ضمان وصول الإرسال للتطبيق فقط لمنع التسريب الخارجي
                setPackage(packageName)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send hotspot broadcast: ${e.message}")
        }
    }

    private fun startForegroundServiceNotification(subnetX: Int, hostY: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة إدارة شبكة البث الافتراضية وخادم التحويل"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("خادم البث الافتراضي يعمل")
            .setContentText("IP الخادم: 192.168.$subnetX.$hostY | البورت: $PROXY_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        // 1. حماية وتفعيل Power WakeLock
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WiFiBridge::HotspotWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 60 * 1000L) // 10 ساعات كحد أقصى
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "WAKE_LOCK permission is missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }

        // 2. حماية وتفعيل Wi-Fi High Performance Lock
        try {
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager.createWifiLock(mode, "WiFiBridge::WifiLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WifiLock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WifiLock: ${e.message}")
        } finally {
            wifiLock = null
        }
    }

    override fun onDestroy() {
        // 1. إيقاف خادم البروكسي وتفريغه من الذاكرة
        try {
            proxyServer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy server: ${e.message}")
        } finally {
            proxyServer = null
        }

        // 2. إغلاق البث الخاص بالنظام
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                hotspotReservation?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing hotspot reservation: ${e.message}")
            } finally {
                hotspotReservation = null
            }
        }

        // 3. تحرير أقفال المعالج والواي فاي
        releaseLocks()

        // 4. إشعار بقية مكونات التطبيق بانتهاء البث
        sendHotspotBroadcast(false, "", "")

        Log.d(TAG, "HotspotService Destroyed successfully.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
