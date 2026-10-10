package com.abdullah.wifibridge.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class MainViewModel : ViewModel() {
    
    private val _currentIp = MutableStateFlow("10.0.0.1")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(8080)
    val currentPort: StateFlow<Int> = _currentPort

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive

    fun generateCleanSessionParams() {
        // Generate random IP in 10.x.y.1 range
        val x = Random.nextInt(1, 255)
        val y = Random.nextInt(1, 255)
        _currentIp.value = "10.$x.$y.1"
        
        // Generate random port
        _currentPort.value = Random.nextInt(10000, 60000)
    }

    fun toggleSession() {
        _isSessionActive.value = !_isSessionActive.value
    }
}
