package com.dharanish.spoofguarddialer.util

import android.content.Intent
import com.dharanish.spoofguarddialer.telecomcore.DialString

object PhoneIntents {
    fun number(intent: Intent): String? {
        if (intent.action != Intent.ACTION_DIAL && intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return if (intent.action == Intent.ACTION_DIAL) "" else null
        if (!uri.scheme.equals("tel", ignoreCase = true)) return null
        val raw = uri.schemeSpecificPart.substringBefore('?')
        val extension = raw.substringAfter(";ext=", "").substringBefore(';')
        val number = raw.substringBefore(";ext=").substringBefore(";phone-context=")
        return DialString.normalize(number + if (extension.isNotBlank()) "," + extension else "")
    }
}
