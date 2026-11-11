package dev.jdtech.jellyfin.player.local.mpv

import androidx.media3.common.PlaybackException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvErrorRecoveryTest {

    private val recovery = MpvErrorRecovery()

    @Test
    fun `a prepare without an error is a normal load`() {
        assertNull(recovery.onPrepare(itemCount = 3))
    }

    @Test
    fun `prepare after a lost connection loads the item it broke on again, once`() {
        recovery.onError(2, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        assertEquals(2, recovery.onPrepare(itemCount = 3))
        assertNull(recovery.onPrepare(itemCount = 3))
    }

    @Test
    fun `prepare after any other error loads the item again too`() {
        recovery.onError(0, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

        assertEquals(0, recovery.onPrepare(itemCount = 1))
    }

    @Test
    fun `a new playlist makes the next prepare a normal load`() {
        recovery.onError(1, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        recovery.forget()

        assertNull(recovery.onPrepare(itemCount = 3))
    }

    @Test
    fun `an index outside the playlist is a normal load`() {
        recovery.onError(3, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

        assertNull(recovery.onPrepare(itemCount = 3))
    }

    @Test
    fun `a reload after a lost connection that fails to open is a network error`() {
        recovery.onError(0, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        recovery.onPrepare(itemCount = 1)

        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            recovery.loadErrorCode("ffmpeg: tcp: Connection to tcp://10.0.2.2:8096 failed"),
        )
    }

    @Test
    fun `failed reloads keep counting as network errors until one opens`() {
        recovery.onError(0, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        recovery.onPrepare(itemCount = 1)
        recovery.onError(0, recovery.loadErrorCode(null))
        recovery.onPrepare(itemCount = 1)

        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            recovery.loadErrorCode(null),
        )

        recovery.fileLoaded()
        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, recovery.loadErrorCode(null))
    }

    @Test
    fun `a file that fails on its first load keeps its plain error (#446)`() {
        recovery.onPrepare(itemCount = 1)

        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, recovery.loadErrorCode(null))
        assertEquals(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            recovery.loadErrorCode("ffmpeg/demuxer: Failed to recognize file format."),
        )
    }

    @Test
    fun `Play after a plain load error retries with the plain error again`() {
        recovery.onError(0, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        recovery.onPrepare(itemCount = 1)

        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, recovery.loadErrorCode(null))
    }

    @Test
    fun `MPVPlayer offers prepare and reloads through this class`() {
        // #474: without COMMAND_PREPARE the Play button only called play(), which did nothing in
        // the error state, and NetworkRecovery never waited for the network on mpv. Gradle runs
        // unit tests from the module directory.
        val source =
            File("src/main/java/dev/jdtech/jellyfin/player/local/mpv/MPVPlayer.kt").readText()
        val commands =
            Regex("""permanentAvailableCommands: Commands =[\s\S]*?\.build\(\)""").find(source)
        assertTrue("permanentAvailableCommands not found", commands != null)
        assertTrue(
            "MPVPlayer must offer COMMAND_PREPARE (#474)",
            commands!!.value.contains("COMMAND_PREPARE,"),
        )
        val prepare = Regex("""override fun prepare\(\) \{[\s\S]*?\n    }""").find(source)
        assertTrue("MPVPlayer.prepare() not found", prepare != null)
        assertTrue(
            "MPVPlayer.prepare() must reload after an error (#474)",
            prepare!!.value.contains("errorRecovery.onPrepare("),
        )
    }

    @Test
    fun `a new playlist after a lost connection gives a failed load its plain error`() {
        recovery.onError(0, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        recovery.onPrepare(itemCount = 1)
        recovery.forget()
        recovery.onPrepare(itemCount = 1)

        assertEquals(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, recovery.loadErrorCode(null))
    }
}
