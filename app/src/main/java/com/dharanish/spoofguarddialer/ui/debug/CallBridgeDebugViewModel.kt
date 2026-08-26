package com.dharanish.spoofguarddialer.ui.debug

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dharanish.spoofguarddialer.telecomcore.CallBridgeController
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import kotlinx.coroutines.launch

class CallBridgeDebugViewModel(application: Application) : AndroidViewModel(application) {

    private val bridgeController = CallBridgeController()

    var activeCallCount by mutableStateOf(0)
        private set
    var statusMessage by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            TelecomCallRegistry.calls.collect { calls ->
                activeCallCount = calls.size
            }
        }
        viewModelScope.launch {
            bridgeController.state.collect { state ->
                statusMessage = when (state) {
                    is CallBridgeController.BridgeState.Idle -> ""
                    is CallBridgeController.BridgeState.Securing -> "Securing… (see Logcat tag CallBridgeController for step-by-step progress)"
                    is CallBridgeController.BridgeState.Merged -> "Merged — bridge connected successfully"
                    is CallBridgeController.BridgeState.Failed -> "Failed: ${state.reason}"
                }
            }
        }
    }

    fun startBridge(testNumber: String) {
        bridgeController.startBridge(getApplication(), testNumber)
    }
}