package dev.jdtech.jellyfin.repository

import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** CE (#281): the server's answer when it comes, Room's copy when it doesn't. */
class StoredFallbackTest {
    private var storedBuilt = false
    private val stored: suspend () -> String = {
        storedBuilt = true
        "room"
    }

    @Test
    fun `the server's answer wins and the stored copy is not built`() = runBlocking {
        assertEquals("server", serverOrStored(stored) { "server" })
        assertTrue(!storedBuilt)
    }

    @Test
    fun `a failing server gives the stored copy`() = runBlocking {
        assertEquals("room", serverOrStored(stored) { throw IOException("unreachable") })
    }

    @Test
    fun `a slow server gives the stored copy after the timeout`() = runBlocking {
        assertEquals("room", serverOrStored(stored, timeoutMs = 50) { awaitCancellation() })
    }

    @Test
    fun `without a stored copy the server's error surfaces`() = runBlocking {
        try {
            serverOrStored<String>(stored = null) { throw IOException("unreachable") }
            fail("expected the server's error")
        } catch (e: IOException) {
            assertEquals("unreachable", e.message)
        }
    }

    @Test
    fun `a server answer of null is not taken for a timeout`() = runBlocking {
        assertNull(serverOrStored<String?>(stored) { null })
    }

    @Test
    fun `an empty stored list means Room has nothing`() = runBlocking {
        try {
            serverOrStoredList<String>(emptyList()) { throw IOException("unreachable") }
            fail("expected the server's error")
        } catch (e: IOException) {
            assertEquals("unreachable", e.message)
        }
    }

    @Test
    fun `a stored list stands in for a failing server`() = runBlocking {
        assertEquals(
            listOf("episode 1"),
            serverOrStoredList(listOf("episode 1")) { throw IOException("unreachable") },
        )
    }
}
