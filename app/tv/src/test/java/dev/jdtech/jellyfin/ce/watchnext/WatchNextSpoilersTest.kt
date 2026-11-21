package dev.jdtech.jellyfin.ce.watchnext

import android.app.Application
import android.net.Uri
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #618: the launcher shows Watch Next outside the app, so "Hide spoilers" reaches it too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WatchNextSpoilersTest {
    private val episode =
        dummyEpisode.copy(
            played = false,
            runtimeTicks = 12_000_000_000,
            images =
                FindroidImages(
                    primary = Uri.parse("https://server/still"),
                    showBackdrop = Uri.parse("https://server/show-backdrop"),
                ),
        )

    @Test
    fun `a hidden episode goes without its still, title and overview`() {
        val entry = episode.toWatchNextEntry(WatchNextKind.NEXT, hideSpoilers = true)!!
        assertNull(entry.episodeTitle)
        assertEquals("", entry.description)
        assertEquals(true, entry.posterUri?.startsWith("https://server/show-backdrop"))
        assertEquals(episode.indexNumber, entry.episodeNumber)
    }

    @Test
    fun `a watched episode and the setting off keep everything`() {
        val off = episode.toWatchNextEntry(WatchNextKind.NEXT, hideSpoilers = false)!!
        assertEquals(episode.name, off.episodeTitle)
        assertEquals(true, off.posterUri?.startsWith("https://server/still"))
        val watched =
            episode.copy(played = true).toWatchNextEntry(WatchNextKind.NEXT, hideSpoilers = true)!!
        assertEquals(episode.name, watched.episodeTitle)
    }
}
