package com.dharanish.spoofguarddialer.telecomcore

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelecomCallRegistry {
    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    fun onCallAdded(call: Call) {
        _calls.value = _calls.value + call
    }

    fun onCallRemoved(call: Call) {
        _calls.value = _calls.value - call
    }
}