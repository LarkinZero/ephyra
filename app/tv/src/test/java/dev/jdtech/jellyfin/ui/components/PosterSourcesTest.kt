package dev.jdtech.jellyfin.ui.components

import android.app.Application
import android.net.Uri
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.models.FindroidImages
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which artwork a TV card tries, and in what order (#416). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PosterSourcesTest {
    private val own = Uri.parse("https://demo.example/items/season/Images/Primary?tag=s")
    private val show = Uri.parse("https://demo.example/items/show/Images/Primary?tag=p")
    private val backdrop = Uri.parse("https://demo.example/items/movie/Images/Backdrop/0?tag=b")

    @Test
    fun `a season without a poster uses the show's`() {
        val season = dummySeason.copy(images = FindroidImages(showPrimary = show))
        assertEquals(listOf(show), posterSources(season, Direction.VERTICAL))
    }

    @Test
    fun `a season with a poster tries its own first, then the show's`() {
        val season = dummySeason.copy(images = FindroidImages(primary = own, showPrimary = show))
        assertEquals(listOf(own, show), posterSources(season, Direction.VERTICAL))
    }

    @Test
    fun `a season of a show without a poster has nothing to show`() {
        val season = dummySeason.copy(images = FindroidImages())
        assertEquals(emptyList<Uri>(), posterSources(season, Direction.VERTICAL))
    }

    @Test
    fun `episodes, movies and shows keep their artwork`() {
        val images = FindroidImages(primary = own, backdrop = backdrop, showPrimary = show)
        assertEquals(
            listOf(show),
            posterSources(dummyEpisode.copy(images = images), Direction.VERTICAL),
        )
        assertEquals(
            listOf(own),
            posterSources(dummyEpisode.copy(images = images), Direction.HORIZONTAL),
        )
        assertEquals(
            listOf(backdrop),
            posterSources(dummyMovie.copy(images = images), Direction.HORIZONTAL),
        )
        assertEquals(
            listOf(own),
            posterSources(dummyMovie.copy(images = images), Direction.VERTICAL),
        )
        assertEquals(
            listOf(own),
            posterSources(dummyShow.copy(images = images), Direction.VERTICAL),
        )
    }
}
