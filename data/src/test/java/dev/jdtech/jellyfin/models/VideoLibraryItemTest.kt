package dev.jdtech.jellyfin.models

import android.app.Application
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CE (#671): the items of Home Videos & Photos and Music Videos libraries. */
// Robolectric for android.net.Uri, which the image mapping uses.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VideoLibraryItemTest {
    private val repository =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "getBaseUrl") "https://jf.example"
            else throw NotImplementedError(method.name)
        } as JellyfinRepository

    private fun map(type: BaseItemKind, canDownload: Boolean = true): FindroidItem? = runBlocking {
        BaseItemDto(id = UUID.randomUUID(), type = type, name = "Item", canDownload = canDownload)
            .toFindroidItem(repository, emptyMap())
    }

    @Test
    fun `both library types are known and shown`() {
        assertEquals(CollectionType.HomeVideos, CollectionType.fromString("homevideos"))
        assertEquals(CollectionType.MusicVideos, CollectionType.fromString("musicvideos"))
        assertTrue(CollectionType.HomeVideos in CollectionType.supported)
        assertTrue(CollectionType.MusicVideos in CollectionType.supported)
    }

    @Test
    fun `only the new library types narrow their Latest row`() {
        assertEquals(listOf(BaseItemKind.VIDEO), CollectionType.HomeVideos.latestItemTypes)
        assertEquals(listOf(BaseItemKind.MUSIC_VIDEO), CollectionType.MusicVideos.latestItemTypes)
        assertNull(CollectionType.Movies.latestItemTypes)
        assertNull(CollectionType.TvShows.latestItemTypes)
        assertNull(CollectionType.Mixed.latestItemTypes)
    }

    @Test
    fun `a home video and a music video map to a movie`() {
        assertTrue(map(BaseItemKind.VIDEO) is FindroidMovie)
        assertTrue(map(BaseItemKind.MUSIC_VIDEO) is FindroidMovie)
    }

    @Test
    fun `a folder with a photo in it maps to a folder`() {
        assertTrue(map(BaseItemKind.PHOTO_ALBUM) is FindroidFolder)
    }

    @Test
    fun `a photo maps to nothing`() {
        assertNull(map(BaseItemKind.PHOTO))
    }

    @Test
    fun `only a real movie can be downloaded`() {
        assertTrue(map(BaseItemKind.MOVIE)!!.canDownload)
        assertFalse(map(BaseItemKind.VIDEO)!!.canDownload)
        assertFalse(map(BaseItemKind.MUSIC_VIDEO)!!.canDownload)
    }
}
