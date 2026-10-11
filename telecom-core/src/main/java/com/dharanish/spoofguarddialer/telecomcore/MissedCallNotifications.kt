package com.dharanish.spoofguarddialer.telecomcore

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat

/** Telecom owns the unread count and delegates notification delivery to this protected receiver. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (telecom.defaultDialerPackage != context.packageName) return
        MissedCallNotifications.show(context, intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0),
            intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER))
    }
}

object MissedCallNotifications {
    const val ACTION_CLEAR = "com.dharanish.spoofguarddialer.CLEAR_MISSED_CALLS"
    private const val CHANNEL = "dialer_missed"
    private const val ID = 3000

    fun show(context: Context, count: Int, number: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (count <= 0) { manager.cancel(ID); return }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT))
        val history = Intent().setClassName(context.packageName, context.packageName + ".MainActivity")
            .putExtra("open_recents", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = PendingIntent.getActivity(context, 42, history, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val clear = PendingIntent.getBroadcast(context, 43, Intent(context, CallActionReceiver::class.java).setAction(ACTION_CLEAR), PendingIntent.FLAG_IMMUTABLE)
        val validNumber = number?.takeIf { count == 1 && it.isNotBlank() && DialString.isCallable(it) }
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(if (count == 1) "Missed call" else "$count missed calls")
            .setContentText(if (count == 1) validNumber ?: "Private or unknown caller" else "Tap to view call history")
            .setNumber(count).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(content).setDeleteIntent(clear)
        if (validNumber != null) {
            val dial = Intent(history).setAction(Intent.ACTION_DIAL).setData(Uri.fromParts("tel", validNumber, null))
                .apply { removeExtra("open_recents") }
            val callback = PendingIntent.getActivity(context, 44, dial, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.sym_action_call, "Call back", callback)
        }
        runCatching { manager.notify(ID, builder.build()) }
    }

    @SuppressLint("MissingPermission") // Android authorizes the default dialer as an alternative to MODIFY_PHONE_STATE.
    fun clear(context: Context) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        if (telecom.defaultDialerPackage == context.packageName) runCatching { telecom.cancelMissedCallsNotification() }
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
