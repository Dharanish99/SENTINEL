package com.dharanish.spoofguarddialer.telecomcore

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CallBridgeController"

// Exotel requires the registered mobile number, followed by '#', sent as DTMF
// immediately after it answers — this is how Exotel identifies which account
// to stream audio for. Fixed for this integration, not something that varies
// per call.
private const val EXOTEL_DTMF_IDENTIFIER = "9944626918#"

class CallBridgeController {

    sealed interface BridgeState {
        data object Idle : BridgeState
        data object Securing : BridgeState
        data object Merged : BridgeState
        data class Failed(val reason: String) : BridgeState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    /** Resets to Idle — call whenever the call this controller was bridging ends. */
    fun reset() {
        _state.value = BridgeState.Idle
    }

    /**
     * Runs hold -> dial -> wait -> DTMF identify -> merge for the current primary
     * call. No cancel once started, by design (prototype decision).
     */
    fun startBridge(context: Context, bridgeNumber: String) {
        if (_state.value !is BridgeState.Idle) {
            Log.d(TAG, "startBridge called while state=${_state.value} — ignoring")
            return
        }

        val primaryCall = TelecomCallRegistry.calls.value.firstOrNull()
        if (primaryCall == null) {
            _state.value = BridgeState.Failed("No active call to bridge from")
            return
        }

        val phoneAccountHandle = primaryCall.details.accountHandle
        if (phoneAccountHandle == null) {
            _state.value = BridgeState.Failed("Primary call has no PhoneAccountHandle")
            return
        }

        val canMerge = (primaryCall.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        if (!canMerge) {
            _state.value = BridgeState.Failed("Carrier/call doesn't support merging")
            return
        }

        _state.value = BridgeState.Securing

        scope.launch {
            Log.d(TAG, "Holding primary call")
            primaryCall.hold()

            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }
            val uri = Uri.fromParts("tel", bridgeNumber, null)

            Log.d(TAG, "Placing bridge call to $bridgeNumber")
            telecomManager?.placeCall(uri, extras)

            val bridgeCall = withTimeoutOrNull(15_000) {
                TelecomCallRegistry.calls
                    .first { calls -> calls.any { it.details.handle?.schemeSpecificPart == bridgeNumber } }
                    .first { it.details.handle?.schemeSpecificPart == bridgeNumber }
            }

            if (bridgeCall == null) {
                Log.d(TAG, "Bridge call never appeared — placeCall() may have failed")
                primaryCall.unhold()
                _state.value = BridgeState.Failed("Bridge call never appeared")
                return@launch
            }

            Log.d(TAG, "Bridge call placed, waiting for it to connect")
            val becameActive = withTimeoutOrNull(30_000) { waitForActive(bridgeCall) }

            if (becameActive != true) {
                Log.d(TAG, "Bridge call never became active — hanging it up")
                bridgeCall.disconnect()
                primaryCall.unhold()
                _state.value = BridgeState.Failed("Bridge call never connected")
                return@launch
            }

            Log.d(TAG, "Bridge call active — sending Exotel DTMF identifier")
            sendDtmfSequence(bridgeCall, EXOTEL_DTMF_IDENTIFIER)

            // Give Exotel a moment to process the identifier and switch into
            // streaming mode before bringing the caller's audio into the mix.
            // 800ms is a guess, not from Exotel documentation — tune this based
            // on real testing if the stream doesn't reliably pick up audio.
            delay(800)

            Log.d(TAG, "Merging with primary call")
            primaryCall.conference(bridgeCall)

            val merged = withTimeoutOrNull(8_000) { waitForParent(primaryCall) }
            if (merged != true) {
                Log.d(TAG, "Merge did not complete in time — cleaning up")
                bridgeCall.disconnect()
                primaryCall.unhold()
                _state.value = BridgeState.Failed("Merge did not complete")
                return@launch
            }

            Log.d(TAG, "Merge confirmed")
            _state.value = BridgeState.Merged
        }
    }

    private suspend fun sendDtmfSequence(call: Call, digits: String) {
        for (digit in digits) {
            Log.d(TAG, "Sending DTMF digit: $digit")
            call.playDtmfTone(digit)
            delay(150)
            call.stopDtmfTone()
            delay(150)
        }
    }

    private suspend fun waitForActive(call: Call): Boolean {
        if (call.state == Call.STATE_ACTIVE) return true
        val deferred = CompletableDeferred<Boolean>()
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                when (state) {
                    Call.STATE_ACTIVE -> if (!deferred.isCompleted) deferred.complete(true)
                    Call.STATE_DISCONNECTED -> if (!deferred.isCompleted) deferred.complete(false)
                }
            }
        }
        call.registerCallback(callback)
        val result = deferred.await()
        call.unregisterCallback(callback)
        return result
    }

    // There's no dedicated "parent changed" callback in the public Call.Callback
    // API, so this polls — bounded by the caller's withTimeoutOrNull.
    private suspend fun waitForParent(call: Call): Boolean {
        while (call.parent == null) {
            delay(300)
        }
        return true
    }
}