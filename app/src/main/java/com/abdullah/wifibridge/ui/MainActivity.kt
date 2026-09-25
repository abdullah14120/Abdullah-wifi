package com.abdullah.wifibridge.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abdullah.wifibridge.databinding.ActivityMainBinding
import com.abdullah.wifibridge.model.NetworkConfig
import com.abdullah.wifibridge.server.HotspotService

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
        val subnetX = binding.etSubnetX.text.toString().toIntOrNull() ?: 10
        val hostY = binding.etHostY.text.toString().toIntOrNull() ?: 1
        val gatewayY = binding.etGatewayY.text.toString().toIntOrNull() ?: 254

        val intent = Intent(this, HotspotService::class.java).apply {
            putExtra("SUBNET_X", subnetX)
            putExtra("HOST_Y", hostY)
            putExtra("GATEWAY_Y", gatewayY)
        }

        ContextCompat.startForegroundService(this, intent)

        isServerRunning = true
        binding.btnToggleServer.text = "إيقاف البث"

        val config = NetworkConfig(subnetX, hostY, gatewayY)
        binding.tvStatus.text = "البث يعمل الآن على:\nIP: ${config.localIpAddress}\nGateway: ${config.routerGatewayAddress}"
        Toast.makeText(this, "تم بدء تشغيل شبكة البث المخصصة", Toast.LENGTH_SHORT).show()
    }

    private fun stopBridgeServer() {
        val intent = Intent(this, HotspotService::class.java)
        stopService(intent)

        isServerRunning = false
        binding.btnToggleServer.text = "بدء البث المخصص"
        binding.tvStatus.text = "البث متوقف"
        Toast.makeText(this, "تم إيقاف خادم البث", Toast.LENGTH_SHORT).show()
    }
}
