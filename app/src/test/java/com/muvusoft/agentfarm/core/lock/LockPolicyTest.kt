package com.muvusoft.agentfarm.core.lock

import com.muvusoft.agentfarm.core.lock.LockPolicy.Availability
import com.muvusoft.agentfarm.core.lock.LockPolicy.Confirm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val now = 1_000_000L

    @Test
    fun aDisabledLockNeverLocks() {
        assertFalse(LockPolicy.locked(enabled = false, unlocked = false, backgroundSince = 0, now = now))
    }

    @Test
    fun anEnabledLockAsksUntilTheOwnerAnswered() {
        assertTrue(LockPolicy.locked(enabled = true, unlocked = false, backgroundSince = null, now = now))
        assertFalse(LockPolicy.locked(enabled = true, unlocked = true, backgroundSince = null, now = now))
    }

    @Test
    fun aShortTripAwayKeepsItOpenALongOneLocksIt() {
        val short = now - LockPolicy.GRACE_MS + 1
        val long = now - LockPolicy.GRACE_MS
        assertFalse(LockPolicy.locked(enabled = true, unlocked = true, backgroundSince = short, now = now))
        assertTrue(LockPolicy.locked(enabled = true, unlocked = true, backgroundSince = long, now = now))
    }

    @Test
    fun destructiveAsksTheOwnerOnlyWhenThePhoneCanProveOne() {
        Availability.values().forEach {
            val expected = if (it == Availability.READY) Confirm.OWNER else Confirm.DIALOG
            assertEquals(it.name, expected, LockPolicy.confirm(it))
        }
    }

    @Test
    fun everyUnavailableStateHasASentence() {
        Availability.values().forEach {
            if (it == Availability.READY) assertNull(LockPolicy.reason(it)) else assertNotNull(it.name, LockPolicy.reason(it))
        }
    }
}
