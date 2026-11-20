package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import java.util.UUID
import timber.log.Timber

/**
 * Puts the items before and after an item into the player's playlist, once that item is ready.
 *
 * CE (#657): the lookups wait on the server (playback sources, and on a shuffled series the full
 * episode), and the user can skip meanwhile. So each neighbour is placed next to the item it was
 * fetched for, wherever that item is by then, not next to the item playing when it arrives. A
 * neighbour the playlist already has (fetched twice while the user went back and forth) is left
 * out, and so is one whose item left the playlist (a jump in the queue replaced it): the next item
 * change asks for it again.
 *
 * Main thread only, like the player.
 */
class NeighbourLoader(private val playlistManager: PlaylistManager) {
    /** The player's playlist, as the items in it. */
    interface Playlist {
        /** The ids of the items in the playlist, in order. */
        fun itemIds(): List<UUID>

        /** Adds [item] to the playlist at [index]. */
        fun insert(index: Int, item: PlayerItem)
    }

    /** Fetches the items before and after [anchor] and puts them next to it in [playlist]. */
    suspend fun load(anchor: UUID, playlist: Playlist) {
        playlistManager.getPreviousPlayerItem(anchor)?.let {
            place(it, anchor, before = true, playlist)
        }
        playlistManager.getNextPlayerItem(anchor)?.let {
            place(it, anchor, before = false, playlist)
        }
    }

    private fun place(neighbour: PlayerItem, anchor: UUID, before: Boolean, playlist: Playlist) {
        val ids = playlist.itemIds()
        val anchorIndex = ids.indexOf(anchor)
        when {
            neighbour.itemId in ids -> Timber.d("${neighbour.itemId} is already queued")
            anchorIndex < 0 -> {
                Timber.d("Not queueing ${neighbour.itemId}: $anchor left the playlist")
                playlistManager.forget(neighbour.itemId)
            }
            else -> playlist.insert(if (before) anchorIndex else anchorIndex + 1, neighbour)
        }
    }
}
