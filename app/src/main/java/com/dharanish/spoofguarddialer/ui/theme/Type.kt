package com.dharanish.spoofguarddialer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dharanish.spoofguarddialer.R

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

val SentinalTypography = Typography(
    // Caller name on incoming/in-call screens — "Daniel", 30px/700 in the mockup
    displayLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.sp
    ),
    // Screen titles — "Contacts", "Settings" — 22px/700
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp
    ),
    // Contact/recent row names — 15px/600
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
    ),
    // General body text
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp
    ),
    // Secondary/muted text — recent-meta, contact-sub, settings-row-sub
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp
    ),
    // Small labels/nav — "Contacts", "Recents" bottom nav labels — 10-11px
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp
    ),
    // Numbers/timers/phone numbers — always JetBrains Mono
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp
    )
)