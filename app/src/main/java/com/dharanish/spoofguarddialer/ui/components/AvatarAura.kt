package com.dharanish.spoofguarddialer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dharanish.spoofguarddialer.ui.theme.AuraState
import com.dharanish.spoofguarddialer.ui.theme.SentinalCardBg
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue

@Composable
fun AvatarAura(
    initial: String,
    state: AuraState,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 168.dp
) {
    // 0.5s color transitions, matching the mockup's CSS `transition: 0.5s ease`
    val ring by animateColorAsState(
        targetValue = state.ringColor,
        animationSpec = tween(500),
        label = "ringColor"
    )
    val auraCenter by animateColorAsState(
        targetValue = state.auraColor,
        animationSpec = tween(500),
        label = "auraColor"
    )

    val auraSize = size + 44.dp // aura extends ~22dp beyond the avatar on each side, per mockup

    Box(
        modifier = modifier.size(auraSize),
        contentAlignment = Alignment.Center
    ) {
        // Aura — soft radial glow behind everything
        Box(
            modifier = Modifier
                .size(auraSize)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(auraCenter, Color.Transparent),
                        center = Offset.Unspecified
                    ),
                    shape = CircleShape
                )
        )

        // Ring + avatar
        Box(
            modifier = Modifier
                .size(size)
                .border(width = 2.dp, color = ring, shape = CircleShape)
                .padding(4.dp)
                .background(SentinalCardBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFFD8D8D8)
            )
        }
    }
}