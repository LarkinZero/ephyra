package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import java.util.UUID

/**
 * The items of [queue] with new streams, as [recreate] makes them, for a quality or audio switch.
 *
 * CE (#721): the streams wait on the server, and meanwhile the neighbour lookup ([NeighbourLoader])
 * can add items to the queue, or a jump in the queue replace it. So the queue is read again once
 * the streams are in, and the items that came in meanwhile get theirs too, until nothing changed
 * while waiting. The result is the queue as it is when this returns, in its order: the order the
 * player's current index counts, as long as the caller swaps it in without suspending first.
 *
 * [recreate] marks an item as queued in [PlaylistManager]. An item that left the queue meanwhile (a
 * jump replaced it) is handed to [forget] at the end, also when the rebuild fails or is cancelled,
 * or the neighbour lookup would take it as queued and never put it back.
 *
 * Each pass recreates at least one item it has not seen, and the queue only grows by the neighbour
 * lookup (two items per item change), so this ends once the user stops changing items.
 *
 * Main thread only, like the player.
 */
suspend fun rebuildQueue(
    queue: () -> List<PlayerItem>,
    forget: (UUID) -> Unit,
    recreate: suspend (PlayerItem) -> PlayerItem,
): List<PlayerItem> {
    val rebuilt = mutableMapOf<UUID, PlayerItem>()
    try {
        while (true) {
            // A copy: the queue can change while a stream is fetched.
            val items = queue().toList()
            val missing = items.filter { it.itemId !in rebuilt }
            if (missing.isEmpty()) return items.map { rebuilt.getValue(it.itemId) }
            for (item in missing) rebuilt[item.itemId] = recreate(item)
        }
    } finally {
        val queued = queue().map { it.itemId }.toSet()
        rebuilt.keys.filter { it !in queued }.forEach(forget)
    }
}
