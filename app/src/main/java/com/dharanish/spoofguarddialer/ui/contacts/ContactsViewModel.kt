package com.dharanish.spoofguarddialer.ui.contacts

import android.app.Application
import android.provider.ContactsContract
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

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    fun loadContacts() {
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { queryContacts() }
            _contacts.value = result
            isLoading = false
        }
    }

    private fun queryContacts(): List<ContactEntry> {
        val resolver = getApplication<Application>().contentResolver
        val entries = mutableListOf<ContactEntry>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val number = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (!number.isNullOrBlank()) {
                    entries.add(ContactEntry(name ?: "Unknown", number))
                }
            }
        }

        return entries.distinctBy { it.number }
    }
}