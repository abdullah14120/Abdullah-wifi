package com.abdullah.wifibridge.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
// استيراد Shizuku
import rikka.shizuku.Shizuku
import com.abdullah.wifibridge.data.shizuku.ShizukuManager

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    // تعريف مدير Shizuku للفحص الأولي
    private val shizukuManager = ShizukuManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel, shizukuManager)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel, shizukuManager: ShizukuManager) {
    val ip by viewModel.currentIp.collectAsState()
    val port by viewModel.currentPort.collectAsState()
    val isActive by viewModel.isSessionActive.collectAsState()
    val netName by viewModel.networkName.collectAsState()
    val netPass by viewModel.networkPassword.collectAsState()
    val logs by viewModel.proxyLogs.collectAsState()
    
    val context = LocalContext.current

    // التأثير الجانبي الذي يعمل مرة واحدة عند فتح الشاشة
    LaunchedEffect(Unit) {
        val status = shizukuManager.checkShizukuStatus()
        if (status == -1) {
            // إضافة سجل يفيد بأن التطبيق ينتظر Shizuku
            // يمكنك ترك هذا كتحذير صامت للمستخدم
        } else if (status == 0) {
            // إذا كان يعمل ولكن لا يملك صلاحية، نطلبها فور فتح التطبيق
            shizukuManager.requestPermission(200)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.startSession(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "WiFi Bridge Pro", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Session Details:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Gateway IP: $ip")
                Text("Proxy Port: $port")
                
                if (isActive && netName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SSID: $netName", color = MaterialTheme.colorScheme.primary)
                    Text("Password: $netPass", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (isActive) {
                    viewModel.stopSession(context)
                } else {
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    permissionLauncher.launch(permissions)
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (isActive) "Stop Broadcast" else "Start Clean Session")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.generateCleanSessionParams() },
            enabled = !isActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Clean Session")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // صندوق مراقبة السجلات الحية
        Text("Live Proxy Logs:", style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.05f))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (log.contains("خطأ") || log.contains("ERROR")) Color.Red else Color.DarkGray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}
