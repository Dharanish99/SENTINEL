package com.dharanish.spoofguarddialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dharanish.spoofguarddialer.ui.theme.SentinalBtnBg
import com.dharanish.spoofguarddialer.ui.theme.SentinalTextSecondary

@Composable
fun SmallAvatar(initial: String, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Box(
        modifier = modifier.size(size).background(SentinalBtnBg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, color = SentinalTextSecondary, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.33).sp)
    }
}