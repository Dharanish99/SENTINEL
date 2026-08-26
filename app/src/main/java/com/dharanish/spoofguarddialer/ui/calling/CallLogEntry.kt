package com.dharanish.spoofguarddialer.ui.calllog

data class CallLogEntry(
    val name: String?,
    val number: String,
    val type: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long
)

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, OTHER
}