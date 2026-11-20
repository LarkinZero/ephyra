package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.resume
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test

/**
 * CE (#721): a quality or audio switch rebuilds the queue with new streams while the neighbour
 * lookup ([NeighbourLoader], #657) can add items to it. Every item ends up in the rebuilt queue, in
 * the playlist's order, so the player's index still points at the item that plays. A slow server is
 * a request the test holds ([slow]) until it [release]s it, as in [NeighbourLoaderTest].
 */
class QueueRebuildTest {
    private val seriesId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()
    private val episodes = List(5) { episode(it + 1) }
    private val e1 = episodes[0].id
    private val e2 = episodes[1].id
    private val e3 = episodes[2].id
    private val e4 = episodes[3].id

    /** Every stream request, by item id, to tell the rebuilt items from the first ones. */
    private val streamRequests = mutableListOf<UUID>()

    /** Requests to hold, as the method name and the item id it asks about. */
    private val slow = mutableListOf<Pair<String, UUID>>()

    /** The held requests, oldest first, by the item id they ask about. */
    private val held = mutableListOf<Pair<UUID, () -> Unit>>()

    private val manager = PlaylistManager(fakeRepository())
    private val loader = NeighbourLoader(manager)

    @Test
    fun `neighbours that arrive while the queue is rebuilt are in it, around the playing item`() =
        runBlocking {
            // The player has just opened e3: its neighbours are on their way.
            val playlist = open(e3)
            slow += "getPlaybackSources" to e2
            val neighbours = launch { loader.load(e3, playlist) }
            awaitHeld(e2)

            // The user switches quality: e3's new stream waits on the server too.
            slow += "getPlaybackSources" to e3
            val rebuild = async { rebuildQueue(playlist::items) { manager.recreatePlayerItem(it) } }
            awaitHeld(e3)

            // The neighbours come in first: e2 before e3, then e4 after it.
            release(e2)
            neighbours.join()
            assertEquals(listOf(e2, e3, e4), playlist.ids, "the playlist before the reload")
            release(e3)
            val rebuilt = rebuild.await()

            assertEquals(playlist.ids, rebuilt.map { it.itemId }, "the rebuilt queue")
            assertEquals(
                e3,
                rebuilt[playlist.current].itemId,
                "the item at the player's index after the reload",
            )
            assertEquals(
                // e3 opens, e2 is fetched, the rebuild asks for e3, e4 is fetched, then the
                // rebuild asks for the two that came in.
                listOf(e3, e2, e3, e4, e2, e4),
                streamRequests,
                "every item gets a stream under the new setting",
            )
        }

    @Test
    fun `a jump in the queue while the queue is rebuilt is not undone`() = runBlocking {
        val playlist = open(e3)
        loader.load(e3, playlist)
        assertEquals(listOf(e2, e3, e4), playlist.ids)

        slow += "getPlaybackSources" to e2
        val rebuild = async { rebuildQueue(playlist::items) { manager.recreatePlayerItem(it) } }
        awaitHeld(e2)
        // A jump in the queue (the TV's queue row) replaces the playlist.
        playlist.replace(manager.jumpTo(e1)!!)
        manager.setCurrentMediaItemIndex(e1)
        release(e2)

        assertEquals(listOf(e1), rebuild.await().map { it.itemId }, "the rebuilt queue")
    }

    /** The player opened at [id], before its neighbours are queued. */
    private suspend fun open(id: UUID): FakePlaylist {
        val first = manager.getInitialItem(id, BaseItemKind.EPISODE)!!
        manager.setCurrentMediaItemIndex(id)
        return FakePlaylist(first)
    }

    private suspend fun awaitHeld(id: UUID) {
        repeat(1000) {
            if (held.any { it.first == id }) return
            yield()
        }
        error("No request for $id was held")
    }

    private fun release(id: UUID) {
        val request = held.first { it.first == id }
        held.remove(request)
        request.second()
    }

    /**
     * The view model's [PlayerItem]s and the player's playlist, which [insert] adds to together,
     * with the index the player plays.
     */
    private class FakePlaylist(start: PlayerItem) : NeighbourLoader.Playlist {
        val items = mutableListOf(start)
        var current = 0
        val ids
            get() = items.map { it.itemId }

        override fun itemIds(): List<UUID> = ids

        override fun insert(index: Int, item: PlayerItem) {
            items.add(index, item)
            if (index <= current) current++
        }

        fun replace(item: PlayerItem) {
            items.clear()
            items.add(item)
            current = 0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fakeRepository() =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, args ->
            val answer: Any =
                when (method.name) {
                    "getEpisode" -> episodes.first { it.id == args[0] }
                    "getEpisodes" -> episodes
                    "getPlaybackSources" -> {
                        streamRequests += args[0] as UUID
                        listOf(source())
                    }
                    else -> error("Unexpected call to ${method.name}")
                }
            val request = method.name to args?.getOrNull(0)
            if (request in slow) {
                slow.remove(request)
                val continuation = args.last() as Continuation<Any?>
                held += (args[0] as UUID) to { continuation.intercepted().resume(answer) }
                COROUTINE_SUSPENDED
            } else {
                answer
            }
        } as JellyfinRepository

    private fun episode(number: Int) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
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
            seriesId = seriesId,
            seriesName = "Show",
            seasonId = seasonId,
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun source() =
        FindroidSource(
            id = "source",
            name = "Source",
            type = FindroidSourceType.REMOTE,
            path = "http://server/video",
            size = 0L,
            mediaStreams = emptyList(),
        )
}
