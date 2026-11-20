package dev.jdtech.jellyfin.utils

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dev.jdtech.jellyfin.database.ServerDatabase
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidMovieDto
import dev.jdtech.jellyfin.models.FindroidSeasonDto
import dev.jdtech.jellyfin.models.FindroidShowDto
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.FindroidUserDataDto
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.download.MediaDownloadEngine
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import javax.inject.Provider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment

/**
 * CE (#718): Room keeps one copy per item id. A download from one server of an id another server
 * stored replaces that copy whole (rows and files), so nothing is left behind under the other
 * server's name; a download of the server's own copy, or of an item nobody stored, touches nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OtherServersCopyTest {
    private val userA = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val userB = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val movieId = UUID.fromString("00000000-0000-0000-0000-000000000040")
    private val showId = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000021")
    private val episodeId = UUID.fromString("00000000-0000-0000-0000-000000000022")
    private val otherEpisodeId = UUID.fromString("00000000-0000-0000-0000-000000000023")

    private val context: Context = RuntimeEnvironment.getApplication()
    private val room =
        Room.inMemoryDatabaseBuilder(context, ServerDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
    private val database: ServerDatabaseDao = room.getServerDatabaseDao()
    private val preferences =
        AppPreferences(context.getSharedPreferences("other-servers-copy", Context.MODE_PRIVATE))
    private val dir = File(context.cacheDir, "other-servers-copy").apply { mkdirs() }

    /** What the server answers; anything else fails, as with the server gone. */
    private var server: (String) -> Any? = { name -> throw IllegalStateException(name) }

    private val downloader: DownloaderImpl by lazy {
        val repository =
            Proxy.newProxyInstance(
                JellyfinRepository::class.java.classLoader,
                arrayOf(JellyfinRepository::class.java),
            ) { _, method, _ ->
                server("JellyfinRepository.${method.name}")
            } as JellyfinRepository
        DownloaderImpl(
            context,
            database,
            Provider { repository },
            preferences,
            Provider { error("No WorkManager in this test") },
            MediaDownloadEngine(OkHttpClient(), Online),
        )
    }

    @After
    fun close() {
        room.close()
        dir.deleteRecursively()
    }

    private fun current(serverId: String) =
        preferences.setValue(preferences.currentServer, serverId)

    private fun file(itemId: UUID) = File(dir, "$itemId.mkv").apply { writeText("video") }

    private suspend fun storeSource(itemId: UUID): File {
        val file = file(itemId)
        database.insertSource(
            FindroidSourceDto(
                id = "source-$itemId",
                itemId = itemId,
                name = "Original",
                type = FindroidSourceType.LOCAL,
                path = file.absolutePath,
            )
        )
        return file
    }

    private suspend fun storeMovie(serverId: String): File {
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
        return storeSource(movieId)
    }

    private suspend fun storeEpisode(serverId: String, id: UUID, index: Int): File {
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
                id = seasonId,
                seriesId = showId,
                name = "Season 1",
                seriesName = "Bluey",
                overview = "",
                indexNumber = 1,
            )
        )
        database.insertEpisode(
            FindroidEpisodeDto(
                id = id,
                serverId = serverId,
                seasonId = seasonId,
                seriesId = showId,
                name = "Episode $index",
                seriesName = "Bluey",
                overview = "",
                indexNumber = index,
                indexNumberEnd = null,
                parentIndexNumber = 1,
                runtimeTicks = 0L,
                premiereDate = null,
                communityRating = null,
                chapters = null,
            )
        )
        return storeSource(id)
    }

    private suspend fun userData(userId: UUID, itemId: UUID, toBeSynced: Boolean) =
        database.insertUserData(
            FindroidUserDataDto(
                userId = userId,
                itemId = itemId,
                played = false,
                favorite = false,
                playbackPositionTicks = 1_200_000_000L,
                toBeSynced = toBeSynced,
            )
        )

    @Test
    fun `a download of a movie another server stored replaces that server's copy`(): Unit =
        runBlocking {
            val file = storeMovie(SERVER_A)
            userData(userA, movieId, toBeSynced = true)
            val movie = database.getMovie(movieId).toFindroidMovie(database, userB)

            current(SERVER_B)
            downloader.releaseOtherServersCopy(movie, SERVER_B)

            assertNull(database.getMovieOrNull(movieId), "A's movie row")
            assertEquals(emptyList(), database.getSources(movieId), "A's source rows")
            assertFalse(file.exists(), "A's file")
            // A's user watched it offline: that still goes to A.
            assertNotNull(database.getUserDataToBeSynced(userA, movieId), "A's progress to send")
        }

    @Test
    fun `the current server's own copy is left alone`(): Unit = runBlocking {
        val file = storeMovie(SERVER_A)
        val movie = database.getMovie(movieId).toFindroidMovie(database, userA)

        current(SERVER_A)
        downloader.releaseOtherServersCopy(movie, SERVER_A)

        assertEquals(SERVER_A, database.getMovieOrNull(movieId)?.serverId)
        assertEquals(1, database.getSources(movieId).size)
        assertTrue(file.exists(), "A's file")
    }

    @Test
    fun `an episode another server stored gives way, its other episodes stay`(): Unit =
        runBlocking {
            val taken = storeEpisode(SERVER_A, episodeId, index = 1)
            val kept = storeEpisode(SERVER_A, otherEpisodeId, index = 2)
            val episode = database.getEpisode(episodeId).toFindroidEpisode(database, userB)

            current(SERVER_B)
            downloader.releaseOtherServersCopy(episode, SERVER_B)

            assertNull(database.getEpisodeOrNull(episodeId), "the episode B downloads")
            assertFalse(taken.exists(), "its file")
            assertEquals(SERVER_A, database.getEpisodeOrNull(otherEpisodeId)?.serverId)
            assertTrue(kept.exists(), "A's other episode's file")
            assertEquals(listOf(seasonId), database.getSeasonsByShowId(showId).map { it.id })
            assertEquals(1, database.getSources(otherEpisodeId).size)
        }

    /** The server's source of the movie, [size] bytes. */
    private fun serverSource(size: Long) =
        FindroidSource(
            id = "source-$movieId",
            name = "Original",
            type = FindroidSourceType.REMOTE,
            path = "http://server.invalid/Videos/$movieId/stream",
            size = size,
            mediaStreams = emptyList(),
        )

    private fun assertMovieKept(file: File) = runBlocking {
        assertEquals(SERVER_A, database.getMovieOrNull(movieId)?.serverId, "A's movie row")
        assertEquals(listOf(file.absolutePath), database.getSources(movieId).map { it.path })
        assertTrue(file.exists(), "A's file")
    }

    @Test
    fun `a download refused for want of space leaves the other server's copy`(): Unit =
        runBlocking {
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            for (external in context.getExternalFilesDirs(null)) {
                ShadowEnvironment.setExternalStorageState(external, Environment.MEDIA_MOUNTED)
            }
            val file = storeMovie(SERVER_A)
            val movie = database.getMovie(movieId).toFindroidMovie(database, userB)
            server = { name ->
                when (name) {
                    "JellyfinRepository.getMediaSources" -> listOf(serverSource(Long.MAX_VALUE / 4))
                    "JellyfinRepository.getServerSegments" -> emptyList<Any>()
                    else -> throw IllegalStateException(name)
                }
            }

            current(SERVER_B)
            val (downloadId, error) = downloader.downloadItem(movie, "source-$movieId", 0, null)

            assertEquals(-1L, downloadId)
            assertNotNull(error, "the refusal")
            assertMovieKept(file)
        }

    @Test
    fun `a download that fails at the server leaves the other server's copy`(): Unit = runBlocking {
        val file = storeMovie(SERVER_A)
        val movie = database.getMovie(movieId).toFindroidMovie(database, userB)
        // The source comes, then the server stops answering.
        server = { name ->
            when (name) {
                "JellyfinRepository.getMediaSources" -> listOf(serverSource(1L))
                else -> throw IllegalStateException(name)
            }
        }

        current(SERVER_B)
        val (downloadId, _) = downloader.downloadItem(movie, "source-$movieId", 0, null)

        assertEquals(-1L, downloadId)
        assertMovieKept(file)
    }

    private object Online : NetworkConnectivity {
        override fun isOnline() = true

        override fun isMetered() = false

        override fun isRoaming() = false
    }

    private companion object {
        const val SERVER_A = "server-a"
        const val SERVER_B = "server-b"
    }
}
