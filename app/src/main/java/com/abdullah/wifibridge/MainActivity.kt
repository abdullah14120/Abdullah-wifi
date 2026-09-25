package com.abdullah.wifibridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine
import com.abdullah.wifibridge.service.BridgeForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnToggleBridge: Button

    private var isBridgeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        btnToggleBridge = findViewById(R.id.btnToggleBridge)

        // فحص صلاحيات الروت أولاً عند الإقلاع
        checkRootAccess()

        btnToggleBridge.setOnClickListener {
            if (!isBridgeActive) {
                startBridgeService()
            } else {
                stopBridgeService()
            }
        }
    }

    private fun checkRootAccess() {
        Thread {
            val hasRoot = RootNetworkMasterEngine.acquireRootPrivileges()
            runOnUiThread {
                if (hasRoot) {
                    statusTextView.text = "حالة الجهاز: تم الحصول على صلاحيات الجذر (Root) بنجاح ✔️"
                } else {
                    statusTextView.text = "تحذير: لم يتم منح صلاحيات الروت! التطبيق لن يعمل."
                    statusTextView.setTextColor(android.graphics.Color.RED)
                }
            }
        }.start()
    }

    private fun startBridgeService() {
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isBridgeActive = true
        btnToggleBridge.text = "إيقاف الجسر الشفاف"
        btnToggleBridge.setBackgroundColor(android.graphics.Color.RED)
        Toast.makeText(this, "تم تشغيل الجسر الشفاف بدون قيود!", Toast.LENGTH_SHORT).show()
    }

    private fun stopBridgeService() {
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        stopService(serviceIntent)
        isBridgeActive = false
        btnToggleBridge.text = "تشغيل الجسر الشفاف"
        btnToggleBridge.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
        Toast.makeText(this, "تم إيقاف الجسر وإعادة تعيين الشبكة.", Toast.LENGTH_SHORT).show()
    }
}
