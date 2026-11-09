package dev.jdtech.jellyfin.player.local

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level invariants for the player that a compile cannot catch and a device test is easy to
 * skip. Each one names the regression it guards. Gradle runs unit tests from the module directory,
 * so the sources are read relative to it.
 */
class PlayerInvariantsTest {

    private fun source(path: String): String {
        val file = File("src/main/java/dev/jdtech/jellyfin/player/local/$path")
        assertTrue("missing ${file.absolutePath}; run from the player/local module", file.isFile)
        return file.readText()
    }

    @Test
    fun `ExoPlayer keeps its default frame rate strategy`() {
        // #338: forcing VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF on phones (the TV-only "match
        // refresh rate" setting defaults to off) removed the surface frame rate hint, so 120Hz
        // panels stayed at 120Hz for the whole playback. ExoPlayer's default, ONLY_IF_SEAMLESS,
        // is what upstream Findroid runs with and what lets the OS drop to 48Hz for 24fps.
        val viewModel = source("presentation/PlayerViewModel.kt")

        assertFalse(
            "PlayerViewModel must not call setVideoChangeFrameRateStrategy (#338)",
            viewModel.contains("setVideoChangeFrameRateStrategy"),
        )
        assertFalse(
            "PlayerViewModel must not use VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF (#338)",
            viewModel.contains("VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF"),
        )
    }

    @Test
    fun `mpv hints the surface with the video frame rate on every device`() {
        // mpv has no frame rate hint of its own (nothing in its Android sources calls
        // Surface.setFrameRate), so MPVPlayer sends one: seamless-only by default, like ExoPlayer,
        // with fixed-source compatibility so the OS picks an exact match or a multiple (#338).
        val mpvPlayer = source("mpv/MPVPlayer.kt")

        assertTrue(
            "MPVPlayer must request seamless switches when the TV setting is off (#338)",
            mpvPlayer.contains("Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS"),
        )
        assertTrue(
            "MPVPlayer must pass FRAME_RATE_COMPATIBILITY_FIXED_SOURCE like ExoPlayer (#338)",
            mpvPlayer.contains("Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE"),
        )
        assertFalse(
            "MPVPlayer must not skip the hint when the TV setting is off (#338)",
            Regex("""if \(!matchRefreshRate\s*\|\|""").containsMatchIn(mpvPlayer),
        )
    }
}
