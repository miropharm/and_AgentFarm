package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.contract.WakeNic
import com.muvusoft.agentfarm.core.state.Link
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeTest {
    @Test
    fun theMagicPacketIsSixFFThenTheMacSixteenTimes() {
        val p = Wake.packet("a4:BB:6d:12:34:56")!!
        assertEquals(102, p.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, p.copyOfRange(0, 6))
        val mac = byteArrayOf(0xA4.toByte(), 0xBB.toByte(), 0x6D, 0x12, 0x34, 0x56)
        for (i in 0 until 16) assertArrayEquals("copy $i", mac, p.copyOfRange(6 + i * 6, 12 + i * 6))
        assertArrayEquals("a dash form is the same card", p, Wake.packet("A4-BB-6D-12-34-56"))
    }

    @Test
    fun aMacThatIsNotOneWakesNothing() {
        for (bad in listOf("", "a4:bb:6d:12:34", "a4:bb:6d:12:34:56:78", "a4:bb:6d:12:34:5", "zz:bb:6d:12:34:56", "00:00:00:00:00:00")) {
            assertNull(bad, Wake.packet(bad))
        }
    }

    @Test
    fun eachCardGetsItsOwnBroadcastAndTheLocalOne() {
        val sends = Wake.sends(listOf(WakeNic("a4:bb:6d:12:34:56", "192.168.1.255"), WakeNic("bad", "10.0.0.255")))
        assertEquals(listOf("192.168.1.255", Wake.EVERYWHERE), sends.map { it.second })
        assertEquals(listOf(Wake.EVERYWHERE), Wake.sends(listOf(WakeNic("a4:bb:6d:12:34:56", ""))).map { it.second })
    }

    @Test
    fun itIsOfferedOnlyWhileTheFarmCannotBeReachedAndACardIsKnown() {
        val nic = listOf(WakeNic("a4:bb:6d:12:34:56", "192.168.1.255"))
        assertTrue(Wake.offered(Link.Offline("unreachable", 1), nic))
        assertTrue(Wake.offered(Link.Connecting("192.168.1.5:7443", 3, 1), nic))
        assertFalse(Wake.offered(Link.Online("192.168.1.5:7443", "manage", emptyMap(), 1), nic))
        assertFalse("a refused phone is not helped by a wake", Wake.offered(Link.Refused("revoked", null, 1), nic))
        assertFalse(Wake.offered(Link.Offline("unreachable", 1), emptyList()))
        assertFalse(Wake.offered(Link.Offline("unreachable", 1), listOf(WakeNic("00:00:00:00:00:00", "x"))))
        assertFalse(Wake.offered(null, nic))
    }

    @Test
    fun overTheSavedCopyItWaitsUntilTheLinkHasFailedOnce() {
        val nic = listOf(WakeNic("a4:bb:6d:12:34:56", "192.168.1.255"))
        assertFalse("the tries of an opening", Wake.offeredOverCopy(Link.Connecting("192.168.1.5:7443", 2, 1), nic, failed = false))
        assertTrue(Wake.offeredOverCopy(Link.Offline("closed", 1), nic, failed = false))
        assertTrue("a retry after the link failed", Wake.offeredOverCopy(Link.Connecting("192.168.1.5:7443", 1, 1), nic, failed = true))
        assertFalse(Wake.offeredOverCopy(Link.Online("192.168.1.5:7443", "manage", emptyMap(), 1), nic, failed = true))
        assertFalse(Wake.offeredOverCopy(Link.Offline("unreachable", 1), emptyList(), failed = true))
    }
}
