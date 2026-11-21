package dev.jdtech.jellyfin.core.presentation.spoiler

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** #618: what "Hide spoilers" hides, and that a press and hold reveals one episode. */
class SpoilersTest {
    private val on = Spoilers(enabled = true)

    @Test
    fun `an unwatched episode is hidden`() {
        assertTrue(on.hides(dummyEpisode.copy(id = UUID.randomUUID(), played = false)))
    }

    @Test
    fun `a watched episode, a movie and nothing are not hidden`() {
        assertFalse(on.hides(dummyEpisode.copy(id = UUID.randomUUID(), played = true)))
        assertFalse(on.hides(dummyMovie.copy(played = false)))
        assertFalse(on.hides(null))
    }

    @Test
    fun `nothing is hidden with the setting off`() {
        assertFalse(Spoilers.Off.hides(dummyEpisode.copy(id = UUID.randomUUID(), played = false)))
    }

    @Test
    fun `a reveal shows that episode only, also to a later Spoilers`() {
        val revealed = dummyEpisode.copy(id = UUID.randomUUID(), played = false)
        val other = dummyEpisode.copy(id = UUID.randomUUID(), played = false)
        on.reveal(revealed)
        assertFalse(Spoilers(enabled = true).hides(revealed))
        assertTrue(on.hides(other))
    }

    @Test
    fun `another user's reveals are cleared`() {
        val revealed = dummyEpisode.copy(id = UUID.randomUUID(), played = false)
        on.reveal(revealed)
        SpoilerReveals.clear()
        assertTrue(on.hides(revealed))
    }
}
