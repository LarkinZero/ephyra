package dev.jdtech.jellyfin.player.local.presentation

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #374: a [PlayerEvents.PlayerError] says whether anything was loaded, so the phone player can
 * close when nothing will ever play instead of staying open, empty and silent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerErrorTest {

    @Test
    fun `a failed start has nothing loaded and keeps the failure's message`() {
        val failure = IllegalStateException("Invalid HTTP status in response: 404")

        val event = PlayerEvents.PlayerError.startFailed(failure)

        assertTrue(event.nothingLoaded)
        assertEquals("Invalid HTTP status in response: 404", event.error.message)
        assertSame(failure, event.error.cause)
    }

    @Test
    fun `a failed start without a message still has nothing loaded`() {
        // PlaylistManager.toPlayerItem throws a NoSuchElementException without a message for an
        // unfinished download while offline (#454).
        val event = PlayerEvents.PlayerError.startFailed(NoSuchElementException())

        assertTrue(event.nothingLoaded)
        assertNull(event.error.message)
    }

    @Test
    fun `a start that returned no item has nothing loaded`() {
        assertTrue(PlayerEvents.PlayerError.startFailed(null).nothingLoaded)
    }

    @Test
    fun `a playback error on a loaded item keeps the player`() {
        val error = playbackError()

        val event = PlayerEvents.PlayerError.fromPlayer(error, MediaItem.fromUri("https://x/1"))

        assertFalse(event.nothingLoaded)
        assertSame(error, event.error)
    }

    @Test
    fun `a playback error without an item has nothing loaded`() {
        assertTrue(PlayerEvents.PlayerError.fromPlayer(playbackError(), null).nothingLoaded)
    }

    private fun playbackError() =
        PlaybackException(
            "Source error",
            null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        )
}
