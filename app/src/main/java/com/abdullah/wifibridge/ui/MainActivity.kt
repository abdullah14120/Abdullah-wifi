package com.abdullah.wifibridge.ui

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, shizukuManager: ShizukuManager) {
    val ip by viewModel.currentIp.collectAsState()
    val port by viewModel.currentPort.collectAsState()
    val isActive by viewModel.isSessionActive.collectAsState()
    val netName by viewModel.networkName.collectAsState()
    val netPass by viewModel.networkPassword.collectAsState()
    val logs by viewModel.proxyLogs.collectAsState()
    
    // 🚀 الإعدادات الجديدة للتخفي والبروكسي
    val selectedDnsProvider by viewModel.selectedDnsProvider.collectAsState()
    val customDohUrl by viewModel.customDohUrl.collectAsState()
    val vlessNodeUri by viewModel.vlessNodeUri.collectAsState()
    val isSniFragmentationEnabled by viewModel.isSniFragmentationEnabled.collectAsState()
    
    val context = LocalContext.current
    var expandedDnsMenu by remember { mutableStateOf(false) }

    // التأثير الجانبي الذي يعمل مرة واحدة عند فتح الشاشة
    LaunchedEffect(Unit) {
        val status = shizukuManager.checkShizukuStatus()
        if (status == -1) {
            // التطبيق ينتظر Shizuku
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "WiFi Bridge Pro", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==========================================
        // 1. قسم بيانات الجلسة الحالية
        // ==========================================
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Session Details:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                // في هذا الإصدار (البروكسي)، الآي بي والبورت لا يزالان ثابتين للنظام
                Text("Gateway IP: $ip")
                Text("Proxy Port: $port")
                
                if (isActive && netName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("SSID: $netName", color = MaterialTheme.colorScheme.primary)
                    Text("Password: $netPass", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // ==========================================
        // 2. قسم إعدادات التخفي و كسر الحجب (Anti-DPI)
        // ==========================================
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("إعدادات كسر الحجب والتخفي:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))

                // اختيار مزود DNS
                ExposedDropdownMenuBox(
                    expanded = expandedDnsMenu && !isActive,
                    onExpandedChange = { if (!isActive) expandedDnsMenu = !expandedDnsMenu }
                ) {
                    OutlinedTextField(
                        value = selectedDnsProvider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("مزود الـ DNS المشفر") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !isActive
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDnsMenu && !isActive,
                        onDismissRequest = { expandedDnsMenu = false }
                    ) {
                        listOf("Cloudflare", "Google", "AliDNS", "Custom DoH", "بدون تشفير").forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider) },
                                onClick = {
                                    viewModel.selectedDnsProvider.value = provider
                                    expandedDnsMenu = false
                                }
                            )
                        }
                    }
                }
                
                // إظهار حقل Custom DoH فقط عند اختياره
                if (selectedDnsProvider == "Custom DoH") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDohUrl,
                        onValueChange = { viewModel.customDohUrl.value = it },
                        label = { Text("رابط الـ Worker (DoH URL)") },
                        enabled = !isActive,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // حقل عقدة VLESS (Xray-core) - للمستقبل
                OutlinedTextField(
                    value = vlessNodeUri,
                    onValueChange = { viewModel.vlessNodeUri.value = it },
                    label = { Text("عقدة VLESS (vless://...) - اختياري") },
                    enabled = !isActive,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                // مفتاح تفعيل تقطيع الـ SNI
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "تقطيع SNI (تجاوز فحص مزود الخدمة)")
                    Switch(
                        checked = isSniFragmentationEnabled,
                        onCheckedChange = { viewModel.isSniFragmentationEnabled.value = it },
                        enabled = !isActive
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==========================================
        // 3. أزرار التحكم
        // ==========================================
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

        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.generateCleanSessionParams() },
            enabled = !isActive,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Clean Session")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 4. السجلات الحية
        // ==========================================
        Text("Live Proxy Logs:", style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // تحديد ارتفاع ثابت حتى لا يملأ الشاشة وتستطيع التمرير للأسفل
                .background(Color.Black.copy(alpha = 0.05f))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (log.contains("خطأ") || log.contains("ERROR") || log.contains("❌")) Color.Red else Color.DarkGray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}
