package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * CE (#726): the quality switches and the audio switches of a transcode. Each asks the server for
 * new streams for the whole queue ([rebuildQueue]) and swaps them in, so a second switch can start
 * while the first still waits on the server.
 *
 * One switch runs at a time: a new one cancels the one in flight and asks for streams with both
 * choices, so the latest choice wins for quality and for audio, whatever order the server answers
 * in. A pick of audio that has not reached the player yet ([pendingAudio]) is passed on to the
 * switch that cancels it; the cap is read from the setting with each request, and the setting holds
 * the latest choice. A switch that fails rolls the setting back to the cap of the streams that play
 * ([playingBitrate]), not to the value from before it started, which a cancelled switch may never
 * have reached. A cancelled switch (a newer one, or the player closing) changes nothing.
 *
 * Main thread only, like the player: [scope] must run its coroutines there.
 */
class StreamSwitches(
    private val scope: CoroutineScope,
    /** The cap the streams that play were requested with: the setting when the player opened. */
    playingBitrate: Int,
    private val host: Host,
) {
    interface Host {
        /** The player's queue, in its order. */
        fun queue(): List<PlayerItem>

        /** The item that plays and its audio track, as [recreate] takes them. */
        fun playing(): Playing

        /** Forgets an item that left the queue while it got new streams ([rebuildQueue]). */
        fun forget(itemId: UUID)

        /**
         * [item] with new streams, under the cap in the setting. [pickedAudio] is the audio stream
         * picked for it, [playingAudio] the track it plays now, each only for the item it is for.
         */
        suspend fun recreate(
            item: PlayerItem,
            playingAudio: StoredChoice?,
            pickedAudio: Int?,
        ): PlayerItem

        /** Shows [bitrate] as the quality setting and stores it. */
        fun setBitrate(bitrate: Int)

        /** Swaps [rebuilt] in, the queue with new streams. Called without suspending after it. */
        fun swapIn(rebuilt: List<PlayerItem>)

        /** The switch failed: the streams that play keep playing. */
        fun failed(error: Exception)
    }

    /** The item that plays when a switch starts, and its audio track (null: unnamed or none). */
    data class Playing(val itemId: UUID?, val audio: StoredChoice?)

    /** An audio stream picked for [itemId] that is not in the player yet. */
    private class AudioPick(
        val itemId: UUID,
        val streamIndex: Int,
        /** Waits for what the pick needs first (the series' choice to be saved). */
        val before: suspend () -> Unit,
    )

    private var playingBitrate = playingBitrate
    private var wantedBitrate = playingBitrate
    private var pendingAudio: AudioPick? = null
    private var job: Job? = null

    /** Switches to [bitrate], 0 for the original quality. */
    fun selectBitrate(bitrate: Int) {
        if (bitrate == wantedBitrate) return
        wantedBitrate = bitrate
        host.setBitrate(bitrate)
        restart()
    }

    /**
     * Switches [itemId], which plays the audio stream [playingIndex], to the stream [streamIndex].
     * [before] runs in the switch before the streams are requested.
     */
    fun selectAudio(
        itemId: UUID,
        streamIndex: Int,
        playingIndex: Int?,
        before: suspend () -> Unit = {},
    ) {
        val pending = pendingAudio?.takeIf { it.itemId == itemId }
        // Back to the track that plays: only a pick on its way has to be undone.
        if (streamIndex == playingIndex && pending == null) return
        pendingAudio = AudioPick(itemId, streamIndex, before)
        restart()
    }

    private fun restart() {
        job?.cancel()
        val bitrate = wantedBitrate
        val audio = pendingAudio
        val playing = host.playing()
        job = scope.launch {
            val rebuilt =
                try {
                    audio?.before?.invoke()
                    rebuildQueue(host::queue, host::forget) { item ->
                        host.recreate(
                            item,
                            playingAudio = playing.audio.takeIf { item.itemId == playing.itemId },
                            pickedAudio = audio?.streamIndex?.takeIf { item.itemId == audio.itemId },
                        )
                    }
                } catch (e: CancellationException) {
                    // A newer switch took over, or the player closed: it owns the setting.
                    throw e
                } catch (e: Exception) {
                    // A request that failed after the cancel is the cancelled switch's too.
                    ensureActive()
                    pendingAudio = null
                    if (wantedBitrate != playingBitrate) {
                        wantedBitrate = playingBitrate
                        host.setBitrate(playingBitrate)
                    }
                    host.failed(e)
                    return@launch
                }
            // The last stream may come in after the cancel.
            ensureActive()
            host.swapIn(rebuilt)
            playingBitrate = bitrate
            pendingAudio = null
        }
    }
}
