package com.dharanish.spoofguarddialer.ui.call

import android.app.Application
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dharanish.spoofguarddialer.telecomcore.CallBridgeController
import com.dharanish.spoofguarddialer.telecomcore.SpoofGuardInCallService
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import com.dharanish.spoofguarddialer.util.ContactLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val currentCall: StateFlow<Call?> = TelecomCallRegistry.calls
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasCall: StateFlow<Boolean> = TelecomCallRegistry.calls
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val callState: StateFlow<Int?> = currentCall
        .flatMapLatest { call -> call?.stateFlow() ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val phoneNumber: StateFlow<String?> = currentCall
        .map { it?.details?.handle?.schemeSpecificPart }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val callerName: StateFlow<String?> = phoneNumber
        .flatMapLatest { number ->
            if (number.isNullOrBlank()) {
                flowOf<String?>(null)
            } else {
                flow { emit(withContext(Dispatchers.IO) { ContactLookup.nameForNumber(getApplication(), number) }) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val supportsMerge: StateFlow<Boolean> = currentCall
        .map { call ->
            val caps = call?.details?.callCapabilities ?: 0
            (caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isOnHold: StateFlow<Boolean> = callState
        .map { it == Call.STATE_HOLDING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isMuted: StateFlow<Boolean> = SpoofGuardInCallService.audioState
        .map { it?.isMuted ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSpeakerOn: StateFlow<Boolean> = SpoofGuardInCallService.audioState
        .map { it?.route == CallAudioState.ROUTE_SPEAKER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _armSecureOnConnect = MutableStateFlow(false)
    val armSecureOnConnect: StateFlow<Boolean> = _armSecureOnConnect.asStateFlow()

    fun setArmSecureOnConnect(armed: Boolean) {
        _armSecureOnConnect.value = armed
    }

    // Owns the real hold -> dial -> DTMF identify -> merge sequence.
    private val bridgeController = CallBridgeController()
    val bridgeState: StateFlow<CallBridgeController.BridgeState> = bridgeController.state

    init {
        viewModelScope.launch {
            callState.collect { state ->
                if (state == Call.STATE_ACTIVE &&
                    _armSecureOnConnect.value &&
                    bridgeController.state.value is CallBridgeController.BridgeState.Idle
                ) {
                    startSecureBridge()
                }
            }
        }
        viewModelScope.launch {
            hasCall.collect { active ->
                if (!active) {
                    bridgeController.reset()
                    _armSecureOnConnect.value = false
                }
            }
        }
    }

    fun startSecureBridge() {
        bridgeController.startBridge(getApplication(), BRIDGE_NUMBER)
    }

    fun answer() {
        currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        currentCall.value?.reject(false, null)
    }

    fun hangUp() {
        currentCall.value?.disconnect()
    }

    fun setMuted(muted: Boolean) {
        SpoofGuardInCallService.requestMute(muted)
    }

    fun setSpeaker(enabled: Boolean) {
        SpoofGuardInCallService.requestSpeaker(enabled)
    }

    fun toggleHold() {
        val call = currentCall.value ?: return
        if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun playDtmf(digit: Char) {
        currentCall.value?.playDtmfTone(digit)
    }

    fun stopDtmf() {
        currentCall.value?.stopDtmfTone()
    }

    companion object {
        // The Exotel-provisioned bridge number. CallBridgeController sends the
        // registered-number DTMF identifier automatically right after this
        // number answers.
        private const val BRIDGE_NUMBER = "9513886363"
    }
}

private fun Call.stateFlow(): Flow<Int> = callbackFlow {
    trySend(state)
    val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            trySend(state)
        }
    }
    registerCallback(callback)
    awaitClose { unregisterCallback(callback) }
}