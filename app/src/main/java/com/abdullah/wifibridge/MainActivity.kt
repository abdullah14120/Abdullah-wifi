package com.abdullah.wifibridge

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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
    private lateinit var spinnerDns: Spinner
    private lateinit var etCustomDns: EditText

    private var isBridgeActive = false
    private var selectedDnsServer = "1.1.1.1" // القيمة الافتراضية الموثوقة (Cloudflare)

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
        spinnerDns = findViewById(R.id.spinnerDns)
        etCustomDns = findViewById(R.id.etCustomDns)

        // إعداد خيارات قائمة الـ DNS الموثوقة
        setupDnsSpinner()

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
     * إعداد خيارات الـ DNS الموثوقة في الـ Spinner مع إمكانية إدخال DNS مخصص
     */
    private fun setupDnsSpinner() {
        val dnsOptions = arrayOf(
            "Cloudflare (1.1.1.1 - موثوق وسريع)",
            "Google (8.8.8.8 - استقرار عالي)",
            "Quad9 (9.9.9.9 - حماية أمنية مضاعفة)",
            "تخصيص DNS يدوي..."
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dnsOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDns.adapter = adapter

        spinnerDns.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        selectedDnsServer = "1.1.1.1"
                        etCustomDns.visibility = View.GONE
                    }
                    1 -> {
                        selectedDnsServer = "8.8.8.8"
                        etCustomDns.visibility = View.GONE
                    }
                    2 -> {
                        selectedDnsServer = "9.9.9.9"
                        etCustomDns.visibility = View.GONE
                    }
                    3 -> {
                        // إظهار حقل الإدخال اليدوي إذا اختار المستخدم تخصيص DNS
                        etCustomDns.visibility = View.VISIBLE
                        val customVal = etCustomDns.text.toString().trim()
                        if (customVal.isNotEmpty()) {
                            selectedDnsServer = customVal
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDnsServer = "1.1.1.1"
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
     * بدء خدمة الجسر الشفاف وتمرير بيانات الشبكة واسم البث والـ DNS المختار
     */
    private fun startBridgeService() {
        val subnetStr = etSubnetX.text.toString().trim()
        val gatewayStr = etGatewayY.text.toString().trim()
        val ssidInput = etSsidName.text.toString().trim()
        val passwordInput = etSsidPassword.text.toString().trim()

        // إذا كان الخيار المختار هو التخصيص اليدوي، نأخذ القيمة من الحقل المخصص
        if (spinnerDns.selectedItemPosition == 3) {
            val customDnsInput = etCustomDns.text.toString().trim()
            if (customDnsInput.isNotEmpty()) {
                selectedDnsServer = customDnsInput
            } else {
                Toast.makeText(this, "يرجى إدخال عنوان IP مخصص للـ DNS!", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // التحقق من صحة المدخلات الأساسية
        if (ssidInput.isEmpty()) {
            Toast.makeText(this, "يرجى إدخال اسم الشبكة (SSID)", Toast.LENGTH_SHORT).show()
            return
        }

        if (passwordInput.length < 8) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 8 خانات على الأقل لـ WPA2!", Toast.LENGTH_SHORT).show()
            return
        }

        val subnetX = if (subnetStr.isNotEmpty()) subnetStr.toInt() else 50
        val gatewayY = if (gatewayStr.isNotEmpty()) gatewayStr.toInt() else 1

        // تجهيز الـ Intent وتمرير القيم المخصصة بما فيها الـ DNS المختار للخدمة الخلفية
        val serviceIntent = Intent(this, BridgeForegroundService::class.java).apply {
            putExtra("SUBNET_X", subnetX)
            putExtra("GATEWAY_Y", gatewayY)
            putExtra("SSID_NAME", ssidInput)
            putExtra("SSID_PASSWORD", passwordInput)
            putExtra("DNS_SERVER", selectedDnsServer)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isBridgeActive = true
        btnToggleBridge.text = "إيقاف الجسر الشفاف والبث"
        btnToggleBridge.setBackgroundColor(Color.RED)
        Toast.makeText(this, "جاري البث ($ssidInput) على Subnet: 192.168.$subnetX.$gatewayY | DNS: $selectedDnsServer", Toast.LENGTH_LONG).show()
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
