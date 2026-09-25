package com.abdullah.wifibridge

import android.content.Intent
import android.graphics.Color
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

        // فحص صلاحيات الروت أولاً عند الإقلاع باستخدام الدالة الصحيحة checkRootAccess()
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
        statusTextView.text = "جاري التحقق من صلاحيات الجذر (Root)..."
        Thread {
            // التصحيح: استدعاء checkRootAccess الموجودة في RootNetworkMasterEngine
            val hasRoot = RootNetworkMasterEngine.checkRootAccess()
            runOnUiThread {
                if (hasRoot) {
                    statusTextView.text = "حالة الجهاز: تم الحصول على صلاحيات الجذر (Root) بنجاح ✔️"
                    statusTextView.setTextColor(Color.parseColor("#4CAF50"))
                    btnToggleBridge.isEnabled = true
                } else {
                    statusTextView.text = "تحذير: لم يتم منح صلاحيات الروت! التطبيق لن يعمل."
                    statusTextView.setTextColor(Color.RED)
                    btnToggleBridge.isEnabled = false
                }
            }
        }.start()
    }

    private fun startBridgeService() {
        val serviceIntent = Intent(this, BridgeForegroundService::class.java).apply {
            action = "ACTION_START_BRIDGE"
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isBridgeActive = true
        btnToggleBridge.text = "إيقاف الجسر الشفاف"
        btnToggleBridge.setBackgroundColor(Color.RED)
        Toast.makeText(this, "تم تشغيل الجسر الشفاف بدون قيود!", Toast.LENGTH_SHORT).show()
    }

    private fun stopBridgeService() {
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        stopService(serviceIntent)
        
        isBridgeActive = false
        btnToggleBridge.text = "تشغيل الجسر الشفاف"
        btnToggleBridge.setBackgroundColor(Color.parseColor("#4CAF50"))
        Toast.makeText(this, "تم إيقاف الجسر وإعادة تعيين الشبكة.", Toast.LENGTH_SHORT).show()
    }
}
