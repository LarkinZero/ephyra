package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the mpv options [mpvSubtitleOptions] builds from the subtitle settings. */
class MpvSubtitleOptionsTest {

    @Test
    fun `override off leaves ASS styles to the track`() {
        assertEquals("scale", mpvSubtitleOptions(overrideEmbeddedStyles = false)["sub-ass-override"])
    }

    @Test
    fun `override on forces the app style onto ASS tracks`() {
        assertEquals("force", mpvSubtitleOptions(overrideEmbeddedStyles = true)["sub-ass-override"])
    }
}
