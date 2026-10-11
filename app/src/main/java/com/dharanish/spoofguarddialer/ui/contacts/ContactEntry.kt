package com.dharanish.spoofguarddialer.ui.contacts

import androidx.compose.runtime.Immutable
import com.dharanish.spoofguarddialer.telecomcore.DialString

@Immutable
data class ContactEntry(
    val name: String,
    val number: String,
    val lookupUri: String? = null,
    val label: String = "Phone",
) {
    val normalizedNumber = DialString.normalize(number)
    val numberSearchKey = DialString.searchKey(number)
    val nameSearchKey = DialString.searchKey(name)
    val initial = name.take(1).uppercase()
}
