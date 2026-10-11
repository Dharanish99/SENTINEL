package com.dharanish.spoofguarddialer.ui.call

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.MainActivity
import com.dharanish.spoofguarddialer.telecomcore.CallBridgeController
import com.dharanish.spoofguarddialer.telecomcore.CallSnapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.dharanish.spoofguarddialer.ui.components.AvatarAura
import com.dharanish.spoofguarddialer.ui.theme.*
import com.dharanish.spoofguarddialer.util.PhoneActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(modifier: Modifier = Modifier, viewModel: CallViewModel = viewModel(), initialDialpad: Boolean = false) {
    val snapshot by viewModel.presentedSnapshot.collectAsState()
    val allCalls by viewModel.snapshots.collectAsState()
    val callerName by viewModel.callerName.collectAsState()
    val bridgeState by viewModel.bridgeState.collectAsState()
    val audio by viewModel.audioRouting.collectAsState()
    val canAddCall by com.dharanish.spoofguarddialer.telecomcore.SpoofGuardInCallService.canAddAnotherCall.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val context = LocalContext.current
    var showKeypad by rememberSaveable { mutableStateOf(initialDialpad) }
    var showAudio by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var digits by rememberSaveable(snapshot?.id) { mutableStateOf("") }
    val isDialing = snapshot?.state in listOf(Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_PULLING_CALL)
    val active = snapshot?.state == Call.STATE_ACTIVE
    val onHold = snapshot?.state == Call.STATE_HOLDING
    val ended = snapshot?.state == Call.STATE_DISCONNECTED
    val ending = ended || snapshot?.state == Call.STATE_DISCONNECTING || snapshot == null
    val securing = bridgeState is CallBridgeController.BridgeState.Securing
    val protectionState = when (bridgeState) {
        is CallBridgeController.BridgeState.Idle -> AuraState.SAFE
        is CallBridgeController.BridgeState.Securing -> AuraState.MERGING
        is CallBridgeController.BridgeState.Merged -> AuraState.ANALYZING
        is CallBridgeController.BridgeState.Failed -> AuraState.GUARDIAN
    }
    val route = audio.routes.firstOrNull { it.id == audio.selectedId }
    val audioIcon = when (route?.type) {
        CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.BluetoothAudio
        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
        CallAudioState.ROUTE_SPEAKER -> Icons.Default.VolumeUp
        else -> Icons.Default.PhoneInTalk
    }
    LaunchedEffect(ending) { if (ending) { showAudio = false; showParticipants = false; viewModel.stopDtmf() } }
    LaunchedEffect(initialDialpad) { if (initialDialpad) showKeypad = true }
    DisposableEffect(snapshot?.id) { onDispose { viewModel.stopDtmf() } }
    BackHandler(enabled = showKeypad) { showKeypad = false }
    val others = allCalls.filter { it.id != snapshot?.id && it.parentId == null && it.state in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING) }
    val canMerge = snapshot?.conferenceableIds?.isNotEmpty() == true || snapshot?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true

    BoxWithConstraints(modifier.fillMaxSize()) {
    val breathingRoom = if (showKeypad) 0.dp else ((maxHeight - 600.dp) / 3).coerceIn(0.dp, 80.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(snapshot?.accountLabel ?: "Phone call", style = MaterialTheme.typography.bodySmall, color = SentinalTextSecondary)
        Spacer(Modifier.height(12.dp + breathingRoom))
        if (!showKeypad) AvatarAura(initial = (callerName ?: snapshot?.number ?: "?").trimStart('+').take(1).uppercase(), state = protectionState, size = 108.dp)
        Text(if (snapshot?.isConference == true) "Conference call" else callerName ?: snapshot?.displayName ?: snapshot?.number ?: "Call ending…",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (callerName != null && snapshot?.isConference != true) Text(snapshot?.number.orEmpty(), style = MaterialTheme.typography.labelLarge, color = SentinalTextSecondary)
        CallStatus(snapshot, securing)
        others.forEach { other ->
            Surface(color = SentinalBtnBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text((other.displayName ?: other.number ?: "Other call") + if (other.state == Call.STATE_HOLDING) " · On hold" else " · Active",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
            }
        }
        if (protectionState == AuraState.THREAT) Text("Spoofed caller ID detected", color = SentinalAmber)

        if (showKeypad) {
            Text(digits.takeLast(24).ifEmpty { " " }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
            DtmfKeypad(enabled = active, onStart = { viewModel.playDtmf(it); digits += it }, onStop = viewModel::stopDtmf)
            TextButton(onClick = { showKeypad = false }) { Icon(Icons.Default.KeyboardArrowDown, null); Text("Hide keypad") }
        } else {
            Spacer(Modifier.height(12.dp + breathingRoom))
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControlButton(Icons.Default.Shield, "Secure", !ending && !isDialing && bridgeState is CallBridgeController.BridgeState.Idle,
                    if (protectionState in listOf(AuraState.MERGING, AuraState.ANALYZING, AuraState.THREAT)) SentinalAmber else SentinalTextPrimary,
                    onClick = viewModel::startSecureBridge)
                CallControlButton(if (audio.muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", active || onHold,
                    if (audio.muted) SentinalEmerald else SentinalTextPrimary, selected = audio.muted,
                    stateLabel = if (audio.muted) "Muted" else "Microphone on", onClick = { viewModel.setMuted(!audio.muted) })
                CallControlButton(Icons.Default.Dialpad, "Keypad", active, onClick = { showKeypad = true })
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControlButton(if (onHold) Icons.Default.PlayArrow else Icons.Default.Pause, if (onHold) "Resume" else "Hold",
                    !ending && !securing && snapshot?.can(Call.Details.CAPABILITY_HOLD) == true, selected = onHold,
                    stateLabel = if (onHold) "On hold" else "Not on hold", onClick = viewModel::toggleHold)
                CallControlButton(audioIcon, route?.label ?: "Audio", !ending && audio.routes.isNotEmpty(), selected = route?.type == CallAudioState.ROUTE_SPEAKER, onClick = { showAudio = true })
                CallControlButton(Icons.Default.Add, "Add call", !securing && (active || onHold) && canAddCall,
                    onClick = { PhoneActions.afterUnlock(context) { PhoneActions.launch(context, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_DIAL).setData(Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } })
            }
            if (others.isNotEmpty() || canMerge || snapshot?.isConference == true) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                if (others.any { it.state == Call.STATE_HOLDING }) TextButton(onClick = viewModel::swap, enabled = !securing) { Icon(Icons.Default.SwapCalls, null); Text("Swap") }
                if (canMerge) TextButton(onClick = viewModel::mergeCalls, enabled = !securing) { Icon(Icons.Default.CallMerge, null); Text("Merge") }
                if (snapshot?.isConference == true) TextButton(onClick = { showParticipants = true }) { Text("Participants") }
            }
        }
        Spacer(Modifier.height(24.dp + breathingRoom))
        FilledIconButton(onClick = viewModel::hangUp, enabled = !ending, modifier = Modifier.size(width = 88.dp, height = 64.dp), shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = SentinalRed, contentColor = Color.White)) { Icon(Icons.Default.CallEnd, "End call", Modifier.size(30.dp)) }
        Spacer(Modifier.height(16.dp))
    }
    }
    if (showAudio) ModalBottomSheet(onDismissRequest = { showAudio = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Call audio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
            audio.routes.forEach { available ->
                Surface(onClick = { viewModel.selectAudio(available.id) }, enabled = audio.pendingId == null, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(available.label) }, leadingContent = { RadioButton(selected = audio.selectedId == available.id, onClick = null) },
                        trailingContent = { if (audio.pendingId == available.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
                }
            }
        }
    }
    if (showParticipants) ModalBottomSheet(onDismissRequest = { showParticipants = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Conference participants", style = MaterialTheme.typography.headlineMedium)
            allCalls.filter { it.parentId == snapshot?.id }.forEach { participant ->
                ListItem(headlineContent = { Text(participant.displayName ?: participant.number ?: "Participant") }, trailingContent = {
                    Row {
                        if (participant.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE)) IconButton(onClick = { viewModel.splitParticipant(participant.id) }) { Icon(Icons.Default.CallSplit, "Separate participant") }
                        if (participant.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE)) IconButton(onClick = { viewModel.disconnectParticipant(participant.id) }) { Icon(Icons.Default.CallEnd, "Disconnect participant") }
                    }
                })
            }
            if (snapshot?.childIds.isNullOrEmpty()) Text("Your carrier does not provide individual participant details.", modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    val error = actionError ?: audio.error
    if (error != null) AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Call action unavailable") }, text = { Text(error) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } })
}

/** Timer updates recompose only the status, leaving the caller and controls in place. */
@Composable
private fun CallStatus(snapshot: CallSnapshot?, securing: Boolean) {
    val connectedAt = snapshot?.connectedAtMillis ?: 0L
    val elapsedBase = remember(snapshot?.id, connectedAt) {
        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectedAt).coerceAtLeast(0)
    }
    var duration by remember(snapshot?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(snapshot?.id, connectedAt, snapshot?.state) {
        if (connectedAt > 0 && snapshot?.state !in listOf(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING)) {
            while (true) { duration = (SystemClock.elapsedRealtime() - elapsedBase).coerceAtLeast(0) / 1000; delay(1000) }
        }
    }
    val status = when (snapshot?.state) {
        Call.STATE_DISCONNECTED -> "Call ended"
        Call.STATE_DISCONNECTING -> "Ending call…"
        Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_PULLING_CALL -> "Calling…"
        Call.STATE_HOLDING -> "On hold · " + formatTimer(duration)
        Call.STATE_ACTIVE -> if (securing) "Securing connection…" else formatTimer(duration)
        else -> "Connecting…"
    }
    Text(status, style = MaterialTheme.typography.labelLarge,
        color = if (snapshot?.state == Call.STATE_HOLDING) SentinalAmber else SentinalTextSecondary,
        modifier = Modifier.padding(vertical = 12.dp).semantics {
            if (snapshot?.state == Call.STATE_DISCONNECTED) liveRegion = LiveRegionMode.Polite
        })
}

@Composable
private fun CallControlButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = SentinalTextPrimary, selected: Boolean = false, stateLabel: String? = null, onClick: () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp).semantics { stateLabel?.let { stateDescription = it } },
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (selected) SentinalEmerald.copy(alpha = 0.22f) else SentinalBtnBg, contentColor = tint)) { Icon(icon, label, Modifier.size(27.dp)) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (enabled) SentinalTextSecondary else SentinalTextMuted, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DtmfKeypad(enabled: Boolean, onStart: (Char) -> Unit, onStop: () -> Unit) {
    val scope = rememberCoroutineScope()
    listOf("123", "456", "789", "*0#").forEach { row ->
        Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { digit ->
                Surface(shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(72.dp)
                    .semantics { role = Role.Button; contentDescription = digit.toString(); onClick {
                        if (enabled) scope.launch { onStart(digit); try { delay(150) } finally { onStop() } }; enabled
                    } }.pointerInput(digit, enabled) { detectTapGestures(onPress = {
                        if (enabled) { onStart(digit); try { tryAwaitRelease() } finally { onStop() } }
                    }) }) { Box(contentAlignment = Alignment.Center) { Text(digit.toString(), fontSize = 28.sp, color = if (enabled) SentinalTextPrimary else SentinalTextMuted) } }
            }
        }
    }
}

private fun formatTimer(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
