package com.abdullah.wifibridge.server

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager

class WifiP2pServerManager(private val context: Context) {

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null

    init {
        channel = p2pManager?.initialize(context, context.mainLooper, null)
    }

    @SuppressLint("MissingPermission")
    fun startP2pGroup(onGroupCreated: (WifiP2pGroup) -> Unit, onError: (String) -> Unit) {
        p2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { createGroupInternal(onGroupCreated, onError) }
            override fun onFailure(reason: Int) { createGroupInternal(onGroupCreated, onError) }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createGroupInternal(onSuccess: (WifiP2pGroup) -> Unit, onError: (String) -> Unit) {
        p2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                p2pManager?.requestGroupInfo(channel) { group ->
                    if (group != null) {
                        onSuccess(group)
                    } else {
                        onError("فشل في الحصول على بيانات شبكة P2P")
                    }
                }
            }

            override fun onFailure(reason: Int) {
                onError("فشل إنشاء المجموعة، كود الخطأ: $reason")
            }
        })
    }

    fun stopGroup() {
        p2pManager?.removeGroup(channel, null)
    }
}
