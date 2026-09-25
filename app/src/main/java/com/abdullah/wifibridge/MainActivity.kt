package com.abdullah.wifibridge

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abdullah.wifibridge.engine.RootNetworkMasterEngine
import com.abdullah.wifibridge.server.BridgeForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var btnToggleBridge: Button
    private lateinit var etSubnetX: EditText
    private lateinit var etGatewayY: EditText
    private lateinit var etSsidName: EditText
    private lateinit var etSsidPassword: EditText

    private var isBridgeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط عناصر واجهة المستخدم بالمتغيرات البرمجية
        statusTextView = findViewById(R.id.statusTextView)
        btnToggleBridge = findViewById(R.id.btnToggleBridge)
        etSubnetX = findViewById(R.id.etSubnetX)
        etGatewayY = findViewById(R.id.etGatewayY)
        etSsidName = findViewById(R.id.etSsidName)
        etSsidPassword = findViewById(R.id.etSsidPassword)

        // فحص صلاحيات الروت عند بدء التطبيق
        checkRootAccess()

        btnToggleBridge.setOnClickListener {
            if (!isBridgeActive) {
                startBridgeService()
            } else {
                stopBridgeService()
            }
        }
    }

    /**
     * فحص توفر صلاحيات الجذر (Root) وإدارتها في الواجهة
     */
    private fun checkRootAccess() {
        statusTextView.text = "جاري التحقق من صلاحيات الجذر (Root)..."
        Thread {
            val hasRoot = RootNetworkMasterEngine.checkRootAccess()
            runOnUiThread {
                if (hasRoot) {
                    statusTextView.text = "حالة الجهاز: تم الحصول على صلاحيات الجذر (Root) بنجاح ✔️"
                    statusTextView.setTextColor(Color.parseColor("#4CAF50"))
                    btnToggleBridge.isEnabled = true
                } else {
                    statusTextView.text = "تحذير: لم يتم منح صلاحيات الروت! التطبيق لن يعمل بدون صلاحيات الجذر."
                    statusTextView.setTextColor(Color.RED)
                    btnToggleBridge.isEnabled = false
                }
            }
        }.start()
    }

    /**
     * بدء خدمة الجسر الشفاف وتمرير بيانات الشبكة واسم الحزمة والبث
     */
    private fun startBridgeService() {
        val subnetStr = etSubnetX.text.toString().trim()
        val gatewayStr = etGatewayY.text.toString().trim()
        val ssidInput = etSsidName.text.toString().trim()
        val passwordInput = etSsidPassword.text.toString().trim()

        // التحقق من صحة المدخلات (كلمة المرور يجب أن تكون 8 خانات على الأقل لـ WPA2)
        if (ssidInput.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال اسم الشبكة (SSID)", Toast.LENGTH_SHORT).show()
            return
        }

        if (passwordInput.length < 8) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 8 خانات على الأقل!", Toast.LENGTH_SHORT).show()
            return
        }

        val subnetX = if (subnetStr.isNotEmpty()) subnetStr.toInt() else 50
        val gatewayY = if (gatewayStr.isNotEmpty()) gatewayStr.toInt() else 1

        // تجهيز الـ Intent وتمرير القيم المخصصة للخدمة الخلفية
        val serviceIntent = Intent(this, BridgeForegroundService::class.java).apply {
            putExtra("SUBNET_X", subnetX)
            putExtra("GATEWAY_Y", gatewayY)
            putExtra("SSID_NAME", ssidInput)
            putExtra("SSID_PASSWORD", passwordInput)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isBridgeActive = true
        btnToggleBridge.text = "إيقاف الجسر الشفاف والبث"
        btnToggleBridge.setBackgroundColor(Color.RED)
        Toast.makeText(this, "جاري بث ($ssidInput) على Subnet: 192.168.$subnetX.$gatewayY", Toast.LENGTH_LONG).show()
    }

    /**
     * إيقاف الخدمة وتحرير واجهات الشبكة وقواعد الحماية
     */
    private fun stopBridgeService() {
        val serviceIntent = Intent(this, BridgeForegroundService::class.java)
        stopService(serviceIntent)
        
        isBridgeActive = false
        btnToggleBridge.text = "تشغيل الجسر الشفاف والبث"
        btnToggleBridge.setBackgroundColor(Color.parseColor("#4CAF50"))
        Toast.makeText(this, "تم إيقاف البث وإعادة تعيين الشبكة بنجاح.", Toast.LENGTH_SHORT).show()
    }
}
