package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.settings.domain.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the mpv options [mpvSubtitleOptions] builds from the subtitle settings. */
class MpvSubtitleOptionsTest {

    private fun options(
        overrideEmbeddedStyles: Boolean = false,
        foregroundColor: String? = Constants.SubtitleStyle.DEFAULT_FG_COLOR,
        backgroundColor: String? = Constants.SubtitleStyle.DEFAULT_BG_COLOR,
        edgeColor: String? = Constants.SubtitleStyle.DEFAULT_EDGE_COLOR,
        edgeType: String? = Constants.SubtitleStyle.EDGE_OUTLINE,
        fontFamily: String? = Constants.SubtitleStyle.FONT_DEFAULT,
        fontScalePercent: Int = 100,
    ) =
        mpvSubtitleOptions(
            overrideEmbeddedStyles,
            foregroundColor,
            backgroundColor,
            edgeColor,
            edgeType,
            fontFamily,
            fontScalePercent,
        )

    @Test
    fun `override off leaves ASS styles to the track`() {
        assertEquals("scale", options(overrideEmbeddedStyles = false)["sub-ass-override"])
    }

    @Test
    fun `override on forces the app style onto ASS tracks`() {
        assertEquals("force", options(overrideEmbeddedStyles = true)["sub-ass-override"])
    }

    @Test
    fun `the default style is white text with a black outline in a translucent box`() {
        val options = options()

        assertEquals("sans-serif", options["sub-font"])
        assertEquals("47", options["sub-font-size"])
        assertEquals("#FFFFFFFF", options["sub-color"])
        assertEquals("background-box", options["sub-border-style"])
        assertEquals("#80000000", options["sub-back-color"])
        assertEquals("#FF000000", options["sub-outline-color"])
        assertEquals("1.65", options["sub-outline-size"])
        assertEquals("0", options["sub-shadow-offset"])
    }

    @Test
    fun `font scale scales the font size and is clamped`() {
        assertEquals("71", options(fontScalePercent = 150)["sub-font-size"])
        assertEquals("94", options(fontScalePercent = 500)["sub-font-size"])
        assertEquals("24", options(fontScalePercent = 10)["sub-font-size"])
    }

    @Test
    fun `a transparent background draws no box`() {
        val options = options(backgroundColor = "#00000000")

        assertEquals("outline-and-shadow", options["sub-border-style"])
        assertEquals("#00000000", options["sub-back-color"])
    }

    @Test
    fun `a drop shadow without a box is drawn in the edge colour`() {
        val options =
            options(
                backgroundColor = "#00000000",
                edgeColor = "#FFFF0000",
                edgeType = Constants.SubtitleStyle.EDGE_DROP_SHADOW,
            )

        assertEquals("#FFFF0000", options["sub-back-color"])
        assertEquals("2", options["sub-shadow-offset"])
        assertEquals("0", options["sub-outline-size"])
    }

    @Test
    fun `a drop shadow gives way to the background box`() {
        val options = options(edgeType = Constants.SubtitleStyle.EDGE_DROP_SHADOW)

        assertEquals("#80000000", options["sub-back-color"])
        assertEquals("0", options["sub-shadow-offset"])
    }

    @Test
    fun `no edge draws neither outline nor shadow`() {
        val options = options(edgeType = Constants.SubtitleStyle.EDGE_NONE)

        assertEquals("0", options["sub-outline-size"])
        assertEquals("0", options["sub-shadow-offset"])
    }

    @Test
    fun `fonts map onto the fontconfig families`() {
        assertEquals("serif", options(fontFamily = Constants.SubtitleStyle.FONT_SERIF)["sub-font"])
        assertEquals(
            "monospace",
            options(fontFamily = Constants.SubtitleStyle.FONT_MONOSPACE)["sub-font"],
        )
        assertEquals(
            "sans-serif",
            options(fontFamily = Constants.SubtitleStyle.FONT_SANS_SERIF)["sub-font"],
        )
    }

    @Test
    fun `colours are normalised and bad ones fall back`() {
        assertEquals("#FFFFFF00", mpvColor("#ffff00", "#FF000000"))
        assertEquals("#80FFFFFF", mpvColor("#80ffffff", "#FF000000"))
        assertEquals("#FF000000", mpvColor("yellow", "#FF000000"))
        assertEquals("#FF000000", mpvColor("#12345", "#FF000000"))
        assertEquals("#FF000000", mpvColor(null, "#FF000000"))
    }
}
