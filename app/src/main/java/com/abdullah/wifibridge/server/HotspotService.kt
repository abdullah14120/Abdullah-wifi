package com.abdullah.wifibridge.server

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.abdullah.wifibridge.model.NetworkConfig

class HotspotService : Service() {

    private var proxyEngine: DualProxyEngine? = null
    private var p2pManager: WifiP2pServerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subnetX = intent?.getIntExtra("SUBNET_X", 10) ?: 10
        val hostY = intent?.getIntExtra("HOST_Y", 1) ?: 1
        val gatewayY = intent?.getIntExtra("GATEWAY_Y", 254) ?: 254

        val config = NetworkConfig(subnetX = subnetX, hostY = hostY, gatewayY = gatewayY)

        startNotification(config)

        // تفعيل WakeLock لحماية السيرفر من خمول المعالج
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiBridge::HotspotLock")
        wakeLock?.acquire(2 * 60 * 60 * 1000L /* ساعتان */)

        // 1. بدء تشغيل مجموعات الواي فاي Direct
        p2pManager = WifiP2pServerManager(this)
        p2pManager?.startP2pGroup(
            onGroupCreated = {
                // 2. تشغيل خادم البروكسي Dual Engine عند نجاح البث
                proxyEngine = DualProxyEngine(config)
                proxyEngine?.start()
            },
            onError = {
                stopSelf()
            }
        )

        return START_STICKY
    }

    private fun startNotification(config: NetworkConfig) {
        val channelId = "HOTSPOT_SERVICE_CHANNEL"
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(channelId, "Wi-Fi Bridge Server", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("خادم البث الافتراضي يعمل بنجاح")
            .setContentText("IP: ${config.localIpAddress} | Gateway: ${config.routerGatewayAddress}")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        proxyEngine?.stop()
        p2pManager?.stopGroup()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
