package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidSeason
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
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test

/**
 * CE (#657): the items next to an item reach the player's playlist next to that item, however the
 * user moved through the queue while they were being fetched. A slow server is a request the test
 * holds ([slow]) until it [release]s it.
 */
class NeighbourLoaderTest {
    private val seriesId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()
    private val episodes = List(6) { episode(it + 1) }
    private val e1 = episodes[0].id
    private val e2 = episodes[1].id
    private val e3 = episodes[2].id
    private val e4 = episodes[3].id
    private val e5 = episodes[4].id
    private val e6 = episodes[5].id

    /** The next season, there only for the tests that set [seasons]. */
    private val season2Id = UUID.randomUUID()
    private val season2 = List(3) { episode(it + 1, season2Id, seasonNumber = 2) }
    private var seasons: List<FindroidSeason> = emptyList()

    /** Requests to hold, as the method name and the item id it asks about. */
    private val slow = mutableListOf<Pair<String, UUID>>()

    /** The held requests, oldest first, by the item id they ask about. */
    private val held = mutableListOf<Pair<UUID, () -> Unit>>()

    private val manager = PlaylistManager(fakeRepository())
    private val loader = NeighbourLoader(manager)

    @Test
    fun `a next item that arrives after Previous goes after the item it was fetched for`() =
        runBlocking {
            val playlist = startAtThirdEpisode()
            moveTo(playlist, e4)

            slow += "getPlaybackSources" to e5
            val load = launch { loader.load(e4, playlist) }
            awaitHeld(e5)
            // Previous twice while e4's next item is on its way.
            moveTo(playlist, e3)
            moveTo(playlist, e2)
            release(e5)
            load.join()

            assertEquals(listOf(e2, e3, e4, e5), playlist.ids)
        }

    @Test
    fun `a previous item that arrives after Next goes before the item it was fetched for`() =
        runBlocking {
            val playlist = startAtThirdEpisode()
            moveTo(playlist, e2)

            slow += "getPlaybackSources" to e1
            val load = launch { loader.load(e2, playlist) }
            awaitHeld(e1)
            moveTo(playlist, e3)
            release(e1)
            load.join()

            assertEquals(listOf(e1, e2, e3, e4), playlist.ids)
        }

    @Test
    fun `a neighbour fetched twice is queued once`() = runBlocking {
        val playlist = startAtThirdEpisode()
        moveTo(playlist, e4)

        slow += "getPlaybackSources" to e5
        val first = launch { loader.load(e4, playlist) }
        awaitHeld(e5)
        // Back and forth while e5 is on its way: e4 asks for it again.
        moveTo(playlist, e3)
        loader.load(e3, playlist)
        moveTo(playlist, e4)
        slow += "getPlaybackSources" to e5
        val second = launch { loader.load(e4, playlist) }
        awaitHeld(e5, count = 2)
        release(e5)
        release(e5)
        first.join()
        second.join()

        assertEquals(listOf(e2, e3, e4, e5), playlist.ids)
    }

    @Test
    fun `a neighbour of an item that left the playlist is dropped and fetched again later`() =
        runBlocking {
            val playlist = startAtThirdEpisode()
            moveTo(playlist, e4)

            slow += "getPlaybackSources" to e5
            val load = launch { loader.load(e4, playlist) }
            awaitHeld(e5)
            // A jump in the queue (the TV's queue row) replaces the playlist.
            manager.jumpTo(e1)
            playlist.replace(e1)
            manager.setCurrentMediaItemIndex(e1)
            release(e5)
            load.join()
            assertEquals(listOf(e1), playlist.ids)

            // Walking up to e4 again queues e5 after it.
            loader.load(e1, playlist)
            for (id in listOf(e2, e3, e4)) {
                moveTo(playlist, id)
                loader.load(id, playlist)
            }
            assertEquals(listOf(e1, e2, e3, e4, e5), playlist.ids)
        }

    @Test
    fun `the neighbours of an item are its own, not the current item's`() = runBlocking {
        manager.getInitialItem(e3, BaseItemKind.EPISODE)
        manager.setCurrentMediaItemIndex(e1)

        assertEquals(e2, manager.getPreviousPlayerItem(e3)?.itemId)
        assertEquals(e4, manager.getNextPlayerItem(e3)?.itemId)
    }

