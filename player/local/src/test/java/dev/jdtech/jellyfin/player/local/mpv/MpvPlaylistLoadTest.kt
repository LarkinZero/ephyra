package dev.jdtech.jellyfin.player.local.mpv

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * CE (#723): replacing mpv's playlist never leaves an entry at another index, so mpv's
 * `playlist-current-pos` never names an entry on the way to the one [MPVPlayer.prepare] plays. The
 * old playlist was cleared with `playlist-clear`, which keeps the playing entry and moves it to
 * index 0, and the first new entry was loaded with `replace`, which plays it: a quality switch on
 * the second item reported the first one as playing, and the player sent a stop and a new start for
 * the item that kept playing.
 */
class MpvPlaylistLoadTest {
    private val uris = listOf("e1", "e2", "e3")

    /** A command that makes mpv play an entry: `replace`, the `-play` flags, or a jump. */
    private fun Array<String>.plays() =
        this[0] == "playlist-play-index" ||
            (this[0] == "loadfile" && (this[2] == "replace" || this[2].endsWith("-play")))

    @Test
    fun `a playlist that starts at its second entry starts none of them on loading`() {
        val commands = playlistLoadCommands(uris, startIndex = 1, startSeconds = null)

        assertEquals(
            emptyList(),
            commands.filter { it.plays() }.map { it.joinToString(" ") },
            "commands that play an entry",
        )
        assertEquals(
            listOf("loadfile e1 append", "loadfile e2 append", "loadfile e3 append"),
            commands.filter { it[0] == "loadfile" }.map { it.joinToString(" ") },
            "the entries, in order",
        )
    }

    @Test
    fun `the old playlist is emptied in one step before the new entries go in`() {
        val commands = playlistLoadCommands(uris, startIndex = 0, startSeconds = null)

        assertEquals("stop", commands.first().joinToString(" "), "the first command")
        // playlist-clear keeps the playing entry and moves it to index 0, which mpv reports.
        assertEquals(
            emptyList(),
            commands.filter { it[0].startsWith("playlist-") }.map { it.joinToString(" ") },
            "playlist commands that leave the playing entry at another index",
        )
        assertEquals(listOf("stop"), clearPlaylistCommand().toList(), "setMediaItems' command")
    }

    @Test
    fun `the start position goes with the entry that plays first`() {
        val commands = playlistLoadCommands(uris, startIndex = 0, startSeconds = 12.5)
        val loads = commands.filter { it[0] == "loadfile" }.map { it.joinToString(" ") }

        assertEquals("loadfile e1 append -1 start=12.5", loads[0], "the first entry")
        assertTrue(loads.drop(1).none { "start=" in it }, "the other entries: $loads")
        assertTrue(commands.none { it.plays() }, "no command plays an entry")
    }
}
