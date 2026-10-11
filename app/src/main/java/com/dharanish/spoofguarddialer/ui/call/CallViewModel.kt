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
import com.dharanish.spoofguarddialer.telecomcore.CallSelection
import com.dharanish.spoofguarddialer.telecomcore.CallSnapshot
import android.telecom.PhoneAccountHandle
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallViewModel(application: Application) : AndroidViewModel(application) {

    val snapshots = TelecomCallRegistry.snapshots
    val currentSnapshot: StateFlow<CallSnapshot?> = snapshots.map(CallSelection::primary)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CallSelection.primary(snapshots.value))
    val presentedSnapshot = combine(snapshots, TelecomCallRegistry.lastEndedCall, CallSelection::presentation)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CallSelection.presentation(snapshots.value, TelecomCallRegistry.lastEndedCall.value))

    private val currentCall: StateFlow<Call?> = currentSnapshot
        .map { TelecomCallRegistry.find(it?.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasCall: StateFlow<Boolean> = TelecomCallRegistry.calls
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TelecomCallRegistry.calls.value.isNotEmpty())

    val callState: StateFlow<Int?> = currentSnapshot.map { it?.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val phoneNumber: StateFlow<String?> = presentedSnapshot
        .map { it?.number }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val callerName: StateFlow<String?> = phoneNumber
        .flatMapLatest { number ->
            if (number.isNullOrBlank()) {
                flowOf<String?>(null)
            } else {
                flow { emit(null); emit(withContext(Dispatchers.IO) { ContactLookup.nameForNumber(getApplication(), number) }) }
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

    val audioRouting = SpoofGuardInCallService.routing
    val isMuted: StateFlow<Boolean> = audioRouting
        .map { it.muted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSpeakerOn: StateFlow<Boolean> = audioRouting
        .map { audio -> audio.routes.firstOrNull { it.id == audio.selectedId }?.type == CallAudioState.ROUTE_SPEAKER }
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
        act { currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    }

    fun reject() {
        act { currentCall.value?.reject(false, null) }
    }

    fun hangUp() {
        act { currentCall.value?.disconnect() }
    }

    fun setMuted(muted: Boolean) {
        SpoofGuardInCallService.requestMute(muted)
    }

    fun setSpeaker(enabled: Boolean) {
        SpoofGuardInCallService.requestSpeaker(enabled)
    }

    fun toggleHold() {
        val call = currentCall.value ?: return
        if (currentSnapshot.value?.can(Call.Details.CAPABILITY_HOLD) != true) return
        act { if (call.state == Call.STATE_HOLDING) call.unhold() else call.hold() }
    }

    private var toneCall: Call? = null
    fun playDtmf(digit: Char) {
        stopDtmf()
        if (callState.value == Call.STATE_ACTIVE) act {
            toneCall = currentCall.value
            toneCall?.playDtmfTone(digit)
        }
    }

    fun stopDtmf() {
        act { toneCall?.stopDtmfTone() }
        toneCall = null
    }

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()
    fun clearError() { _actionError.value = null; SpoofGuardInCallService.clearAudioError() }
    private fun act(action: () -> Unit) {
        try { action() } catch (_: RuntimeException) { _actionError.value = "This call action is unavailable. Please try again." }
    }
    fun selectAudio(id: String) = SpoofGuardInCallService.requestRoute(id)
    fun swap() {
        snapshots.value.firstOrNull { it.parentId == null && it.state == Call.STATE_HOLDING }
            ?.let { held -> act { TelecomCallRegistry.find(held.id)?.unhold() } }
    }
    fun mergeCalls() {
        val snapshot = currentSnapshot.value ?: return
        val call = TelecomCallRegistry.find(snapshot.id) ?: return
        val other = snapshot.conferenceableIds.firstNotNullOfOrNull(TelecomCallRegistry::find)
        act {
            if (other != null) call.conference(other)
            else if (snapshot.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) call.mergeConference()
        }
    }
    fun disconnectParticipant(id: String) = act {
        snapshots.value.firstOrNull { it.id == id && it.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE) }
            ?.let { TelecomCallRegistry.find(id)?.disconnect() }
    }
    fun splitParticipant(id: String) = act {
        snapshots.value.firstOrNull { it.id == id && it.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE) }
            ?.let { TelecomCallRegistry.find(id)?.splitFromConference() }
    }
    fun selectAccount(handle: PhoneAccountHandle) = act { currentCall.value?.phoneAccountSelected(handle, false) }
    fun rejectWithMessage(message: String) = act { currentCall.value?.reject(true, message) }
    @android.annotation.SuppressLint("MissingPermission") // Telecom permits the default dialer; MODIFY_PHONE_STATE is only the alternative privileged path.
    fun silence() = act {
        val app = getApplication<Application>()
        val telecom = app.getSystemService(android.telecom.TelecomManager::class.java)
        if (telecom.defaultDialerPackage == app.packageName) telecom.silenceRinger()
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
