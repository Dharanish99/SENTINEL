package com.dharanish.spoofguarddialer

import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.SpoofGuardInCallService
import com.dharanish.spoofguarddialer.telecomcore.TelecomCallRegistry
import com.dharanish.spoofguarddialer.telecomcore.CallProximityController
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.dharanish.spoofguarddialer.ui.call.CallScreen
import com.dharanish.spoofguarddialer.ui.call.CallViewModel
import com.dharanish.spoofguarddialer.ui.theme.SpoofGuardDialerTheme
import kotlinx.coroutines.delay

/** Only this activity is visible above keyguard. It never contains contacts, settings or history. */
class InCallActivity : ComponentActivity() {
    private var showDialpad by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleAction(intent)
        setContent {
            SpoofGuardDialerTheme {
                val model: CallViewModel = viewModel()
                val hasCall by model.hasCall.collectAsState()
                val covered by CallProximityController.covered.collectAsState()
                DisposableEffect(covered) {
                    val previousBrightness = window.attributes.screenBrightness
                    if (covered) window.attributes = window.attributes.apply { screenBrightness = 0f }
                    onDispose { window.attributes = window.attributes.apply { screenBrightness = previousBrightness } }
                }
                LaunchedEffect(hasCall) {
                    if (!hasCall) { delay(2200); if (TelecomCallRegistry.calls.value.isEmpty()) finishAndRemoveTask() }
                }
                BackHandler { moveTaskToBack(true) }
                Scaffold { padding ->
                    Box(Modifier.fillMaxSize()) {
                        CallScreen(Modifier.padding(padding), model, showDialpad)
                        if (covered) Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                            detectTapGestures(onTap = {}, onLongPress = {}, onDoubleTap = {})
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleAction(intent) }

    private fun handleAction(intent: Intent) {
        showDialpad = intent.getBooleanExtra("show_dialpad", false)
        if (intent.action == SpoofGuardInCallService.ACTION_ANSWER) {
            val call = TelecomCallRegistry.find(intent.getStringExtra(SpoofGuardInCallService.EXTRA_CALL_ID))
            if (call?.state == Call.STATE_RINGING || call?.state == Call.STATE_SIMULATED_RINGING) call?.answer(VideoProfile.STATE_AUDIO_ONLY)
            intent.action = null
        }
    }
}
