package com.dharanish.spoofguarddialer.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight, app-wide security preference store. A plain SharedPreferences
 * wrapper (not a ViewModel) so it's trivially readable from anywhere —
 * Settings, the incoming-call screen, and eventually CallBridgeController —
 * without needing a shared ViewModelStoreOwner.
 *
 * Note: "Always Protect" and "Auto-secure unknown callers" are deliberately
 * separate flags per product decision, not one controlling the other.
 */
object SecuritySettings {
    private const val PREFS_NAME = "sentinal_security_prefs"
    private const val KEY_ALWAYS_PROTECT = "always_protect"
    private const val KEY_AUTO_SECURE_UNKNOWN = "auto_secure_unknown_callers"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAlwaysProtectEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALWAYS_PROTECT, true)

    fun setAlwaysProtectEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALWAYS_PROTECT, enabled).apply()
    }

    fun isAutoSecureUnknownCallersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SECURE_UNKNOWN, false)

    fun setAutoSecureUnknownCallersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SECURE_UNKNOWN, enabled).apply()
    }
}