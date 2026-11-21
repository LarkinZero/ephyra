package dev.jdtech.jellyfin.player.local.mpv

/**
 * CE (#723): the mpv command that empties the playlist and stops what plays, for a new playlist.
 *
 * In one step: mpv's `playlist-current-pos` goes from the playing entry to none (-1). The two steps
 * used before, `playlist-clear` and then `playlist-remove current`, left the playing entry alone in
 * the playlist for a moment, at index 0, and mpv reported that index. [MPVPlayer] already held the
 * new playlist by then, so index 0 named its first item: a quality switch on the second item of a
 * queue looked like a change to the first item and back, a stop and a new start for the item that
 * kept playing (or, when mpv's next report was lost in between, the first item for good).
 */
internal fun clearPlaylistCommand(): Array<String> = arrayOf("stop")

/**
 * CE (#723): the mpv commands that load a new playlist of [uris], for [MPVPlayer.prepare], which
 * then plays the entry at [startIndex] with `playlist-play-index`.
 *
 * The entries are appended to an empty playlist, which plays none of them: mpv's
 * `playlist-current-pos` goes from none (-1) straight to [startIndex]. Loading the first entry with
 * `replace` started it, so mpv reported index 0 before the jump.
 *
 * [startSeconds] goes with the entry at [startIndex] as its start option (mpv 0.38+, the bundled
 * build is 0.42: `loadfile <url> <flags> <index> <options>`, where the index of -1 is the default),
 * so mpv opens it there instead of seeking afterwards.
 */
internal fun playlistLoadCommands(
    uris: List<String>,
    startIndex: Int,
    startSeconds: Double?,
): List<Array<String>> = buildList {
    // Nothing to do after setMediaItems, but a stopped player keeps its playlist, and prepare()
    // replaces it.
    add(clearPlaylistCommand())
    uris.forEachIndexed { index, uri ->
        if (index == startIndex && startSeconds != null) {
            add(arrayOf("loadfile", uri, "append", "-1", "start=$startSeconds"))
        } else {
            add(arrayOf("loadfile", uri, "append"))
        }
    }
}
