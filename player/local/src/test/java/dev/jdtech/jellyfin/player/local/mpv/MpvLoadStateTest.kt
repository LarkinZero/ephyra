package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #446: a stream that failed to open left the mpv player black and BUFFERING without an error
 * when mpv delivered END_FILE before its error lines. Both orders were seen on the emulators
 * with the same refused connection (about one in five opens had END_FILE first).
 */
class MpvLoadStateTest {

    private val state = MpvLoadState().apply { startFile() }

    @Test
    fun `END_FILE before the error line is a failure`() {
        val failure = state.endFile()

        assertEquals(MpvLoadState.Failure(null), failure)
        // The lines that explain it come after: only logged, not reported again.
        assertTrue(state.errorLogged("ffmpeg: tcp: Connection to tcp://demo.jellyfin.org:443 failed"))
        assertTrue(state.errorLogged("stream: Failed to open https://demo.jellyfin.org/..."))
        assertNull(state.endFile())
    }

    @Test
    fun `the error line before END_FILE is the message`() {
        assertFalse(state.errorLogged("stream: Failed to open https://demo.jellyfin.org/..."))

        assertEquals(
            MpvLoadState.Failure("stream: Failed to open https://demo.jellyfin.org/..."),
            state.endFile(),
        )
    }

    @Test
    fun `a file that loaded and then ended is no load failure`() {
        state.fileLoaded()
        state.errorLogged("ffmpeg: tcp: ffurl_read returned 0xffffff92")

        assertNull(state.endFile())
    }

    @Test
    fun `an END_FILE this player caused is no failure`() {
        // Replace (quality switch, episode pick, new intent), Next/Previous, stop(), the #445 stop.
        state.expectEndFile()
        state.errorLogged("stream: Failed to open https://demo.jellyfin.org/...")

        assertNull(state.endFile())
    }

    @Test
    fun `the next file starts clean`() {
        state.endFile()
        state.startFile()

        assertFalse(state.errorLogged("lavf: ...treating it as fatal error."))
        state.fileLoaded()
        assertNull(state.endFile())
    }

    @Test
    fun `a failure is reported once`() {
        assertEquals(MpvLoadState.Failure(null), state.endFile())
        // The player stops mpv after the report; that END_FILE says nothing new.
        state.expectEndFile()
        assertNull(state.endFile())
    }
}
