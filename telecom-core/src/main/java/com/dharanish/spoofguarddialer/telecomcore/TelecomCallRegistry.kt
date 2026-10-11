package com.dharanish.spoofguarddialer.telecomcore

import android.content.Context
import android.telecom.Call
import android.telecom.TelecomManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelecomCallRegistry {
    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()
    private val _snapshots = MutableStateFlow<List<CallSnapshot>>(emptyList())
    val snapshots = _snapshots.asStateFlow()
    private val ids = mutableMapOf<Call, String>()
    private val _postDialWait = MutableStateFlow<Pair<String, String>?>(null)
    val postDialWait = _postDialWait.asStateFlow()
    private val _lastDisconnect = MutableStateFlow("Call ended")
    val lastDisconnect = _lastDisconnect.asStateFlow()
    private val _lastEndedCall = MutableStateFlow<CallSnapshot?>(null)
    val lastEndedCall = _lastEndedCall.asStateFlow()

    fun id(call: Call): String = ids.getOrPut(call) { java.util.UUID.randomUUID().toString() }
    fun find(id: String?): Call? = ids.entries.firstOrNull { it.value == id }?.key

    fun onCallAdded(call: Call) {
        id(call)
        if (call !in _calls.value) _calls.value = _calls.value + call
    }

    fun onCallRemoved(call: Call) {
        _snapshots.value.firstOrNull { it.id == ids[call] && it.parentId == null }?.let {
            _lastEndedCall.value = it.copy(state = Call.STATE_DISCONNECTED)
        }
        _lastDisconnect.value = call.details?.disconnectCause?.label?.toString()?.takeIf { it.isNotBlank() }
            ?: "Call ended"
        if (_postDialWait.value?.first == ids[call]) _postDialWait.value = null
        _calls.value = _calls.value - call
        ids.remove(call)
    }

    fun onPostDialWait(call: Call, remaining: String) { _postDialWait.value = id(call) to remaining }

    fun continuePostDial(proceed: Boolean) {
        find(_postDialWait.value?.first)?.postDialContinue(proceed)
        _postDialWait.value = null
    }

    fun refresh(context: Context) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        _snapshots.value = _calls.value.map { call ->
            val details = call.details
            val accountLabel = runCatching { telecom.getPhoneAccount(details?.accountHandle)?.label?.toString() }.getOrNull()
            CallSnapshot(
                id = id(call), state = call.state,
                number = details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() && details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED },
                displayName = details?.callerDisplayName?.takeIf { it.isNotBlank() && details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED },
                capabilities = details?.callCapabilities ?: 0,
                parentId = call.parent?.let(::id), childIds = call.children.map(::id),
                conferenceableIds = call.conferenceableCalls.map(::id),
                connectedAtMillis = details?.connectTimeMillis ?: 0,
                isConference = details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
                accountLabel = accountLabel,
                presentation = details?.handlePresentation ?: TelecomManager.PRESENTATION_UNKNOWN,
                cannedResponses = call.cannedTextResponses?.toList().orEmpty(),
            )
        }
    }

    fun clear() {
        _calls.value = emptyList()
        _snapshots.value = emptyList()
        _postDialWait.value = null
        ids.clear()
    }
}
