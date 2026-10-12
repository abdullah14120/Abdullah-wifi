package com.abdullah.wifibridge.data.network

import android.content.Context
import android.os.PowerManager

class KeepAliveManager(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquireWakeLock() {
        if (wakeLock == null) {
            // استخدام PARTIAL_WAKE_LOCK يبقي المعالج يعمل حتى لو انطفأت الشاشة
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WiFiBridge::ProxyWakeLock"
            )
            // تحديد مدة أقصاها 12 ساعة لتجنب استنزاف البطارية إذا نسي المستخدم إغلاقه
            wakeLock?.acquire(12 * 60 * 60 * 1000L) 
        }
    }

    fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }
}
