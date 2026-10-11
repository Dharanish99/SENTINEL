package com.dharanish.spoofguarddialer.ui.calllog

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val _entries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    var isLoading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var observing = false
    private var job: Job? = null
    private var limit = 100
    private var search = ""
    private var missedOnly = false
    var hasMore by mutableStateOf(false); private set
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) = loadCallLog() }

    fun loadMore() { limit += 100; loadCallLog() }
    fun filter(query: String, missed: Boolean) {
        if (search == query && missedOnly == missed) return
        search = query; missedOnly = missed; limit = 100; loadCallLog()
    }
    fun loadCallLog() {
        job?.cancel()
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            _entries.value = emptyList(); isLoading = false; error = null; return
        }
        if (!observing) observing = runCatching {
            app.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer); true
        }.getOrDefault(false)
        job = viewModelScope.launch {
            isLoading = true; error = null
            try {
                val result = withContext(Dispatchers.IO) { queryCallLog(limit + 1) }
                hasMore = result.size > limit; _entries.value = result.take(limit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _entries.value = emptyList(); error = "Call history couldn't be loaded. Check permission and try again." }
            finally { isLoading = false }
        }
    }

    fun delete(ids: List<Long>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    // Delete only the entries the user actually confirmed, never unseen/new calls.
                    ids.forEach { resolver.delete(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, it), null, null) }
                }
                loadCallLog()
            } catch (_: SecurityException) { error = "Allow call-log access to delete history" }
            catch (_: Exception) { error = "Couldn't delete these calls. Try again." }
        }
    }

    private fun queryCallLog(max: Int): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        val columns = arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls._ID, CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.PHONE_ACCOUNT_ID)
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (search.isNotBlank()) {
            conditions += "(" + CallLog.Calls.NUMBER + " LIKE ? OR " + CallLog.Calls.CACHED_NAME + " LIKE ?)"
            args += "%" + search + "%"; args += "%" + search + "%"
        }
        if (missedOnly) { conditions += CallLog.Calls.TYPE + " = ?"; args += CallLog.Calls.MISSED_TYPE.toString() }
        getApplication<Application>().contentResolver.query(CallLog.Calls.CONTENT_URI, columns, conditions.joinToString(" AND ").ifBlank { null }, args.toTypedArray().takeIf { it.isNotEmpty() }, CallLog.Calls.DATE + " DESC")?.use { cursor ->
            while (entries.size < max && cursor.moveToNext()) {
                val type = when (cursor.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING; CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE -> CallType.MISSED; CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                    else -> CallType.OTHER
                }
                val presentation = cursor.getInt(6)
                val number = if (presentation == CallLog.Calls.PRESENTATION_ALLOWED) cursor.getString(1).orEmpty() else ""
                entries += CallLogEntry(cursor.getString(0)?.takeIf { it.isNotBlank() && presentation == CallLog.Calls.PRESENTATION_ALLOWED }, number, type, cursor.getLong(3), cursor.getLong(4), cursor.getLong(5), presentation, cursor.getString(7))
            }
        }
        return entries
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
