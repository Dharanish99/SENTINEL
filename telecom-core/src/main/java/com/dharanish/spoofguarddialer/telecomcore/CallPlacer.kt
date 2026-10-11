package com.dharanish.spoofguarddialer.telecomcore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

data class CallingAccount(val handle: PhoneAccountHandle, val label: String)

object CallPlacer {
    fun accounts(context: Context): List<CallingAccount> = runCatching {
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom.callCapablePhoneAccounts.map { CallingAccount(it, telecom.getPhoneAccount(it)?.label?.toString() ?: "Phone account") }
    }.getOrDefault(emptyList())

    fun place(context: Context, number: String, account: PhoneAccountHandle? = null): Boolean {
        val normalized = DialString.normalize(number)
        if (!DialString.isCallable(normalized)) return fail(context, "Enter a phone number first")
        return placeUri(context, Uri.fromParts("tel", normalized, null), account).also { success ->
            if (success) context.getSharedPreferences("dialer", Context.MODE_PRIVATE).edit().putString("last_number", normalized).apply()
        }
    }

    fun voicemail(context: Context, account: PhoneAccountHandle? = null): Boolean =
        placeUri(context, Uri.fromParts("voicemail", "", null), account)

    private fun placeUri(context: Context, uri: Uri, account: PhoneAccountHandle?): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return fail(context, "Allow Phone permission in Settings to place calls")
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java)
                ?: return fail(context, "Calling is not available on this device")
            val emergency = uri.scheme == "tel" && runCatching {
                context.getSystemService(android.telephony.TelephonyManager::class.java).isEmergencyNumber(uri.schemeSpecificPart)
            }.getOrDefault(false)
            val extras = Bundle().apply {
                if (account != null && !emergency) putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            }
            telecom.placeCall(uri, extras)
            true
        } catch (_: SecurityException) {
            fail(context, "Phone permission is unavailable. Check your default phone app and permissions.")
        } catch (_: RuntimeException) {
            fail(context, "Couldn't start the call. Check your SIM and calling settings.")
        }
    }

    private fun fail(context: Context, message: String): Boolean {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return false
    }
}
