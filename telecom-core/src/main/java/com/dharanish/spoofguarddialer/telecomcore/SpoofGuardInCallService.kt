package com.dharanish.spoofguarddialer.telecomcore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SpoofGuardInCallService"
private const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
private const val INCOMING_CALL_NOTIFICATION_ID = 2001

class SpoofGuardInCallService : InCallService() {

    companion object {
        private var instance: SpoofGuardInCallService? = null

        private val _audioState = MutableStateFlow<CallAudioState?>(null)
        val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

        fun requestMute(muted: Boolean) {
            instance?.setMuted(muted)
        }

        fun requestSpeaker(enabled: Boolean) {
            val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            instance?.setAudioRoute(route)
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: $state")
            if (state != Call.STATE_RINGING) {
                cancelIncomingCallNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createIncomingCallChannel()
    }

    override fun onDestroy() {
        instance = null
        _audioState.value = null
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        _audioState.value = audioState
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.handle}")
        call.registerCallback(callCallback)
        TelecomCallRegistry.onCallAdded(call)

        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        call.unregisterCallback(callCallback)
        TelecomCallRegistry.onCallRemoved(call)
        cancelIncomingCallNotification()
    }

    private fun createIncomingCallChannel() {
        val channel = NotificationChannel(
            INCOMING_CALL_CHANNEL_ID,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen incoming call alerts"
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showIncomingCallNotification(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (launchIntent == null) {
            Log.e(TAG, "No launch intent found for $packageName — cannot show full-screen call UI")
            return
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setContentTitle("Incoming call")
            .setContentText(number)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    private fun cancelIncomingCallNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}