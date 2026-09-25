package com.abdullah.wifibridge.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdullah.wifibridge.databinding.ActivityMainBinding
import com.abdullah.wifibridge.server.HotspotService
import com.abdullah.wifibridge.utils.QRCodeGenerator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    // 1. مسجل الأذونات التفاعلي الذكي
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissionsAndProceed()
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
        try {
            unregisterReceiver(hotspotReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
     * فحص الأذونات بذكاء ومراعاة إصدار النظام واختلافات الواجهات (MIUI / Stock)
     */
    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. أذونات الموقع الجغرافي (مطلوبة لجميع الإصدارات لتشغيل Hotspot/Wi-Fi Direct)
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 2. إذن الأجهزة المجاورة (مطلوب فقط بدءاً من Android 12 / API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        // 3. إذن الإشعارات (مطلوب فقط بدءاً من Android 13 / API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkPermissionsAndProceed()
        }
    }

    /**
     * التحقق النهائي قبل التمرير وبدء الخدمة
     */
    private fun checkPermissionsAndProceed() {
        val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || 
                          hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            true // غير موجود في أندرويد 11 وما قبله
        }

        if (!hasLocation || !hasNearby) {
            showPermissionExplanationDialog()
            return
        }

        // التأكد من أن مفتاح الـ GPS مفعّل في الجهاز (شرط أساسي لـ LocalOnlyHotspot على أندرويد 11/12)
        if (!isLocationServiceEnabled()) {
            showEnableLocationDialog()
            return
        }

        checkAndRequestBatteryOptimization()
        startBridgeServerService()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * التحقق مما إذا كانت خدمة الموقع (GPS) مفعلة بالنظام
     */
    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showEnableLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل خدمة الموقع (GPS)")
            .setMessage("يتطلب نظام أندرويد تفعيل خدمة الموقع الجغرافي في الجهاز لتشغيل نقطة الإتصال وبث الواي فاي.")
            .setPositiveButton("تفعيل") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPermissionExplanationDialog() {
        val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "يتطلب التطبيق إذن الموقع وإذن الأجهزة المجاورة لتشغيل خادم البث والواي فاي. يرجى تفعيلها من الإعدادات."
        } else {
            "يتطلب التطبيق إذن الموقع الجغرافي لتشغيل خادم البث والواي فاي. يرجى تفعيله من الإعدادات."
        }

        AlertDialog.Builder(this)
            .setTitle("الأذونات مطلوبة")
            .setMessage(msg)
            .setPositiveButton("الانتقال للإعدادات") { _, _ -> openAppSettings() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * توجيه المستخدم لصفحة إعدادات التطبيق بمرونة لتغطية واجهات Redmi / MIUI
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح الإعدادات تلقائياً", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * طلب استثناء تحسينات البطارية لعدم إغلاق سيرفر البروكسي في الخلفية
     */
    @SuppressLint("BatteryLife")
    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "يرجى تعطيل موفر البطارية للتطبيق لضمان استقرار البث", Toast.LENGTH_SHORT).show()
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

        try {
            ContextCompat.startForegroundService(this, intent)
            binding.tvStatus.text = "جاري تهيئة بث الشبكة وتوليد الكود..."
        } catch (e: Exception) {
            Toast.makeText(this, "فشل بدء الخدمة: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUiWithActualHotspot(ssid: String, pass: String) {
        isServerRunning = true
        binding.btnToggleServer.text = "إيقاف خادم البث"

        binding.etSsid.setText(ssid)
        binding.etPassword.setText(pass)

        val subnetX = binding.etSubnetX.text.toString().ifEmpty { "10" }
        val hostY = binding.etHostY.text.toString().ifEmpty { "1" }
        val serverIp = "192.168.$subnetX.$hostY"

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
