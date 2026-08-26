package com.dharanish.spoofguarddialer.telecomcore

import android.content.Context
import android.net.Uri
import android.telecom.TelecomManager

object CallPlacer {
    fun place(context: Context, number: String) {
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        val uri = Uri.fromParts("tel", number, null)
        telecomManager?.placeCall(uri, null)
    }
}