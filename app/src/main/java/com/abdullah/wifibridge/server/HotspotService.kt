package com.abdullah.wifibridge.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * خدمة محرك الجسر والبث الافتراضي (Hotspot Engine & Smart Proxy Server).
 * صُممت للعمل بكفاءة استثنائية على Android 14+ وجميع الإصدارات السابقة مع حماية أقفال الطاقة والشبكة.
 */
class HotspotService : Service() {

    // =========================================================================
    // 1. حالة الخدمة الهيكلية (State Management for UI Binding)
    // =========================================================================
    data class HotspotState(
        val isRunning: Boolean = false,
        val ssid: String = "",
        val password: String = "",
        val port: Int = PROXY_PORT,
        val ipAddress: String = ""
    )

    private val _serviceState = MutableStateFlow(HotspotState())
    val serviceState: StateFlow<HotspotState> = _serviceState.asStateFlow()

    // =========================================================================
    // 2. المكونات والأقفال البرمجية
    // =========================================================================
    private lateinit var wifiManager: WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var proxyServer: SmartProxyServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): HotspotService = this@HotspotService
    }

    companion object {
        const val ACTION_HOTSPOT_STATE = "com.abdullah.wifibridge.HOTSPOT_STATE"
        const val ACTION_STOP_SERVICE = "com.abdullah.wifibridge.ACTION_STOP_SERVICE"

        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_SUBNET_X = "extra_subnet_x"
        const val EXTRA_HOST_Y = "extra_host_y"
        const val EXTRA_GATEWAY_Y = "extra_gateway_y"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "wifi_bridge_channel"
        private const val TAG = "HotspotService"

        private const val PROXY_PORT = 8080
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        acquireLocks()
        Log.d(TAG, "HotspotService Created & Locks Acquired.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // إذا كان الأمر صادراً لإيقاف الخدمة من الإشعار العلوي
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val subnetX = intent?.getIntExtra(EXTRA_SUBNET_X, 10) ?: 10
        val hostY = intent?.getIntExtra(EXTRA_HOST_Y, 1) ?: 1
        val ipStr = "192.168.$subnetX.$hostY"

        // 1. بدء إشعار الخدمة بالخلفية بأقصى درجات الحماية الحصينة المتوافقة مع API 34+
        startForegroundServiceNotification(ipStr)

        // 2. تشغيل خادم البروكسي الذكي
        startProxyEngine()

        // 3. تشغيل نقطة البث الافتراضية للواي فاي
        startSystemLocalHotspot(ipStr)

        return START_STICKY
    }

    // =========================================================================
    // 3. المحركات التشغيلية (Proxy & SoftAP Hotspot)
    // =========================================================================
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

    private fun startSystemLocalHotspot(ipAddress: String) {
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

                        // تحديث حالة التدفق لمراقبات Flow اللحظية
                        _serviceState.value = HotspotState(
                            isRunning = true,
                            ssid = actualSsid,
                            password = actualPassword,
                            port = PROXY_PORT,
                            ipAddress = ipAddress
                        )

                        // إرسال البيانات الحقيقية عبر Broadcast للواجهات الكلاسيكية
                        sendHotspotBroadcast(true, actualSsid, actualPassword)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        Log.e(TAG, "Local Hotspot failed with reason code: $reason")
                        updateStateFailed()
                    }
                }, null)
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied starting Local Hotspot (Location/Nearby missing): ${e.message}")
                updateStateFailed()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Wi-Fi state invalid or Hotspot already active: ${e.message}")
                updateStateFailed()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception starting Local Hotspot: ${e.message}", e)
                updateStateFailed()
            }
        } else {
            Log.e(TAG, "LocalOnlyHotspot requires Android 8.0 (API 26) or higher.")
            updateStateFailed()
        }
    }

    private fun updateStateFailed() {
        _serviceState.value = HotspotState(isRunning = false)
        sendHotspotBroadcast(false, "", "")
    }

    private fun sendHotspotBroadcast(isRunning: Boolean, ssid: String, pass: String) {
        try {
            val intent = Intent(ACTION_HOTSPOT_STATE).apply {
                putExtra(EXTRA_IS_RUNNING, isRunning)
                putExtra(EXTRA_SSID, ssid)
                putExtra(EXTRA_PASSWORD, pass)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send hotspot broadcast: ${e.message}")
        }
    }

    // =========================================================================
    // 4. إدارة الإشعارات و Foreground Service (Android 14 Compatible)
    // =========================================================================
    private fun startForegroundServiceNotification(ipAddress: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Bridge Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة إدارة شبكة البث الافتراضية وخادم التحويل"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // إعداد زر الإيقاف المباشر من داخل الإشعار
        val stopIntent = Intent(this, HotspotService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("خادم البث الافتراضي يعمل")
            .setContentText("IP الخادم: $ipAddress | البورت: $PROXY_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف البث", stopPendingIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // تحديد كلا النوعين لاستقرار الخدمة التام على أندرويد 14
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or 
                                   ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(NOTIFICATION_ID, notification, serviceTypes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    // =========================================================================
    // 5. إدارة الأقفال والذاكرة (WakeLock & WifiLock)
    // =========================================================================
    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WiFiBridge::HotspotWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(10 * 60 * 60 * 1000L) // 10 ساعات أمان
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }

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
        // 1. إيقاف خادم البروكسي
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

        // 3. تحرير الأقفال وتحديث الحالة
        releaseLocks()
        updateStateFailed()

        Log.d(TAG, "HotspotService Destroyed successfully.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
