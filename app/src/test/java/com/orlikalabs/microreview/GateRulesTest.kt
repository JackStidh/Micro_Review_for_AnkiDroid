package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateRulesTest {
    private val own = "com.orlikalabs.microreview"
    private val instagram = "com.instagram.android"
    private val launcher = "com.nothing.launcher"
    private val gated = setOf(instagram, "com.android.chrome")

    private fun mustGate(
        foreground: String,
        enabled: Boolean = true,
        pass: String? = null,
        hasDueCard: Boolean = true,
        paused: Boolean = false,
        apps: Set<String> = gated,
    ) = GateRules.mustGate(foreground, own, enabled, apps, pass, hasDueCard, paused)

    @Test
    fun aChosenAppIsGated() {
        assertTrue(mustGate(instagram))
        assertFalse(mustGate(launcher))
    }

    @Test
    fun notGatedWhenOffPausedOrNothingDue() {
        assertFalse(mustGate(instagram, enabled = false))
        assertFalse(mustGate(instagram, paused = true))
        assertFalse(mustGate(instagram, hasDueCard = false))
    }

    @Test
    fun thePassOpensItsAppOnly() {
        assertFalse(mustGate(instagram, pass = instagram))
        assertTrue(mustGate("com.android.chrome", pass = instagram))
    }

    @Test
    fun closingAndOpeningAgainAsksAgain() {
        // Paid, in the app: the pass holds.
        var pass = GateRules.passAfter(instagram, instagram)
        assertEquals(instagram, pass)
        // Home: the pass is gone, with no grace period.
        pass = GateRules.passAfter(pass, launcher)
        assertNull(pass)
        pass = GateRules.passAfter(pass, instagram)
        assertTrue(mustGate(instagram, pass = pass))
    }

    @Test
    fun systemWindowsKeepThePass() {
        assertEquals(instagram, GateRules.passAfter(instagram, "com.android.systemui"))
        assertEquals(instagram, GateRules.passAfter(instagram, "com.google.android.permissioncontroller"))
        assertNull(GateRules.passAfter(null, instagram))
    }

    @Test
    fun openingThisAppEndsThePass() {
        // Instagram, then Micro Review's settings, then Instagram again: asks again.
        val pass = GateRules.passAfter(instagram, own)
        assertNull(pass)
        assertTrue(mustGate(instagram, pass = GateRules.passAfter(pass, instagram)))
    }

    @Test
    fun someAppsAreNeverGated() {
        val everything = gated + own + GateRules.NEVER_GATED + GateRules.TRANSIENT_PACKAGES
        assertFalse(mustGate(own, apps = everything))
        assertFalse(mustGate("com.ichi2.anki", apps = everything))
        assertFalse(mustGate("com.google.android.dialer", apps = everything))
        assertFalse(mustGate("com.android.systemui", apps = everything))
    }

    @Test
    fun oneEmergencySkipPerDay() {
        assertEquals(1, GateRules.skipsLeft(perDay = 1, usedDay = 0, used = 0, today = 20_000))
        assertEquals(0, GateRules.skipsLeft(perDay = 1, usedDay = 20_000, used = 1, today = 20_000))
        // A new day gives it back.
        assertEquals(1, GateRules.skipsLeft(perDay = 1, usedDay = 20_000, used = 1, today = 20_001))
        assertEquals(0, GateRules.skipsLeft(perDay = 0, usedDay = 0, used = 0, today = 20_001))
        assertEquals(0, GateRules.skipsLeft(perDay = 1, usedDay = 20_000, used = 5, today = 20_000))
    }

    @Test
    fun cardsRequiredStaysInRange() {
        assertEquals(1, GateRules.cardsRequired(0))
        assertEquals(5, GateRules.cardsRequired(5))
        assertEquals(GateRules.MAX_CARDS, GateRules.cardsRequired(500))
    }
}
