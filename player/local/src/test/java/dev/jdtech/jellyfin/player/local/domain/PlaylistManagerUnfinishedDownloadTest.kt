package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** An unfinished download's partial file is never what plays (#454). */
class PlaylistManagerUnfinishedDownloadTest {
    private val episode = episode()

    @Test
    fun `an unfinished download gives way to the server source`() {
        val manager = PlaylistManager(fakeRepository(listOf(partial(), remote())))

        val item = runBlocking { manager.getInitialItem(episode.id, BaseItemKind.EPISODE) }

        assertEquals(REMOTE_PATH, item?.mediaSourceUri)
    }

    @Test
    fun `an unfinished download alone is not played and gives no raw error text`() {
        val manager = PlaylistManager(fakeRepository(listOf(partial())))

        try {
            val item = runBlocking { manager.getInitialItem(episode.id, BaseItemKind.EPISODE) }
            fail("Expected no playable source, got ${item?.mediaSourceUri}")
        } catch (e: NoSuchElementException) {
            // PlayerViewModel toasts the message when there is one; the player's own error
            // is the one the user should see.
            assertNull(e.message)
        }
    }

    private fun fakeRepository(sources: List<FindroidSource>) =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getEpisode" -> episode
                "getPlaybackSources" -> sources
                else -> error("Unexpected call to ${method.name}")
            }
        } as JellyfinRepository

    private fun partial() =
        FindroidSource(
            id = "source",
            name = "Source",
            type = FindroidSourceType.LOCAL,
            path = "/downloads/Show/S01/S01E01 - Episode.ts.download",
            size = 0L,
            mediaStreams = emptyList(),
            downloadId = 1L,
        )

    private fun remote() =
        FindroidSource(
            id = "source",
            name = "Source",
            type = FindroidSourceType.REMOTE,
            path = REMOTE_PATH,
            size = 0L,
            mediaStreams = emptyList(),
        )

    private fun episode() =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode 1",
            originalTitle = null,
            overview = "",
            indexNumber = 1,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = UUID.randomUUID(),
            seriesName = "Show",
            seasonId = UUID.randomUUID(),
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private companion object {
        const val REMOTE_PATH = "http://server/video"
    }
}
