package com.dharanish.spoofguarddialer.telecomcore

import android.telecom.Call

/** Immutable values ensure details changes reach Compose even when Call identity is stable. */
data class CallSnapshot(
    val id: String,
    val state: Int,
    val number: String?,
    val displayName: String?,
    val capabilities: Int,
    val parentId: String?,
    val childIds: List<String>,
    val conferenceableIds: List<String>,
    val connectedAtMillis: Long,
    val isConference: Boolean,
    val accountLabel: String?,
    val presentation: Int,
    val cannedResponses: List<String> = emptyList(),
) {
    fun can(capability: Int) = capabilities and capability != 0
    fun notificationName(): String = if (isConference) "Conference call" else
        displayName?.takeIf { it.isNotBlank() } ?: number?.takeIf { it.isNotBlank() } ?: "Private number"
}

object CallSelection {
    /** Ended identity is display-only; primary() still returns actionable, live calls only. */
    fun presentation(calls: List<CallSnapshot>, ended: CallSnapshot?): CallSnapshot? = primary(calls)
        ?: calls.lastOrNull { it.parentId == null }
        ?: ended

    fun primary(calls: List<CallSnapshot>): CallSnapshot? = calls.filter {
        it.parentId == null && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
    }.minByOrNull {
        when (it.state) {
            Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> 0
            Call.STATE_SELECT_PHONE_ACCOUNT -> 1
            Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_PULLING_CALL -> 2
            Call.STATE_ACTIVE -> 3
            Call.STATE_HOLDING -> 4
            else -> 5
        }
    }
}
