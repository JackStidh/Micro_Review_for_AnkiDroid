package com.orlikalabs.microreview

import com.orlikalabs.microreview.Interruptions.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InterruptionsTest {
    private fun reason(
        snoozed: Boolean = false,
        quietHours: Boolean = false,
        doNotDisturb: Boolean = false,
        inCall: Boolean = false,
        bluetooth: Boolean = false,
    ) = Interruptions.reason(snoozed, quietHours, doNotDisturb, inCall, bluetooth)

    @Test
    fun nothingInTheWay() {
        assertNull(reason())
    }

    @Test
    fun eachReasonAlone() {
        assertEquals(Reason.SNOOZED, reason(snoozed = true))
        assertEquals(Reason.QUIET_HOURS, reason(quietHours = true))
        assertEquals(Reason.DO_NOT_DISTURB, reason(doNotDisturb = true))
        assertEquals(Reason.IN_CALL, reason(inCall = true))
        assertEquals(Reason.BLUETOOTH, reason(bluetooth = true))
    }

    @Test
    fun theUsersOwnSnoozeIsToldFirst() {
        assertEquals(Reason.SNOOZED, reason(snoozed = true, bluetooth = true, doNotDisturb = true))
    }

    @Test
    fun bluetoothComesBeforeTheRest() {
        assertEquals(Reason.BLUETOOTH, reason(bluetooth = true, quietHours = true, doNotDisturb = true, inCall = true))
    }
}
