package com.dharanish.spoofguarddialer.telecomcore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRoute(val id: String, val label: String, val type: Int)
data class DialerAudioState(
    val routes: List<AudioRoute> = emptyList(),
    val selectedId: String? = null,
    val muted: Boolean = false,
    val pendingId: String? = null,
    val error: String? = null,
)

class SpoofGuardInCallService : InCallService() {
    companion object {
        const val ACTION_ANSWER = "com.dharanish.spoofguarddialer.ANSWER"
        const val ACTION_DECLINE = "com.dharanish.spoofguarddialer.DECLINE"
        const val ACTION_HANG_UP = "com.dharanish.spoofguarddialer.HANG_UP"
        const val EXTRA_CALL_ID = "call_id"
        private const val INCOMING = 2001
        private const val ONGOING = 2002
        private const val INCOMING_CHANNEL = "dialer_incoming_v2"
        private const val ONGOING_CHANNEL = "dialer_ongoing"
        private const val MISSED_CHANNEL = "dialer_missed"
        private var instance: SpoofGuardInCallService? = null
        private val _audioState = MutableStateFlow<CallAudioState?>(null)
        val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()
        private val _routing = MutableStateFlow(DialerAudioState())
        val routing = _routing.asStateFlow()
        private val _canAddCall = MutableStateFlow(false)
        val canAddAnotherCall = _canAddCall.asStateFlow()

        fun requestMute(muted: Boolean) { instance?.setMuted(muted) }
        fun requestSpeaker(enabled: Boolean) {
            val route = _routing.value.routes.firstOrNull {
                if (enabled) it.type == CallAudioState.ROUTE_SPEAKER
                else it.type == CallAudioState.ROUTE_WIRED_HEADSET || it.type == CallAudioState.ROUTE_EARPIECE
            }
            route?.let { requestRoute(it.id) }
        }
        fun requestRoute(id: String) { instance?.changeRoute(id) }
        fun clearAudioError() { _routing.value = _routing.value.copy(error = null) }

        fun callIntent(context: Context): Intent = Intent().setClassName(context.packageName, context.packageName + ".InCallActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        fun openCallUi(context: Context) { runCatching { context.startActivity(callIntent(context)) } }
    }

    private var endpoints: List<CallEndpoint> = emptyList()
    private lateinit var proximity: CallProximityController
    private var currentEndpoint: CallEndpoint? = null
    private var currentAudioRoute: Int? = null
    private val routeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var routeRequest = 0L
    private var foregroundCall = false
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        instance = this
        manager.createNotificationChannels(listOf(
            NotificationChannel(INCOMING_CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Answer or decline incoming calls"; setSound(null, null)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
            NotificationChannel(ONGOING_CHANNEL, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) },
            NotificationChannel(MISSED_CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT),
        ))
        proximity = CallProximityController(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = refresh()
            override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
            override fun onParentChanged(call: Call, parent: Call?) = refresh()
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = refresh()
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = refresh()
            override fun onCannedTextResponsesLoaded(call: Call, cannedTextResponses: MutableList<String>) = refresh()
            override fun onPostDialWait(call: Call, remainingPostDialSequence: String) {
                TelecomCallRegistry.onPostDialWait(call, remainingPostDialSequence)
                openCallUi(this@SpoofGuardInCallService)
            }
        }
        callbacks[call] = callback
        call.registerCallback(callback)
        TelecomCallRegistry.onCallAdded(call)
        refresh()
        if (call.state != Call.STATE_RINGING && call.state != Call.STATE_SIMULATED_RINGING && call.parent == null) openCallUi(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callbacks.remove(call)?.let(call::unregisterCallback)
        TelecomCallRegistry.onCallRemoved(call)
        refresh()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        startActivity(callIntent(this).putExtra("show_dialpad", showDialpad))
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        _audioState.value = audioState
        currentAudioRoute = audioState.route
        if (Build.VERSION.SDK_INT < 34 || endpoints.isEmpty()) {
            val routes = mutableListOf<AudioRoute>()
            listOf(CallAudioState.ROUTE_EARPIECE to "Phone", CallAudioState.ROUTE_SPEAKER to "Speaker",
                CallAudioState.ROUTE_WIRED_HEADSET to "Wired headset").forEach { (type, name) ->
                if (audioState.supportedRouteMask and type != 0) routes += AudioRoute(type.toString(), name, type)
            }
            if (audioState.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                routes += AudioRoute(CallAudioState.ROUTE_BLUETOOTH.toString(), "Bluetooth", CallAudioState.ROUTE_BLUETOOTH)
            }
            _routing.value = DialerAudioState(routes, audioState.route.toString(), audioState.isMuted)
        } else _routing.value = _routing.value.copy(muted = audioState.isMuted)
        reconcileSelectedRoute()
        updateProximity()
    }

    @RequiresApi(34)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        endpoints = availableEndpoints.toList()
        _routing.value = _routing.value.copy(routes = endpoints.map { AudioRoute(it.identifier.toString(), it.endpointName.toString(), endpointType(it.endpointType)) })
        reconcileSelectedRoute()
        if (_routing.value.pendingId != null && _routing.value.routes.none { it.id == _routing.value.pendingId }) {
            _routing.value = _routing.value.copy(pendingId = null, error = "Audio device disconnected")
        }
        updateProximity()
    }

