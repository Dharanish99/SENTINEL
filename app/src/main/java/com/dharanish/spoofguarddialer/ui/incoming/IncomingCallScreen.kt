package com.dharanish.spoofguarddialer.ui.incoming

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharanish.spoofguarddialer.ui.components.AvatarAura
import com.dharanish.spoofguarddialer.ui.theme.*
import com.dharanish.spoofguarddialer.util.SecuritySettings
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerNumber: String,
    isVerified: Boolean,
    onAnswer: (armed: Boolean) -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Unknown callers default-arm based on the "Auto-secure unknown callers"
    // setting; known contacts never show this at all, so armed is irrelevant then.
    var armed by remember(callerNumber) {
        mutableStateOf(!isVerified && SecuritySettings.isAutoSecureUnknownCallersEnabled(context))
    }

    Column(
        modifier = modifier.fillMaxSize().background(SentinalBg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = SentinalTextSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("SENTINAL secured", color = SentinalTextSecondary, fontSize = 11.sp)
            }

            Spacer(Modifier.height(28.dp))
            AvatarAura(
                initial = callerName.trimStart('+').take(1).uppercase(),
                // Reusing THREAT purely for its amber ring color here — this screen
                // has no alert pill, so there's no risk of implying a real verdict.
                // Amber just means "unverified caller", not "confirmed threat".
                state = if (isVerified) AuraState.SAFE else AuraState.THREAT
            )

            Text(
                callerName,
                style = MaterialTheme.typography.displayLarge,
                color = SentinalTextPrimary,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                callerNumber,
                style = MaterialTheme.typography.labelLarge,
                color = SentinalTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (isVerified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(SentinalBtnBg, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Call, null, tint = SentinalEmerald, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verified contact", color = SentinalEmerald, fontSize = 12.sp)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(if (armed) SentinalEmerald else SentinalBtnBg, RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { armed = !armed }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Shield, null,
                        tint = if (armed) Color.White else SentinalAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (armed) "Secure Mode will activate" else "Unknown caller · Tap to enable Secure Mode",
                        color = if (armed) Color.White else SentinalAmber,
                        fontSize = 12.sp,
                        fontWeight = if (armed) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(46.dp)
            ) {
                QuickAction(icon = Icons.Filled.Notifications, label = "Remind me")
                QuickAction(icon = Icons.Filled.Message, label = "Message")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(SentinalRed, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onReject
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            SlideToAnswer(onAnswer = { onAnswer(armed) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(52.dp).background(SentinalBtnBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SentinalTextPrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = SentinalTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun SlideToAnswer(onAnswer: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val handleSizePx = with(density) { 50.dp.toPx() }
    val handleStartPx = with(density) { 3.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var answered by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "slideNudge")
    val nudgePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
        label = "nudgePhase"
    )
    val nudgeOffsetPx = if (offsetX == 0f && !answered) {
        val bump = if (nudgePhase in 0.08f..0.22f) {
            (1f - abs(nudgePhase - 0.15f) / 0.07f).coerceIn(0f, 1f)
        } else 0f
        bump * with(density) { 7.dp.toPx() }
    } else 0f

    Box(
        modifier = modifier
            .height(56.dp)
            .border(1.5.dp, Color(0xFF333333), RoundedCornerShape(999.dp))
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "Slide to answer",
            color = SentinalTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 76.dp)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((offsetX + nudgeOffsetPx).roundToInt(), 0) }
                .padding(start = 3.dp)
                .size(50.dp)
                .background(SentinalEmerald, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val max = (trackWidthPx - handleSizePx - handleStartPx * 2).coerceAtLeast(0f)
                        offsetX = (offsetX + delta).coerceIn(0f, max)
                    },
                    onDragStopped = {
                        val max = (trackWidthPx - handleSizePx - handleStartPx * 2).coerceAtLeast(1f)
                        if (offsetX >= max * 0.82f) {
                            answered = true
                            onAnswer()
                        } else {
                            offsetX = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Call, "Answer", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}