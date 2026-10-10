package com.abdullah.wifibridge.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper

class WifiDirectManager(context: Context) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

    @SuppressLint("MissingPermission") // سيتم طلب الصلاحيات من الواجهة
    fun startBroadcast(
        onSuccess: (networkName: String, password: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (manager == null || channel == null) {
            onFailure("Wi-Fi Direct غير مدعوم في هذا الجهاز")
            return
        }

        // إزالة أي شبكة معلقة أولاً لتجنب التعارض، ثم إنشاء شبكة جديدة
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
        manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // الانتظار نصف ثانية لضمان تهيئة النظام للشبكة
                Thread.sleep(500)
                manager.requestGroupInfo(channel) { group ->
                    if (group != null && group.isGroupOwner) {
                        // إرجاع اسم الشبكة وكلمة المرور
                        onSuccess(group.networkName, group.passphrase ?: "بدون كلمة مرور")
                    } else {
                        onFailure("تم الإنشاء ولكن لم يتم تعيين الجهاز كمضيف.")
                    }
                }
            }
            override fun onFailure(reason: Int) {
                onFailure("فشل البث، كود الخطأ: $reason")
            }
        })
    }

    fun stopBroadcast() {
        manager?.removeGroup(channel, null)
    }
}
