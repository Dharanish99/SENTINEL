package com.dharanish.spoofguarddialer.ui.contacts

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dharanish.spoofguarddialer.telecomcore.DialString
import com.dharanish.spoofguarddialer.util.DialerPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()
    private val _favorites: MutableStateFlow<Set<String>> = MutableStateFlow(DialerPreferences.favorites(application))
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
    var isLoading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private var job: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) = loadContacts(debounce = true) }

    fun toggleFavorite(number: String) {
        DialerPreferences.toggleFavorite(getApplication(), number)
        _favorites.value = DialerPreferences.favorites(getApplication())
    }

    fun loadContacts(debounce: Boolean = false) {
        job?.cancel()
        val app = getApplication<Application>()
        _favorites.value = DialerPreferences.favorites(app)
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            _contacts.value = emptyList(); isLoading = false; error = null; return
        }
        if (!observing) observing = runCatching {
            app.contentResolver.registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true, observer); true
        }.getOrDefault(false)
        job = viewModelScope.launch {
            if (debounce) delay(200)
            isLoading = true; error = null
            try { _contacts.value = withContext(Dispatchers.IO) { queryContacts() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _contacts.value = emptyList(); error = "Contacts couldn't be loaded. Check permission and try again." }
            finally { isLoading = false }
        }
    }

    private suspend fun queryContacts(): List<ContactEntry> {
        val app = getApplication<Application>()
        val projection = arrayOf("display_name", "data1", "contact_id", "lookup", "data2", "data3")
        val entries = mutableListOf<ContactEntry>()
        app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, "display_name COLLATE LOCALIZED ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: number
                val lookup = ContactsContract.Contacts.getLookupUri(cursor.getLong(2), cursor.getString(3))?.toString()
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(app.resources, cursor.getInt(4), cursor.getString(5)).toString()
                entries += ContactEntry(name, number, lookup, label)
            }
        }
        return entries.distinctBy { it.normalizedNumber }
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
