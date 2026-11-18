package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcPairingTest {
    @Test
    fun codesAreReadAsTyped() {
        assertEquals("k3p9x2mq7rtvw8de", PcPairing.normalize("K3P9-X2MQ-7RTV-W8DE"))
        assertEquals("k3p9x2mq7rtvw8de", PcPairing.normalize(" k3p9 x2mq 7rtv w8de "))
        assertNull(PcPairing.normalize("k3p9-x2mq-7rtv"))
        assertNull(PcPairing.normalize("k3p9-x2mq-7rtv-w8de-zz"))
        assertEquals("k3p9-x2mq-7rtv-w8de", PcPairing.display("k3p9x2mq7rtvw8de"))
        assertEquals("mrf-k3p9x2mq7rtvw8de", PcPairing.topic("k3p9x2mq7rtvw8de"))
    }

    @Test
    fun aMessagePausesFromTheRelaysTime() {
        val line = """{"id":"abc","time":1763490000,"expires":1763533200,"event":"message","topic":"mrf-x","message":"300"}"""
        assertEquals((1763490000L + 300) * 1000, PcPairing.pausedUntil(line))
    }

    @Test
    fun otherLinesDontPause() {
        assertEquals(0L, PcPairing.pausedUntil("""{"id":"a","time":1763490000,"event":"open","topic":"mrf-x"}"""))
        assertEquals(0L, PcPairing.pausedUntil("""{"id":"a","time":1763490000,"event":"keepalive","topic":"mrf-x"}"""))
        assertEquals(0L, PcPairing.pausedUntil("""{"id":"a","time":1763490000,"event":"message","message":"hello"}"""))
        assertEquals(0L, PcPairing.pausedUntil(""))
    }

    @Test
    fun zeroResumesAtOnce() {
        assertEquals(1763490000L * 1000, PcPairing.pausedUntil("""{"time":1763490000,"event":"message","message":"0"}"""))
    }

    @Test
    fun aPauseIsCapped() {
        val line = """{"time":1000,"event":"message","message":"99999"}"""
        assertEquals((1000L + 30 * 60) * 1000, PcPairing.pausedUntil(line))
    }
}
