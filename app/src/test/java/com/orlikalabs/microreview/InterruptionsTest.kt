package com.orlikalabs.microreview

import com.orlikalabs.microreview.Interruptions.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptionsTest {
    private fun reason(
        snoozed: Boolean = false,
        quietHours: Boolean = false,
        driving: Boolean = false,
        doNotDisturb: Boolean = false,
        inCall: Boolean = false,
        bluetooth: Boolean = false,
    ) = Interruptions.reason(snoozed, quietHours, driving, doNotDisturb, inCall, bluetooth)

    @Test
    fun nothingInTheWay() {
        assertNull(reason())
    }

    @Test
    fun eachReasonAlone() {
        assertEquals(Reason.SNOOZED, reason(snoozed = true))
        assertEquals(Reason.QUIET_HOURS, reason(quietHours = true))
        assertEquals(Reason.DRIVING, reason(driving = true))
        assertEquals(Reason.DO_NOT_DISTURB, reason(doNotDisturb = true))
        assertEquals(Reason.IN_CALL, reason(inCall = true))
        assertEquals(Reason.BLUETOOTH, reason(bluetooth = true))
    }

    @Test
    fun bluetoothComesAfterDrivingAndBeforeTheRest() {
        assertEquals(Reason.DRIVING, reason(driving = true, bluetooth = true))
        assertEquals(Reason.BLUETOOTH, reason(bluetooth = true, quietHours = true, doNotDisturb = true, inCall = true))
    }

    @Test
    fun theUsersOwnSnoozeIsToldFirst() {
        assertEquals(Reason.SNOOZED, reason(snoozed = true, driving = true, doNotDisturb = true))
        assertEquals(Reason.DRIVING, reason(driving = true, quietHours = true, doNotDisturb = true))
    }

    @Test
    fun vehicleReportsGoStale() {
        val hour = 60 * 60 * 1000L
        assertTrue(Interruptions.inVehicle(entered = true, reportedAt = 0, now = hour))
        // The "left the vehicle" report was missed: don't stay paused for ever.
        assertFalse(Interruptions.inVehicle(entered = true, reportedAt = 0, now = 4 * hour))
        assertFalse(Interruptions.inVehicle(entered = false, reportedAt = 0, now = hour))
        // The clock was set back.
        assertFalse(Interruptions.inVehicle(entered = true, reportedAt = hour, now = 0))
    }
}
