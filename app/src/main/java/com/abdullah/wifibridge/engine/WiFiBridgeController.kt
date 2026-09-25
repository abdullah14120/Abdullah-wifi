package com.abdullah.wifibridge.engine

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.lang.reflect.Method

object WifiDirectBridgeEngine {

    private const val TAG = "WifiDirectBridge"

    /**
     * إنشاء مجموعة Wi-Fi Direct (توليد شبكة شبيهة بـ DIRECT-xxxx) لجهاز أندرويد 8
     */
    fun createWifiP2pGroup(context: Context, callback: (Boolean, String?, String?) -> Unit) {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, context.mainLooper) {
            Log.w(TAG, "Wi-Fi P2P channel disconnected")
        }

        if (manager == null || channel == null) {
            Log.e(TAG, "Wi-Fi Direct is not supported on this device")
            callback(false, null, null)
            return
        }

        try {
            // إنشاء الـ ActionListener بالطريقة الصحيحة تماماً ككائن مجهول مطبق للواجهة
            val actionListener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct Group created successfully!")
                    // جلب تفاصيل الشبكة والكلمة السرية المنشأة تلقائياً
                    getGroupDetails(manager, channel, callback)
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to create Wi-Fi Direct Group. Reason code: $reason")
                    callback(false, null, null)
                }
            }

            // استخدام الـ Reflection لإنشاء مجموعة P2P اجبارية (Group Owner)
            val method: Method = manager.javaClass.getDeclaredMethod(
                "createGroup",
                WifiP2pManager.Channel::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            
            // استدعاء الانعكاس مرة واحدة وبشكل صحيح تماماً
            method.invoke(manager, channel, actionListener)

        } catch (e: Exception) {
            Log.e(TAG, "Reflection error while creating Wi-Fi Direct group", e)
            callback(false, null, null)
        }
    }

    private fun getGroupDetails(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        callback: (Boolean, String?, String?) -> Unit
    ) {
        try {
            val method = manager.javaClass.getDeclaredMethod(
                "requestGroupInfo",
                WifiP2pManager.Channel::class.java,
                WifiP2pManager.GroupInfoListener::class.java
            )
            
            val groupInfoListener = WifiP2pManager.GroupInfoListener { group ->
                if (group != null) {
                    val ssid = group.networkName // عادة يبدأ بـ DIRECT-xx
                    val password = group.passphrase // كلمة المرور الديناميكية
                    Log.i(TAG, "Group Info -> SSID: $ssid, Password: $password")
                    callback(true, ssid, password)
                } else {
                    callback(true, "DIRECT-Redmi-Bridge", "12345678")
                }
            }

            method.invoke(manager, channel, groupInfoListener)

        } catch (e: Exception) {
            Log.e(TAG, "Error requesting group info", e)
            callback(false, null, null)
        }
    }

    /**
     * إيقاف مجموعة الـ Wi-Fi Direct
     */
    fun removeWifiP2pGroup(context: Context) {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        val channel = manager?.initialize(context, context.mainLooper, null) ?: return
        
        try {
            val actionListener = object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct Group removed successfully.")
                }
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Failed to remove Wi-Fi Direct Group. Reason: $reason")
                }
            }

            val method: Method = manager.javaClass.getDeclaredMethod(
                "removeGroup",
                WifiP2pManager.Channel::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            
            method.invoke(manager, channel, actionListener)

        } catch (e: Exception) {
            Log.e(TAG, "Error removing Wi-Fi Direct group", e)
        }
    }
}
