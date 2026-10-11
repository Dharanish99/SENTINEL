package com.dharanish.spoofguarddialer.telecomcore

import android.telecom.Call
import android.telecom.CallAudioState
import org.junit.Assert.*
import org.junit.Test

class CallPresentationAndProximityTest {
    @Test fun handsetActivatesSensorWhileDialingAndTalking() {
        listOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE).forEach {
            assertTrue(ProximityPolicy.enabled(listOf(it), CallAudioState.ROUTE_EARPIECE))
        }
    }
    @Test fun ringingHeldAndEndedCallsReleaseScreen() {
        listOf(Call.STATE_RINGING, Call.STATE_HOLDING, Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED, Call.STATE_SELECT_PHONE_ACCOUNT).forEach {
            assertFalse(ProximityPolicy.enabled(listOf(it), CallAudioState.ROUTE_EARPIECE))
        }
        assertFalse(ProximityPolicy.enabled(emptyList(), CallAudioState.ROUTE_EARPIECE))
    }
    @Test fun speakerAndConnectedAccessoriesKeepScreenAvailable() {
        listOf(CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_WIRED_HEADSET, null).forEach {
            assertFalse(ProximityPolicy.enabled(listOf(Call.STATE_ACTIVE), it))
        }
    }
    @Test fun binaryAndDistanceSensorsDistinguishNearFromFar() {
        assertTrue(ProximityPolicy.isNear(0f, 1f))
        assertFalse(ProximityPolicy.isNear(1f, 1f))
        assertTrue(ProximityPolicy.isNear(3f, 10f))
        assertFalse(ProximityPolicy.isNear(5f, 10f))
        assertFalse(ProximityPolicy.isNear(Float.NaN, 5f))
        assertFalse(ProximityPolicy.isNear(-1f, 5f))
    }
    @Test fun confirmedEndpointSurvivesAvailableListArrivingLater() {
        val routes = listOf(AudioRoute("earpiece-uuid", "Phone", CallAudioState.ROUTE_EARPIECE), AudioRoute("speaker-uuid", "Speaker", CallAudioState.ROUTE_SPEAKER))
        assertEquals("earpiece-uuid", AudioRouteSelection.resolve(routes, "earpiece-uuid", CallAudioState.ROUTE_SPEAKER))
        assertEquals("earpiece-uuid", AudioRouteSelection.resolve(routes, null, CallAudioState.ROUTE_EARPIECE))
        assertEquals("speaker-uuid", AudioRouteSelection.resolve(routes, "removed-device", CallAudioState.ROUTE_SPEAKER))
    }
    @Test fun endedIdentityRemainsForPresentationButCannotBecomeActionTarget() {
        val ended = call("finished", Call.STATE_DISCONNECTED)
        assertEquals(ended, CallSelection.presentation(emptyList(), ended))
        assertNull(CallSelection.primary(listOf(ended)))
    }
    @Test fun remainingHeldCallAndNewRingingCallReplaceEndedIdentity() {
        val ended = call("finished", Call.STATE_DISCONNECTED)
        val held = call("held", Call.STATE_HOLDING)
        val incoming = call("new", Call.STATE_RINGING)
        assertEquals(held, CallSelection.presentation(listOf(held), ended))
        assertEquals(incoming, CallSelection.presentation(listOf(held, incoming), ended))
    }
    @Test fun disconnectingCallKeepsItsOwnIdentityInsteadOfAnEarlierCall() {
        val previous = call("previous", Call.STATE_DISCONNECTED)
        val current = call("current", Call.STATE_DISCONNECTING)
        assertEquals(current, CallSelection.presentation(listOf(current), previous))
    }
    private fun call(id: String, state: Int) = CallSnapshot(id, state, "5550100", "QA caller", 0, null, emptyList(), emptyList(), 1000, false, "SIM", 1)
}
