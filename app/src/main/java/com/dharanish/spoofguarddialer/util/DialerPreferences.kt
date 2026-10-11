package com.dharanish.spoofguarddialer.util

import android.content.Context
import com.dharanish.spoofguarddialer.telecomcore.DialString

object DialerPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("dialer", Context.MODE_PRIVATE)
    fun haptics(context: Context) = prefs(context).getBoolean("haptics", true)
    fun setHaptics(context: Context, value: Boolean) { prefs(context).edit().putBoolean("haptics", value).apply() }
    fun tones(context: Context) = prefs(context).getBoolean("tones", false)
    fun setTones(context: Context, value: Boolean) { prefs(context).edit().putBoolean("tones", value).apply() }
    fun favorites(context: Context): Set<String> = prefs(context).getStringSet("favorites", emptySet()).orEmpty().toSet()
    fun toggleFavorite(context: Context, number: String) {
        val key = DialString.normalize(number)
        val updated = favorites(context).toMutableSet()
        if (!updated.add(key)) updated.remove(key)
        prefs(context).edit().putStringSet("favorites", updated).apply()
    }
    fun lastNumber(context: Context) = prefs(context).getString("last_number", "").orEmpty()
    fun speedDial(context: Context, key: String) = prefs(context).getString("speed_" + key, "").orEmpty()
    fun setSpeedDial(context: Context, key: String, number: String) { prefs(context).edit().putString("speed_" + key, DialString.normalize(number)).apply() }
}
