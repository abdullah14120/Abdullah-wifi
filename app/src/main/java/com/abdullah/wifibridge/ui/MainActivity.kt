package com.abdullah.wifibridge.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdullah.wifibridge.databinding.ActivityMainBinding
import com.abdullah.wifibridge.server.HotspotService
import com.abdullah.wifibridge.utils.QRCodeGenerator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    // 1. مسجل الأذونات الحديث (Multiple Permissions Launcher)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkAndRequestBatteryOptimization()
            startBridgeServerService()
        } else {
            Toast.makeText(
                this,
                "يلزم تقديم جميع الأذونات لتمكين بث الواي فاي وتوليد الشبكة!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // 2. مستلم تحديثات حالة البث والاسم الحقيقي للشبكة من HotspotService
    private val hotspotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == HotspotService.ACTION_HOTSPOT_STATE) {
                val isRunning = intent.getBooleanExtra(HotspotService.EXTRA_IS_RUNNING, false)
                val actualSsid = intent.getStringExtra(HotspotService.EXTRA_SSID) ?: ""
                val actualPassword = intent.getStringExtra(HotspotService.EXTRA_PASSWORD) ?: ""

                if (isRunning) {
                    updateUiWithActualHotspot(actualSsid, actualPassword)
                } else {
                    stopBridgeServerUi()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(HotspotService.ACTION_HOTSPOT_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hotspotReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(hotspotReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(hotspotReceiver)
    }

    private fun setupListeners() {
        binding.btnToggleServer.setOnClickListener {
            if (!isServerRunning) {
                checkPermissionsAndStart()
            } else {
                stopBridgeServer()
            }
        }
    }

    /**
     * التحقق من الأذونات المطلوبة حسب إصدار النظام قبل بدء الخدمة
     */
    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf<String>()

        // أذونات الموقع الجغرافي الأساسية
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // إذن الأجهزة المجاورة للواي فاي في أندرويد 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // إذن الإشعارات لأندرويد 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkAndRequestBatteryOptimization()
            startBridgeServerService()
        }
    }

    /**
     * طلب تجاهل تحسينات البطارية لضمان عدم إغلاق سيرفر البروكسي في الخلفية
     */
    @SuppressLint("BatteryLife")
    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "يرجى تعطيل تحسين البطارية للتطبيق من الإعدادات", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * قراءة إعدادات الشبكة المخصصة وإرسالها للخدمة لبدء البث
     */
    private fun startBridgeServerService() {
        val subnetX = binding.etSubnetX.text.toString().toIntOrNull() ?: 10
        val hostY = binding.etHostY.text.toString().toIntOrNull() ?: 1
        val gatewayY = binding.etGatewayY.text.toString().toIntOrNull() ?: 254

        val intent = Intent(this, HotspotService::class.java).apply {
            putExtra(HotspotService.EXTRA_SUBNET_X, subnetX)
            putExtra(HotspotService.EXTRA_HOST_Y, hostY)
            putExtra(HotspotService.EXTRA_GATEWAY_Y, gatewayY)
        }

        ContextCompat.startForegroundService(this, intent)
        binding.tvStatus.text = "جاري تهيئة بث الشبكة وتوليد الكود..."
    }

    private fun updateUiWithActualHotspot(ssid: String, pass: String) {
        isServerRunning = true
        binding.btnToggleServer.text = "إيقاف خادم البث"

        // تحديث حقول الواجهة بالاسم والكلمة الحقيقية المفعّلة في النظام
        binding.etSsid.setText(ssid)
        binding.etPassword.setText(pass)

        // قراءة قيم IP المخصصة المعروضة
        val subnetX = binding.etSubnetX.text.toString().ifEmpty { "10" }
        val hostY = binding.etHostY.text.toString().ifEmpty { "1" }
        val serverIp = "192.168.$subnetX.$hostY"

        // توليد الـ QR Code شاملاً اسم الشبكة، الباسورد، وعنوان السيرفر
        val qrBitmap = QRCodeGenerator.generateWifiQrCode(ssid, pass)
        if (qrBitmap != null) {
            binding.ivQrCode.setImageBitmap(qrBitmap)
            binding.tvQrSsidInfo.text = "SSID: $ssid\nServer IP: $serverIp | Proxy Port: 8080"
            binding.cardQrContainer.visibility = View.VISIBLE
        }

        binding.tvStatus.text = "تم تفعيل البث بنجاح!\nالاسم الحقيقي: $ssid"
        Toast.makeText(this, "تم تفعيل البث وتحديث الباركود", Toast.LENGTH_SHORT).show()
    }

    private fun stopBridgeServer() {
        val intent = Intent(this, HotspotService::class.java)
        stopService(intent)
        stopBridgeServerUi()
    }

    private fun stopBridgeServerUi() {
        isServerRunning = false
        binding.cardQrContainer.visibility = View.GONE
        binding.btnToggleServer.text = "بدء البث المخصص وتوليد QR"
        binding.tvStatus.text = "البث متوقف"
    }
}
