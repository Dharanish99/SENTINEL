package com.dharanish.spoofguarddialer.ui.call

import android.telecom.Call
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dharanish.spoofguarddialer.telecomcore.CallBridgeController
import com.dharanish.spoofguarddialer.ui.components.AvatarAura
import com.dharanish.spoofguarddialer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dtmfRows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "", '#' to "")
)

@Composable
fun InCallScreen(
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = viewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val callerName by viewModel.callerName.collectAsState()
    val isOnHold by viewModel.isOnHold.collectAsState()
    val bridgeState by viewModel.bridgeState.collectAsState()
    val isDialing = callState == Call.STATE_DIALING || callState == Call.STATE_CONNECTING

    val protectionState = when (bridgeState) {
        is CallBridgeController.BridgeState.Idle -> AuraState.SAFE
        is CallBridgeController.BridgeState.Securing -> AuraState.MERGING
        is CallBridgeController.BridgeState.Merged -> AuraState.ANALYZING
        is CallBridgeController.BridgeState.Failed -> AuraState.GUARDIAN
    }

    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showKeypad by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(SentinalBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarAura(
                initial = (callerName ?: phoneNumber ?: "?").trimStart('+').take(1).uppercase(),
                state = protectionState
            )
            Text(
                callerName ?: (phoneNumber ?: "Unknown number"),
                style = MaterialTheme.typography.displayLarge,
                color = SentinalTextPrimary,
                modifier = Modifier.padding(top = 18.dp)
            )
            if (callerName != null) {
                Text(
                    phoneNumber ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = SentinalTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(modifier = Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
                when {
                    isDialing -> Text("Calling…", color = SentinalTextSecondary, fontSize = 13.sp)
                    bridgeState is CallBridgeController.BridgeState.Securing ->
                        Text("Securing connection…", color = SentinalTextSecondary, fontSize = 13.sp)
                    isOnHold -> Text("On hold", color = SentinalAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    else -> Text(formatTimer(elapsedSeconds), style = MaterialTheme.typography.labelLarge, color = SentinalTextSecondary)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.CenterEnd) {
            ThreatPill(visible = protectionState == AuraState.THREAT, onEndCall = { viewModel.hangUp() })
        }

        Spacer(Modifier.weight(1f))

        if (showKeypad) {
            DtmfKeypad(
                onDigit = { digit ->
                    viewModel.playDtmf(digit)
                    scope.launch {
                        delay(120)
                        viewModel.stopDtmf()
                    }
                },
                onHide = { showKeypad = false },
                onEndCall = { viewModel.hangUp() }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 28.dp)) {
                CallControlButton(
                    icon = if (protectionState == AuraState.MERGING) Icons.Filled.Sync else Icons.Filled.Shield,
                    label = "Secure",
                    tint = when {
                        isDialing -> SentinalTextMuted
                        protectionState == AuraState.MERGING || protectionState == AuraState.ANALYZING || protectionState == AuraState.THREAT -> SentinalAmber
                        protectionState == AuraState.GUARDIAN -> SentinalGuardianGray
                        else -> SentinalTextPrimary
                    },
                    onClick = {
                        if (!isDialing && bridgeState is CallBridgeController.BridgeState.Idle) {
                            viewModel.startSecureBridge()
                        }
                    }
                )
                CallControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = "Mute",
                    onClick = { viewModel.setMuted(!isMuted) }
                )
                CallControlButton(icon = Icons.Filled.Dialpad, label = "Keypad", onClick = { showKeypad = true })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(bottom = 36.dp)) {
                CallControlButton(
                    icon = if (isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = if (isOnHold) "Resume" else "Hold",
                    tint = if (isOnHold) SentinalAmber else SentinalTextPrimary,
                    onClick = {
                        // Blocked while our own bridge sequence is holding/dialing —
                        // avoids racing our internal hold()/unhold() calls.
                        if (bridgeState !is CallBridgeController.BridgeState.Securing) {
                            viewModel.toggleHold()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(SentinalRed, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.hangUp() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                CallControlButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "Speaker",
                    tint = if (isSpeakerOn) SentinalEmerald else SentinalTextPrimary,
                    onClick = { viewModel.setSpeaker(!isSpeakerOn) }
                )
            }
        }
    }
}

@Composable
private fun DtmfKeypad(onDigit: (Char) -> Unit, onHide: () -> Unit, onEndCall: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 24.dp)) {
        Icon(
            Icons.Filled.KeyboardArrowDown, "Hide keypad", tint = SentinalTextSecondary,
            modifier = Modifier
                .size(28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHide
                )
                .padding(bottom = 12.dp)
        )
        dtmfRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { (digit, letters) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(56.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onDigit(digit) }
                            ),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(digit.toString(), color = SentinalTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        if (letters.isNotEmpty()) {
                            Text(letters, color = SentinalTextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(SentinalRed, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEndCall
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    tint: Color = SentinalTextPrimary,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(SentinalBtnBg, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = SentinalTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ThreatPill(visible: Boolean, onEndCall: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it })
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(end = 14.dp)
                .background(SentinalCardBg, RoundedCornerShape(999.dp))
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Icon(Icons.Filled.Shield, null, tint = SentinalAmber, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(10.dp))
            Text("Spoofed caller ID detected", color = SentinalTextPrimary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "End call",
                color = SentinalRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEndCall
                    )
                    .padding(4.dp)
            )
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}