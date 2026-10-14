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

        // 1. التحقق من دعم الجهاز
        if (currentManager == null || currentChannel == null) {
            onFailure("Wi-Fi Direct غير مدعوم في هذا الجهاز")
            return
        }

        // 2. التحقق من الصلاحيات
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            // في أندرويد 13 والإصدارات الأقدم، قد لا تكون هذه الصلاحية المطلوبة بدقة هنا ولكن نتحقق منها احتياطياً
            // سيتم تجاهل الفشل إذا كان النظام أقدم
        }

        // 3. إزالة أي مجموعة سابقة لتجنب التعارض
        currentManager.removeGroup(currentChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = createNewGroup(currentManager, currentChannel, customSsid, customPassword, onSuccess, onFailure)
            override fun onFailure(reason: Int) = createNewGroup(currentManager, currentChannel, customSsid, customPassword, onSuccess, onFailure)
        })
    }

    @SuppressLint("MissingPermission")
    private fun createNewGroup(
        currentManager: WifiP2pManager,
        currentChannel: WifiP2pManager.Channel,
        customSsid: String,
        customPassword: String,
        onSuccess: (String, String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // الطريقة الحديثة لدعم الإعدادات المخصصة (أندرويد 10 وما فوق)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && customSsid.isNotEmpty() && customPassword.length >= 8) {
            try {
                // جوجل تجبرنا على أن يبدأ الاسم بـ DIRECT- يليه حرفين أو رقمين ثم شرطة
                val validSsid = if (customSsid.startsWith("DIRECT-")) {
                    customSsid
                } else {
                    "DIRECT-a1-$customSsid"
                }

                val config = WifiP2pConfig.Builder()
                    .setNetworkName(validSsid)
                    .setPassphrase(customPassword)
                    .build()

                currentManager.createGroup(currentChannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        onSuccess(validSsid, customPassword)
                    }

                    override fun onFailure(reason: Int) {
                        onFailure("فشل إنشاء المجموعة المخصصة (الكود: $reason)")
                    }
                })
            } catch (e: Exception) {
                onFailure("خطأ في بناء بيانات الشبكة: ${e.message}")
            }
        } else {
            // الطريقة الكلاسيكية للأجهزة القديمة أو في حال كانت البيانات المخصصة فارغة/قصيرة
            currentManager.createGroup(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Handler(Looper.getMainLooper()).postDelayed({
                        currentManager.requestGroupInfo(currentChannel) { group ->
                            if (group != null && group.isGroupOwner) {
                                onSuccess(group.networkName, group.passphrase ?: "12345678")
                            } else {
                                onFailure("لم يتم تعيين الجهاز كمضيف للشبكة.")
                            }
                        }
                    }, 500)
                }

                override fun onFailure(reason: Int) {
                    onFailure("فشل البث التلقائي، كود: $reason")
                }
            })
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
