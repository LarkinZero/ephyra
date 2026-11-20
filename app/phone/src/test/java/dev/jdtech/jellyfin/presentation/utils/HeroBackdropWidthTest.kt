package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Test

/** #622: which windows show the backdrop at 16:9 beside the text, and how wide. */
class HeroBackdropWidthTest {
    private fun backdrop(width: Dp, windowHeight: Dp, beside: Boolean = true): Dp =
        Layout.heroBackdropWidth(width, Layout.heroHeight(width, windowHeight), beside)

    @Test
    fun `landscape tablets keep 16 by 9 at the end side`() {
        // 10.5 inch: hero 360 dp, picture 640 dp. 8 inch and the unfolded fold: hero 320 dp.
        assertEquals(640f, backdrop(1280.dp, 800.dp).value, absoluteTolerance = 0.5f)
        assertEquals(568.9f, backdrop(1024.dp, 640.dp).value, absoluteTolerance = 0.5f)
        assertEquals(568.9f, backdrop(841.dp, 701.dp).value, absoluteTolerance = 0.5f)
    }

    @Test
    fun `portrait windows and panes keep the full width`() {
        listOf(
                800.dp to 1280.dp, // 10.5 inch portrait
                640.dp to 1024.dp, // 8 inch portrait
                701.dp to 841.dp, // fold portrait
                600.dp to 800.dp, // split-screen half
            )
            .forEach { (width, height) ->
                assertEquals(width, backdrop(width, height), message = "$width x $height")
            }
        // The Season list-detail pane at 1280 x 800 is 799 dp wide.
        assertEquals(799.dp, backdrop(799.dp, 800.dp))
    }

    @Test
    fun `with the setting off every window keeps the full width`() {
        listOf(1280.dp to 800.dp, 1024.dp to 640.dp, 841.dp to 701.dp).forEach { (width, height) ->
            assertEquals(
                width,
                backdrop(width, height, beside = false),
                message = "$width x $height",
            )
        }
    }
}
