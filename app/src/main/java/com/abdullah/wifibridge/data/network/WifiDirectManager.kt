package com.abdullah.wifibridge.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

class WifiDirectManager(context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    @SuppressLint("MissingPermission")
    fun startBroadcast(
        onSuccess: (networkName: String, password: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (manager == null || channel == null) {
            onFailure("Wi-Fi Direct غير مدعوم في هذا الجهاز")
            return
        }

        // إزالة أي شبكة معلقة أولاً لتجنب التعارض
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = createNewGroup(onSuccess, onFailure)
            override fun onFailure(reason: Int) = createNewGroup(onSuccess, onFailure)
        })
    }

    @SuppressLint("MissingPermission")
    private fun createNewGroup(
        onSuccess: (String, String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // الطريقة الحديثة والمستقرة جداً لأندرويد 10 وما فوق (نفس طريقة NetShare)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // توليد حرفين عشوائيين ليتوافق مع شروط شبكات P2P (يجب أن تبدأ بـ DIRECT-xy)
            val randomChars = (1..2).map { "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".random() }.joinToString("")
            val networkName = "DIRECT-$randomChars-Bridge"
            val password = Random.nextInt(10000000, 99999999).toString() // كلمة مرور من 8 أرقام

            val config = WifiP2pConfig.Builder()
                .setNetworkName(networkName)
                .setPassphrase(password)
                .build()

            manager?.createGroup(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // تم البث بنجاح بالإعدادات التي فرضناها نحن
                    onSuccess(networkName, password)
                }
                override fun onFailure(reason: Int) {
                    onFailure("فشل البث الحديث، كود: $reason")
                }
            })
        } else {
            // الطريقة الكلاسيكية للأجهزة القديمة
            manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Handler(Looper.getMainLooper()).postDelayed({
                        manager.requestGroupInfo(channel) { group ->
                            if (group != null && group.isGroupOwner) {
                                onSuccess(group.networkName, group.passphrase ?: "12345678")
                            } else {
                                onFailure("لم يتم تعيين الجهاز كمضيف.")
                            }
                        }
                    }, 500)
                }
                override fun onFailure(reason: Int) {
                    onFailure("فشل البث، كود: $reason")
                }
            })
        }
    }

    fun stopBroadcast() {
        manager?.removeGroup(channel, null)
    }
}
