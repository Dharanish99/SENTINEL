package com.dharanish.spoofguarddialer.ui.theme

import androidx.compose.ui.graphics.Color

enum class AuraState {
    SAFE,       // emerald — no protection engaged
    MERGING,    // amber — hold/dial/merge in progress (shield icon spins)
    ANALYZING,  // amber — merged, protected, awaiting a backend verdict
    THREAT,     // amber — confirmed threat (reserved for a real future verdict; triggers the alert pill)
    GUARDIAN;   // flat gray, no glow — carrier doesn't support merge, or the sequence failed

    val ringColor: Color
        get() = when (this) {
            SAFE -> SentinalEmerald
            MERGING -> SentinalAmber
            ANALYZING -> SentinalAmber
            THREAT -> SentinalAmber
            GUARDIAN -> SentinalGuardianGray
        }

    val auraColor: Color
        get() = when (this) {
            SAFE -> SentinalEmerald.copy(alpha = 0.35f)
            MERGING -> SentinalAmber.copy(alpha = 0.40f)
            ANALYZING -> SentinalAmber.copy(alpha = 0.40f)
            THREAT -> SentinalAmber.copy(alpha = 0.40f)
            GUARDIAN -> Color.Transparent
        }
}