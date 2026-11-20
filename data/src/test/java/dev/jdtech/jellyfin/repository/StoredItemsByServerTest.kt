package dev.jdtech.jellyfin.repository

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabase
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidMovieDto
import dev.jdtech.jellyfin.models.FindroidSeasonDto
import dev.jdtech.jellyfin.models.FindroidShowDto
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.FindroidUserDataDto
import dev.jdtech.jellyfin.models.getLocalSourcesForItems
import dev.jdtech.jellyfin.player.DeviceProfileBuilder
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CE (#718): the copies in Room serve the server that stored them, not another server with the same
 * item ids (Jellyfin derives ids from the media's paths, so a server reinstalled over the same
 * folders has them too). The server here always fails: the API client has no address, so every
 * getter falls back to Room or fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StoredItemsByServerTest {
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val movieId = UUID.fromString("00000000-0000-0000-0000-000000000040")
    private val showId = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000021")
    private val episodeId = UUID.fromString("00000000-0000-0000-0000-000000000022")
    private val otherSeasonId = UUID.fromString("00000000-0000-0000-0000-000000000031")
    private val otherEpisodeId = UUID.fromString("00000000-0000-0000-0000-000000000032")

    private val context: Context = RuntimeEnvironment.getApplication()
    private val room =
        Room.inMemoryDatabaseBuilder(context, ServerDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
    private val database: ServerDatabaseDao = room.getServerDatabaseDao()
    private val preferences =
        AppPreferences(context.getSharedPreferences("stored-by-server", Context.MODE_PRIVATE))

    private val repository: JellyfinRepository by lazy {
        // The SDK names the device after it; Robolectric has none.
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "test")
        val api = JellyfinApi(context).apply { userId = this@StoredItemsByServerTest.userId }
        JellyfinRepositoryImpl(context, api, database, preferences, DeviceProfileBuilder(context))
    }

    @After fun close() = room.close()

    private fun current(serverId: String) =
        preferences.setValue(preferences.currentServer, serverId)

    private suspend fun storeMovie(serverId: String) {
        database.insertMovie(
            FindroidMovieDto(
                id = movieId,
                serverId = serverId,
                name = "Movie",
                originalTitle = null,
                overview = "",
                runtimeTicks = 0L,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                status = "",
                productionYear = null,
                endDate = null,
                chapters = null,
            )
        )
        database.insertSource(source(movieId))
    }

    /** What the downloader stores for an episode: the show and season as it finds them. */
    private suspend fun storeEpisode(serverId: String, season: UUID, episode: UUID, index: Int) {
        database.insertShow(
            FindroidShowDto(
                id = showId,
                serverId = serverId,
                name = "Bluey",
                originalTitle = null,
                overview = "",
                runtimeTicks = 0L,
                communityRating = null,
                officialRating = null,
                status = "Continuing",
                productionYear = 2018,
                endDate = null,
            )
        )
        database.insertSeason(
            FindroidSeasonDto(
                id = season,
                seriesId = showId,
                name = "Season $index",
                seriesName = "Bluey",
                overview = "",
                indexNumber = index,
            )
        )
        database.insertEpisode(
            FindroidEpisodeDto(
                id = episode,
                serverId = serverId,
                seasonId = season,
                seriesId = showId,
                name = "Episode $index",
                seriesName = "Bluey",
                overview = "",
                indexNumber = 1,
                indexNumberEnd = null,
                parentIndexNumber = index,
                runtimeTicks = 4_200_000_000L,
                premiereDate = null,
                communityRating = null,
                chapters = null,
            )
        )
        database.insertSource(source(episode))
    }

    private fun source(itemId: UUID) =
        FindroidSourceDto(
            id = "source-$itemId",
            itemId = itemId,
            name = "Original",
            type = FindroidSourceType.LOCAL,
            path = "/storage/downloads/$itemId.mkv",
        )

    private suspend fun fails(get: suspend () -> Any?): Boolean = runCatching { get() }.isFailure

    @Test
    fun `a movie stored for another server is not the current server's`() = runBlocking {
        storeMovie(SERVER_A)

        current(SERVER_B)
        assertTrue(fails { repository.getMovie(movieId) }, "getMovie on B")
        assertTrue(fails { repository.getItem(movieId) }, "getItem on B")

        current(SERVER_A)
        assertEquals("Movie", repository.getMovie(movieId).name)
        assertEquals(movieId, repository.getItem(movieId)?.id)
    }

    @Test
    fun `an episode, its season and its show stored for another server are not the current server's`() =
        runBlocking {
            storeEpisode(SERVER_A, seasonId, episodeId, index = 1)

            current(SERVER_B)
            assertTrue(fails { repository.getEpisode(episodeId) }, "getEpisode on B")
            assertTrue(fails { repository.getSeason(seasonId) }, "getSeason on B")
            assertTrue(fails { repository.getShow(showId) }, "getShow on B")
            for (id in listOf(episodeId, seasonId, showId)) {
                assertTrue(fails { repository.getItem(id) }, "getItem($id) on B")
            }

            current(SERVER_A)
            assertEquals("Episode 1", repository.getEpisode(episodeId).name)
            assertEquals("Season 1", repository.getSeason(seasonId).name)
            assertEquals("Bluey", repository.getShow(showId).name)
            assertEquals(episodeId, repository.getItem(episodeId)?.id)
        }

    @Test
    fun `the stored lists of a show are the current server's`() = runBlocking {
        storeEpisode(SERVER_A, seasonId, episodeId, index = 1)

        current(SERVER_B)
        assertEquals(emptyList(), repository.getSeasons(showId, offline = true).map { it.id })
        assertEquals(
            emptyList(),
            repository.getEpisodes(showId, seasonId, offline = true).map { it.id },
        )
        assertEquals(
            emptyList(),
            repository.getEpisodes(showId, null, offline = true).map { it.id },
        )

        current(SERVER_A)
        assertEquals(listOf(seasonId), repository.getSeasons(showId, offline = true).map { it.id })
        assertEquals(
            listOf(episodeId),
            repository.getEpisodes(showId, seasonId, offline = true).map { it.id },
        )
    }

    @Test
    fun `a show both servers downloaded episodes of is listed for each with its own episodes`() =
        runBlocking {
            // A stored the show first, so its row names A; B's episode is in another season.
            storeEpisode(SERVER_A, seasonId, episodeId, index = 1)
            storeEpisode(SERVER_B, otherSeasonId, otherEpisodeId, index = 2)

            current(SERVER_B)
            assertEquals(listOf(showId), repository.getDownloads().map { it.id })
            assertEquals("Bluey", repository.getShow(showId).name)
            assertEquals(
                listOf(otherSeasonId),
                repository.getSeasons(showId, offline = true).map { it.id },
            )
            assertEquals(
                listOf(otherEpisodeId),
                repository.getEpisodes(showId, null, offline = true).map { it.id },
            )
            assertTrue(fails { repository.getSeason(seasonId) }, "A's season on B")

            current(SERVER_A)
            assertEquals(listOf(showId), repository.getDownloads().map { it.id })
            assertEquals(
                listOf(seasonId),
                repository.getSeasons(showId, offline = true).map { it.id },
            )
            assertEquals(
                listOf(episodeId),
                repository.getEpisodes(showId, null, offline = true).map { it.id },
            )
        }

    @Test
    fun `playback asks the current server rather than play another server's download`() =
        runBlocking {
            storeMovie(SERVER_A)

            current(SERVER_B)
            // Asking the server is what fails here; A's file must not be the answer.
            val onB = runCatching { repository.getPlaybackSources(movieId, null) }
            assertTrue(onB.isFailure, "Got ${onB.getOrNull()?.map { it.path }}")

            current(SERVER_A)
            assertEquals(
                listOf("/storage/downloads/$movieId.mkv"),
                repository.getPlaybackSources(movieId, null).map { it.path },
            )
        }

    @Test
    fun `downloaded marks, episode counts and smart downloads see only the current server's`() =
        runBlocking {
            storeMovie(SERVER_A)
            storeEpisode(SERVER_A, seasonId, episodeId, index = 1)
            val ids = listOf(movieId, episodeId)

            assertEquals(emptyMap(), database.getLocalSourcesForItems(ids, SERVER_B))
            assertEquals(ids.toSet(), database.getLocalSourcesForItems(ids, SERVER_A).keys)
            assertEquals(emptyList(), database.getSeasonEpisodeCounts(showId, SERVER_B))
            assertEquals(1, database.getSeasonEpisodeCounts(showId, SERVER_A).single().downloaded)
            assertEquals(emptyList(), database.getSeriesIdsWithCompletedDownloads(SERVER_B))
            assertEquals(listOf(showId), database.getSeriesIdsWithCompletedDownloads(SERVER_A))
            assertEquals(emptyList(), database.getSourcesOfServer(movieId, SERVER_B))
            assertEquals(emptyList(), database.getCompletedSources(episodeId, SERVER_B))
        }

    @Test
    fun `progress still to be sent to a server is sent after another server took the item over`() =
        runBlocking {
            // A's user watched the movie offline; then B's download of the same id took the row.
            storeMovie(SERVER_B)
            database.insertUserData(
                FindroidUserDataDto(
                    userId = userId,
                    itemId = movieId,
                    played = false,
                    favorite = false,
                    playbackPositionTicks = 1_200_000_000L,
                    toBeSynced = true,
                )
            )

            assertEquals(
                listOf(movieId),
                database.getAllUserDataToBeSynced(SERVER_A, userId).map { it.itemId },
            )
        }

    private companion object {
        const val SERVER_A = "server-a"
        const val SERVER_B = "server-b"
    }
}
