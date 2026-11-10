package dev.jdtech.jellyfin.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [stopUserData]: what a playback stop writes to the local database. */
class StopUserDataTest {

    @Test
    fun `under 10 percent resets the position`() {
        assertEquals(StopUserData(positionTicks = 0L, played = false), stopUserData(5_000L, 0))
        assertEquals(StopUserData(positionTicks = 0L, played = false), stopUserData(5_000L, 9))
    }

    @Test
    fun `over 90 percent marks the item played`() {
        assertEquals(StopUserData(positionTicks = 0L, played = true), stopUserData(5_000L, 91))
    }

    @Test
    fun `in between saves the position`() {
        assertEquals(StopUserData(positionTicks = 5_000L, played = false), stopUserData(5_000L, 10))
        assertEquals(StopUserData(positionTicks = 5_000L, played = false), stopUserData(5_000L, 90))
    }

    @Test
    fun `no percentage writes nothing`() {
        // #461: an item left while loading keeps its local position, played flag and sync flag.
        assertNull(stopUserData(38_461_200_000L, null))
        assertNull(stopUserData(0L, null))
    }
}
