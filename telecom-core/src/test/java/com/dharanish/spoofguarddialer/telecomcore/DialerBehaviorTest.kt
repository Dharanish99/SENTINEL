package com.dharanish.spoofguarddialer.telecomcore

import android.telecom.Call
import org.junit.Assert.*
import org.junit.Test

class DialerBehaviorTest {
    @Test fun formattedInternationalNumbersKeepTheirCountryCode() { assertEquals("+442079460123", DialString.normalize("+44 (20) 7946-0123")) }
    @Test fun unicodeDigitsBecomeDialableDigits() { assertEquals("12345", DialString.normalize("١٢३４5")) }
    @Test fun vanityNumbersUseTelephoneLetterMapping() { assertEquals("18003569377", DialString.normalize("1-800-FLOWERS")) }
    @Test fun serviceCodesAndPostDialControlsSurvive() { assertEquals("*21*5550100#,123;45#", DialString.normalize("*21*5550100#,123;45#")) }
    @Test fun emptyAndMalformedNumbersCannotBeCalled() {
        listOf("", "+", "*#", "++123", "12+34", ",123", ";123").forEach { assertFalse(it, DialString.isCallable(it)) }
    }
    @Test fun shortCodesAndInternationalNumbersCanBeCalled() {
        listOf("112", "*#06#", "+15550100", "5550100,123;45").forEach { assertTrue(it, DialString.isCallable(it)) }
    }
    @Test fun inputsAreBounded() { assertEquals(256, DialString.normalize("1".repeat(1000)).length) }
    @Test fun ringingCallTakesPriorityOverAnActiveCall() {
        assertEquals("incoming", CallSelection.primary(listOf(call("active", Call.STATE_ACTIVE), call("incoming", Call.STATE_RINGING)))?.id)
    }
    @Test fun outgoingCallTakesPriorityOverHeldCall() {
        assertEquals("outgoing", CallSelection.primary(listOf(call("held", Call.STATE_HOLDING), call("outgoing", Call.STATE_DIALING)))?.id)
    }
    @Test fun conferenceParentIsSelectedInsteadOfItsChildren() {
        assertEquals("conference", CallSelection.primary(listOf(call("child", Call.STATE_ACTIVE, "conference"), call("conference", Call.STATE_ACTIVE)))?.id)
    }
    @Test fun disconnectedAndDisconnectingCallsDoNotStealFocus() {
        assertEquals("held", CallSelection.primary(listOf(call("old", Call.STATE_DISCONNECTED), call("ending", Call.STATE_DISCONNECTING), call("held", Call.STATE_HOLDING)))?.id)
    }
    @Test fun accountChoiceTakesPriorityOverEstablishedCalls() {
        assertEquals("choose", CallSelection.primary(listOf(call("active", Call.STATE_ACTIVE), call("choose", Call.STATE_SELECT_PHONE_ACCOUNT)))?.id)
    }
    @Test fun removedCallsLeaveNoPrimaryCall() { assertNull(CallSelection.primary(emptyList())) }
    @Test fun blankCarrierNamesCannotCrashCallNotifications() {
        val incoming = call("incoming", Call.STATE_RINGING).copy(displayName = "", number = "5550101")
        assertEquals("5550101", incoming.notificationName())
        assertEquals("Private number", incoming.copy(displayName = " ", number = "").notificationName())
        assertEquals("Conference call", incoming.copy(isConference = true).notificationName())
    }
    private fun call(id: String, state: Int, parent: String? = null) = CallSnapshot(id, state, null, null, 0, parent, emptyList(), emptyList(), 0, parent == null && id == "conference", null, 1)
}
