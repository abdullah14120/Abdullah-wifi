package com.abdullah.wifibridge.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdullah.wifibridge.databinding.ActivityMainBinding
import com.abdullah.wifibridge.model.NetworkConfig
import com.abdullah.wifibridge.server.HotspotService
import com.abdullah.wifibridge.utils.QRCodeGenerator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
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
        val ssid = binding.etSsid.text.toString().ifEmpty { "Abdullah-WiFi-Bridge" }
        val password = binding.etPassword.text.toString().ifEmpty { "12345678" }
        val subnetX = binding.etSubnetX.text.toString().toIntOrNull() ?: 10
        val hostY = binding.etHostY.text.toString().toIntOrNull() ?: 1
        val gatewayY = binding.etGatewayY.text.toString().toIntOrNull() ?: 254

        val intent = Intent(this, HotspotService::class.java).apply {
            putExtra("SSID", ssid)
            putExtra("PASSWORD", password)
            putExtra("SUBNET_X", subnetX)
            putExtra("HOST_Y", hostY)
            putExtra("GATEWAY_Y", gatewayY)
        }

        ContextCompat.startForegroundService(this, intent)

        // توليد وعرض الـ QR Code للعملاء
        val qrBitmap = QRCodeGenerator.generateWifiQrCode(ssid, password)
        if (qrBitmap != null) {
            binding.ivQrCode.setImageBitmap(qrBitmap)
            binding.tvQrSsidInfo.text = "SSID: $ssid | Pass: $password"
            binding.cardQrContainer.visibility = View.VISIBLE
        }

        isServerRunning = true
        binding.btnToggleServer.text = "إيقاف خادم البث"

        val config = NetworkConfig(subnetX, hostY, gatewayY)
        binding.tvStatus.text = "البث يعمل الآن على:\nIP: ${config.localIpAddress}\nGateway: ${config.routerGatewayAddress}"
        Toast.makeText(this, "تم تشغيل الشبكة وتوليد الـ QR Code", Toast.LENGTH_SHORT).show()
    }

    private fun stopBridgeServer() {
        val intent = Intent(this, HotspotService::class.java)
        stopService(intent)

        isServerRunning = false
        binding.cardQrContainer.visibility = View.GONE
        binding.btnToggleServer.text = "بدء البث المخصص وتوليد QR"
        binding.tvStatus.text = "البث متوقف"
        Toast.makeText(this, "تم إيقاف الخادم", Toast.LENGTH_SHORT).show()
    }
}