    @Test
    fun `in a shuffled series a late next item goes after the item it was fetched for`() =
        runBlocking {
            manager.random = Random(657)
            val first = manager.getInitialItem(seriesId, BaseItemKind.SERIES, shuffle = true)!!
            val order = listOf(first.itemId) + manager.upcomingItems().map { it.id }
            val playlist = FakePlaylist(first.itemId)
            manager.setCurrentMediaItemIndex(first.itemId)
            loader.load(first.itemId, playlist)
            assertEquals(order.take(2), playlist.ids)

            moveTo(playlist, order[1])
            // A light queue fetches the episode in full first: that is the slow request.
            slow += "getEpisode" to order[2]
            val load = launch { loader.load(order[1], playlist) }
            awaitHeld(order[2])
            moveTo(playlist, order[0])
            release(order[2])
            load.join()

            assertEquals(order.take(3), playlist.ids)
        }

    @Test
    fun `two overlapping lookups at the season's last episode append the next season once`() =
        runBlocking {
            seasons = listOf(season(seasonId, 1), season(season2Id, 2))
            manager.getInitialItem(e6, BaseItemKind.EPISODE)
            manager.loadQueue()

            // The first lookup waits on the server for the seasons; the user leaves e6 and comes
            // back, and e6 asks again.
            slow += "getSeasons" to seriesId
            val first = launch { manager.getNextPlayerItem(e6) }
            awaitHeld(seriesId)
            val second = launch { manager.getNextPlayerItem(e6) }
            repeat(10) { yield() }
            release(seriesId)
            first.join()
            second.join()

            manager.setCurrentMediaItemIndex(e6)
            assertEquals(season2.map { it.id }, manager.upcomingItems().map { it.id })
        }

    /** e3 playing, with e2 and e4 queued around it. */
    private suspend fun startAtThirdEpisode(): FakePlaylist {
        manager.getInitialItem(e3, BaseItemKind.EPISODE)
        val playlist = FakePlaylist(e3)
        manager.setCurrentMediaItemIndex(e3)
        loader.load(e3, playlist)
        assertEquals(listOf(e2, e3, e4), playlist.ids)
        return playlist
    }

    /** What the player view model does when the player moves to [id]. */
    private fun moveTo(playlist: FakePlaylist, id: UUID) {
        playlist.current = playlist.ids.indexOf(id)
        manager.setCurrentMediaItemIndex(id)
    }

    private suspend fun awaitHeld(id: UUID, count: Int = 1) {
        repeat(1000) {
            if (held.count { it.first == id } >= count) return
            yield()
        }
        error("No request for $id was held")
    }

    private fun release(id: UUID) {
        val request = held.first { it.first == id }
        held.remove(request)
        request.second()
    }

    /** The player's playlist, by item id, and the index it plays. */
    private class FakePlaylist(vararg start: UUID) : NeighbourLoader.Playlist {
        val ids = start.toMutableList()
        var current = 0

        override fun itemIds(): List<UUID> = ids.toList()

        override fun insert(index: Int, item: PlayerItem) {
            ids.add(index, item.itemId)
            if (index <= current) current++
        }

        fun replace(id: UUID) {
            ids.clear()
            ids.add(id)
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
                    // A whole series (shuffle) or the season.
                    "getEpisodes" -> if (args[1] == season2Id) season2 else episodes
                    "getSeasons" -> seasons
                    "getPlaybackSources" -> listOf(source())
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

    private fun episode(number: Int, season: UUID = seasonId, seasonNumber: Int = 1) =
        FindroidEpisode(
            id = UUID.randomUUID(),
            name = "Episode $number",
            originalTitle = null,
            overview = "",
            indexNumber = number,
            indexNumberEnd = null,
            parentIndexNumber = seasonNumber,
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
            seasonId = season,
            seasonName = "Season $seasonNumber",
            communityRating = null,
            people = emptyList(),
            missing = false,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun season(id: UUID, number: Int) =
        FindroidSeason(
            id = id,
            name = "Season $number",
            seriesId = seriesId,
            seriesName = "Show",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            indexNumber = number,
            episodes = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            unplayedItemCount = null,
            images = FindroidImages(),
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
