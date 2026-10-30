package com.telegramyou.app.telegram.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {

    @Test
    fun `a pending call is asked, ringing or ringing here, by who made it`() {
        assertEquals(CallPhase.Requesting, callPhaseOf("callStatePending", isOutgoing = true, isReceived = false, mediaConnected = false))
        assertEquals(CallPhase.Ringing, callPhaseOf("callStatePending", isOutgoing = true, isReceived = true, mediaConnected = false))
        assertEquals(CallPhase.Incoming, callPhaseOf("callStatePending", isOutgoing = false, isReceived = true, mediaConnected = false))
    }

    @Test
    fun `ready is only talking once the sound flows`() {
        assertEquals(CallPhase.Connecting, callPhaseOf("callStateExchangingKeys", true, true, false))
        assertEquals(CallPhase.Connecting, callPhaseOf("callStateReady", true, true, mediaConnected = false))
        assertEquals(CallPhase.Active, callPhaseOf("callStateReady", true, true, mediaConnected = true))
        assertEquals(CallPhase.Ended, callPhaseOf("callStateDiscarded", true, true, true))
        assertEquals(CallPhase.Ended, callPhaseOf("callStateError", true, true, true))
    }

    @Test
    fun `how a call ended is told from the reason and the side`() {
        assertEquals(CallEnd.Missed, callEndOf("callStateDiscarded", "callDiscardReasonMissed", isOutgoing = false, wasActive = false))
        assertEquals(CallEnd.Declined, callEndOf("callStateDiscarded", "callDiscardReasonMissed", isOutgoing = true, wasActive = false))
        assertEquals(CallEnd.Declined, callEndOf("callStateDiscarded", "callDiscardReasonDeclined", isOutgoing = true, wasActive = false))
        assertEquals(CallEnd.Missed, callEndOf("callStateDiscarded", "callDiscardReasonHungUp", isOutgoing = false, wasActive = false))
        assertEquals(CallEnd.HungUp, callEndOf("callStateDiscarded", "callDiscardReasonHungUp", isOutgoing = false, wasActive = true))
        assertEquals(CallEnd.Failed, callEndOf("callStateDiscarded", "callDiscardReasonDisconnected", isOutgoing = true, wasActive = true))
        assertEquals(CallEnd.Busy, callEndOf("callStateError", null, isOutgoing = true, wasActive = false, error = "USER_BUSY"))
        assertEquals(CallEnd.Failed, callEndOf("callStateError", null, isOutgoing = true, wasActive = false, error = "CALL_PROTOCOL_LAYER_INVALID"))
    }

    @Test
    fun `the line under the name counts the call as a dialer does`() {
        assertEquals("0:07", callDuration(7))
        assertEquals("12:40", callDuration(760))
        assertEquals("1:02:05", callDuration(3725))
        assertEquals("0:42", callStatusLabel(CallPhase.Active, 42))
        assertEquals("Ringing…", callStatusLabel(CallPhase.Ringing, 0))
        assertEquals("Incoming video call", callStatusLabel(CallPhase.Incoming, 0, isVideo = true))
        assertEquals("Busy", callStatusLabel(CallPhase.Ended, 0, CallEnd.Busy))
        assertEquals("Call ended", callStatusLabel(CallPhase.Ended, 0))
    }

    @Test
    fun `only a person is somebody to call`() {
        val person = ChatPreview(id = 1, title = "Ann", lastMessage = "", timestampLabel = "")
        assertTrue(person.isCallable)
        assertFalse(person.copy(isGroup = true).isCallable)
        assertFalse(person.copy(isChannel = true).isCallable)
        assertFalse(person.copy(isBot = true).isCallable)
        assertFalse(person.copy(isSavedMessages = true).isCallable)
    }
}
