package com.dharanish.spoofguarddialer.ui.calllog

data class CallLogEntry(
    val name: String?,
    val number: String,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val id: Long = 0,
    val presentation: Int = 1,
    val accountId: String? = null,
)

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, OTHER
}