    @RequiresApi(34)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        currentEndpoint = callEndpoint
        currentAudioRoute = endpointType(callEndpoint.endpointType)
        _routing.value = _routing.value.copy(selectedId = callEndpoint.identifier.toString(), pendingId = null, error = null)
        updateProximity()
    }

    override fun onMuteStateChanged(isMuted: Boolean) { _routing.value = _routing.value.copy(muted = isMuted) }
    override fun onCanAddCallChanged(canAddCall: Boolean) { _canAddCall.value = canAddCall }

    private fun reconcileSelectedRoute() {
        val confirmedId = if (Build.VERSION.SDK_INT >= 34) currentEndpoint?.identifier?.toString() else null
        _routing.value = _routing.value.copy(selectedId = AudioRouteSelection.resolve(_routing.value.routes, confirmedId, currentAudioRoute))
    }

    @RequiresApi(34)
    private fun endpointType(type: Int) = when (type) {
        CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
        CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
        CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        else -> 0
    }

    @Suppress("DEPRECATION")
    private fun changeRoute(id: String) {
        if (_routing.value.routes.none { it.id == id }) return
        if (_routing.value.selectedId == id) return
        val request = ++routeRequest
        _routing.value = _routing.value.copy(pendingId = id, error = null)
        routeHandler.postDelayed({
            if (request == routeRequest && _routing.value.pendingId == id) {
                _routing.value = _routing.value.copy(pendingId = null, error = "Audio switch timed out. Please try again.")
            }
        }, 8_000)
        try {
            if (Build.VERSION.SDK_INT >= 34 && endpoints.isNotEmpty()) {
                val endpoint = endpoints.firstOrNull { it.identifier.toString() == id } ?: throw IllegalStateException()
                requestCallEndpointChange(endpoint, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) { /* Confirm selection only in onCallEndpointChanged. */ }
                    override fun onError(error: CallEndpointException) {
                        if (request == routeRequest && _routing.value.pendingId == id) {
                            _routing.value = _routing.value.copy(pendingId = null, error = "Couldn't switch audio. Try another device.")
                        }
                    }
                })
            } else id.toIntOrNull()?.let(::setAudioRoute)
        } catch (_: RuntimeException) {
            _routing.value = _routing.value.copy(pendingId = null, error = "Audio device is no longer available")
        }
    }

    private fun refresh() {
        _canAddCall.value = canAddCall()
        TelecomCallRegistry.refresh(this)
        updateNotifications()
        updateProximity()
    }

    private fun updateProximity() {
        // The confirmed route is independent of endpoint-list callback ordering.
        proximity.update(ProximityPolicy.enabled(TelecomCallRegistry.snapshots.value.map { it.state }, currentAudioRoute))
    }

    private fun activityAction(call: CallSnapshot, action: String? = null): PendingIntent {
        val intent = callIntent(this).setAction(action).setData(Uri.parse("spoofguard:call/" + call.id + "/" + action))
            .putExtra(EXTRA_CALL_ID, call.id)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun receiverAction(call: CallSnapshot, action: String): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("spoofguard:call/" + call.id + "/" + action)).putExtra(EXTRA_CALL_ID, call.id)
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun updateNotifications() {
        val snapshots = TelecomCallRegistry.snapshots.value
        val ringing = snapshots.firstOrNull { it.state == Call.STATE_RINGING || it.state == Call.STATE_SIMULATED_RINGING }
        val ongoing = CallSelection.primary(snapshots.filter { it != ringing })
        if (ringing == null) manager.cancel(INCOMING) else notifyCall(ringing, true)
        if (ongoing == null) {
            if (foregroundCall) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundCall = false }
            manager.cancel(ONGOING)
        } else notifyCall(ongoing, false)
    }

    private fun notifyCall(call: CallSnapshot, incoming: Boolean) {
        val person = Person.Builder().setName(call.notificationName()).setImportant(true).build()
        val style = if (incoming) NotificationCompat.CallStyle.forIncomingCall(person,
            receiverAction(call, ACTION_DECLINE), activityAction(call, ACTION_ANSWER))
        else NotificationCompat.CallStyle.forOngoingCall(person, receiverAction(call, ACTION_HANG_UP))
        val publicVersion = NotificationCompat.Builder(this, if (incoming) INCOMING_CHANNEL else ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle(if (incoming) "Incoming call" else "Call in progress").build()
        val builder = NotificationCompat.Builder(this, if (incoming) INCOMING_CHANNEL else ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming).setStyle(style)
            .setContentIntent(activityAction(call)).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion).setContentText(call.accountLabel)
        if (incoming) builder.setFullScreenIntent(activityAction(call), true).setPriority(NotificationCompat.PRIORITY_MAX)
        else if (call.connectedAtMillis > 0) builder.setWhen(call.connectedAtMillis).setUsesChronometer(true)
        runCatching {
            if (incoming) manager.notify(INCOMING, builder.build())
            else {
                // Ongoing CallStyle notifications must be associated with foreground work.
                startForeground(ONGOING, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                foregroundCall = true
            }
        }.onFailure {
            android.util.Log.w("DialerNotifications", "Could not show call notification", it)
            if (!incoming) showFallbackCallNotification(call)
        }
    }

    private fun showFallbackCallNotification(call: CallSnapshot) {
        val fallback = NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle(call.notificationName())
            .setContentText("Call in progress").setContentIntent(activityAction(call))
            .setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.sym_call_missed, "End call", receiverAction(call, ACTION_HANG_UP))
        if (call.connectedAtMillis > 0) fallback.setWhen(call.connectedAtMillis).setUsesChronometer(true)
        runCatching { manager.notify(ONGOING, fallback.build()) }
    }

    override fun onDestroy() {
        if (foregroundCall) { stopForeground(STOP_FOREGROUND_REMOVE); foregroundCall = false }
        routeHandler.removeCallbacksAndMessages(null)
        callbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callbacks.clear()
        proximity.close()
        manager.cancel(INCOMING); manager.cancel(ONGOING)
        TelecomCallRegistry.clear()
        endpoints = emptyList(); _audioState.value = null; _routing.value = DialerAudioState()
        _canAddCall.value = false
        instance = null
        super.onDestroy()
    }
}

/** Explicit, non-exported notification receiver; stale actions cannot affect a different call. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MissedCallNotifications.ACTION_CLEAR) {
            MissedCallNotifications.clear(context)
            return
        }
        val call = TelecomCallRegistry.find(intent.getStringExtra(SpoofGuardInCallService.EXTRA_CALL_ID)) ?: return
        when (intent.action) {
            SpoofGuardInCallService.ACTION_DECLINE -> if (call.state == Call.STATE_RINGING || call.state == Call.STATE_SIMULATED_RINGING) call.reject(false, null)
            SpoofGuardInCallService.ACTION_HANG_UP -> call.disconnect()
        }
    }
}
