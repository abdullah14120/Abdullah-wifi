package com.abdullah.wifibridge.data.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat

class WifiDirectManager(private val context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    @SuppressLint("MissingPermission")
    fun startBroadcast(
        customSsid: String = "",
        customPassword: String = "",
        onSuccess: (networkName: String, password: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentManager = manager
        val currentChannel = channel

        if (currentManager == null || currentChannel == null) {
            onFailure("Wi-Fi Direct غير مدعوم في هذا الجهاز")
            return
        }

        // إزالة المجموعة السابقة والانتظار ثانية لتهدئة الكرت
        currentManager.removeGroup(currentChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Handler(Looper.getMainLooper()).postDelayed({
                    executeCreateGroup(currentManager, currentChannel, customSsid, customPassword, onSuccess, onFailure)
                }, 1000)
            }

            override fun onFailure(reason: Int) {
                Handler(Looper.getMainLooper()).postDelayed({
                    executeCreateGroup(currentManager, currentChannel, customSsid, customPassword, onSuccess, onFailure)
                }, 1000)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun executeCreateGroup(
        currentManager: WifiP2pManager,
        currentChannel: WifiP2pManager.Channel,
        customSsid: String,
        customPassword: String,
        onSuccess: (String, String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // الطريقة الحديثة (أندرويد 10 وأحدث)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && customSsid.isNotEmpty() && customPassword.length >= 8) {
            try {
                val validSsid = if (customSsid.startsWith("DIRECT-")) customSsid else "DIRECT-a1-$customSsid"

                val config = WifiP2pConfig.Builder()
                    .setNetworkName(validSsid)
                    .setPassphrase(customPassword)
                    .build()

                currentManager.createGroup(currentChannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        onSuccess(validSsid, customPassword)
                    }
                    override fun onFailure(reason: Int) {
                        if (reason == 2) {
                            onFailure("النظام مشغول (Busy). أطفئ الواي فاي وشغله مجدداً.")
                        } else {
                            onFailure("فشل إنشاء المجموعة المخصصة (الكود: $reason)")
                        }
                    }
                })
            } catch (e: Exception) {
                onFailure("خطأ في بيانات الشبكة: ${e.message}")
            }
        } else {
            // ==========================================
            // الطريقة الكلاسيكية الجديدة (للأجهزة العنيدة)
            // ==========================================
            
            // 🚀 السر الأول: إيقاف أي عملية بحث (Discovery) قد تعطل إنشاء المجموعة
            currentManager.stopPeerDiscovery(currentChannel, null)

            // 🚀 السر الثاني: استخدام الدالة القديمة ولكن بشكل "صامت ومستمر"
            currentManager.createGroup(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    
                    // ننتظر ثانيتين كاملتين للتأكد من أن النواة قامت بتجهيز الشبكة بالكامل
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkGroupStatus(currentManager, currentChannel, onSuccess, onFailure, retryCount = 0)
                    }, 2000)
                }

                override fun onFailure(reason: Int) {
                    if (reason == 2) {
                        onFailure("النظام مشغول (Busy). يرجى إيقاف الواي فاي وتشغيله يدوياً من إعدادات الهاتف.")
                    } else {
                        onFailure("فشل البث التلقائي، كود: $reason")
                    }
                }
            })
        }
    }

    // 🚀 السر الثالث: دالة استدعاء متكررة (Recursive Check) 
    // إذا لم يكن الهاتف مضيفاً في الثانية الأولى، ستحاول مرة أخرى بعد ثانية بدلاً من الفشل فوراً!
    @SuppressLint("MissingPermission")
    private fun checkGroupStatus(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        onSuccess: (String, String) -> Unit,
        onFailure: (String) -> Unit,
        retryCount: Int
    ) {
        manager.requestGroupInfo(channel) { group ->
            if (group != null && group.isGroupOwner) {
                // نجاح! الهاتف أعلن نفسه كمضيف
                onSuccess(group.networkName, group.passphrase ?: "12345678")
            } else {
                if (retryCount < 3) { // المحاولة 3 مرات كحد أقصى (تأخير 1 ثانية بين كل محاولة)
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkGroupStatus(manager, channel, onSuccess, onFailure, retryCount + 1)
                    }, 1000)
                } else {
                    onFailure("لم يتم تعيين الجهاز كمضيف للشبكة. النظام يرفض فتح البث.")
                }
            }
        }
    }

    fun stopBroadcast() {
        val currentManager = manager
        val currentChannel = channel
        if (currentManager != null && currentChannel != null) {
            currentManager.removeGroup(currentChannel, null)
        }
    }
}
