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
 * Main thread only, like the player.
 */
suspend fun rebuildQueue(
    queue: () -> List<PlayerItem>,
    recreate: suspend (PlayerItem) -> PlayerItem,
): List<PlayerItem> {
    val rebuilt = mutableMapOf<UUID, PlayerItem>()
    while (true) {
        // A copy: the queue can change while a stream is fetched.
        val items = queue().toList()
        val missing = items.filter { it.itemId !in rebuilt }
        if (missing.isEmpty()) return items.map { rebuilt.getValue(it.itemId) }
        for (item in missing) rebuilt[item.itemId] = recreate(item)
    }
}
