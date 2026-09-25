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

    // مسجل الأذونات الذكي
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // بعد استجابة المستخدم، نفحص ما إذا كان بإمكاننا البدء مباشرة
        checkPermissionsAndProceed(isDirectRetry = true)
    }

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
     * تجهيز قائمة الأذونات المطلوبة بحسب إصدار أندرويد وواجهة النظام
     */
    private fun checkPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. أذونات الموقع الجغرافي
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && 
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 2. إذن الأجهزة المجاورة (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        // 3. إذن الإشعارات (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkPermissionsAndProceed(isDirectRetry = false)
        }
    }

    /**
     * التحقق المرن للتأكد من إمكانية بدء الخدمة بدون تعليق الواجهة
     */
    private fun checkPermissionsAndProceed(isDirectRetry: Boolean) {
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasLocation = hasFineLocation || hasCoarseLocation

        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            true
        }

        // على أجهزة Redmi / MIUI، إذا كان إذن الموقع ممنوحاً، يمكن تشغيل LocalOnlyHotspot بنجاح حتى لو دمجت شاومي إذن الأجهزة المجاورة
        if (hasLocation || hasNearby) {
            if (!isLocationServiceEnabled()) {
                showEnableLocationDialog()
                return
            }
            checkAndRequestBatteryOptimization()
            startBridgeServerService()
        } else {
            if (!isDirectRetry) {
                showPermissionExplanationDialog()
            } else {
                Toast.makeText(this, "يرجى منحي إذن الموقع لتشغيل البث", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showEnableLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل خدمة الموقع (GPS)")
            .setMessage("يتطلب نظام أندرويد تفعيل زر الموقع الجغرافي (GPS) في الشريحة العلوية لتشغيل نقطة الاتصال المحلية.")
            .setPositiveButton("تفعيل") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPermissionExplanationDialog() {
        val msg = "يتطلب التطبيق إذن الموقع لتشغيل خادم البث والجسريات. يرجى السماح به من الإعدادات."

        AlertDialog.Builder(this)
            .setTitle("الأذونات مطلوبة")
            .setMessage(msg)
            .setPositiveButton("الانتقال للإعدادات") { _, _ -> openAppSettings() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر فتح الإعدادات", Toast.LENGTH_SHORT).show()
        }
    }

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
                    // تجنب الخلل في واجهات MIUI
                }
            }
        }
    }

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
