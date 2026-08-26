package com.dharanish.spoofguarddialer.ui.calllog

import android.app.Application
import android.provider.CallLog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val _entries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val entries: StateFlow<List<CallLogEntry>> = _entries.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    fun loadCallLog() {
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { queryCallLog() }
            _entries.value = result
            isLoading = false
        }
    }

    private fun queryCallLog(): List<CallLogEntry> {
        val resolver = getApplication<Application>().contentResolver
        val entries = mutableListOf<CallLogEntry>()

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (number.isNullOrBlank()) continue

                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val typeInt = if (typeIndex >= 0) cursor.getInt(typeIndex) else -1
                val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else 0L
                val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    else -> CallType.OTHER
                }

                entries.add(CallLogEntry(name, number, type, date, duration))
            }
        }

        return entries.take(50)
    }
}