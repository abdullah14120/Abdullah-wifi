package com.abdullah.wifibridge.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdullah.wifibridge.databinding.ActivityMainBinding
import com.abdullah.wifibridge.server.HotspotService
import com.abdullah.wifibridge.utils.QRCodeGenerator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    // مستلم تحديثات حالة البث والاسم الحقيقي للشبكة
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
                startBridgeServer()
            } else {
                stopBridgeServer()
            }
        }
    }

    private fun startBridgeServer() {
        val intent = Intent(this, HotspotService::class.java)
        ContextCompat.startForegroundService(this, intent)
        binding.tvStatus.text = "جاري تهيئة بث الشبكة وتوليد الكود..."
    }

    private fun updateUiWithActualHotspot(ssid: String, pass: String) {
        isServerRunning = true
        binding.btnToggleServer.text = "إيقاف خادم البث"

        // تحديث حقول الواجهة بالاسم والكلمة الحقيقية المفعّلة في النظام
        binding.etSsid.setText(ssid)
        binding.etPassword.setText(pass)

        // توليد الـ QR Code بالبيانات الحقيقية للتأكد من نجاح الاتصال 100%
        val qrBitmap = QRCodeGenerator.generateWifiQrCode(ssid, pass)
        if (qrBitmap != null) {
            binding.ivQrCode.setImageBitmap(qrBitmap)
            binding.tvQrSsidInfo.text = "SSID: $ssid | Pass: $pass"
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
