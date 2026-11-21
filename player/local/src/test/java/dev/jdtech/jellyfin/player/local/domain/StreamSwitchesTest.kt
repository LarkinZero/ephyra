package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Test

/**
 * CE (#726): quality and audio switches that overlap. Every stream request waits until the test
 * answers it ([answer], [fail]), in any order; the switches run on [Dispatchers.Unconfined], so an
 * answer runs the switch on to its next request or its end before it returns.
 */
class StreamSwitchesTest {
    private val e1 = UUID.randomUUID()
    private val e2 = UUID.randomUUID()
    private val e3 = UUID.randomUUID()
    private val playingAudio = StoredChoice(language = "eng", title = null, positionInLanguage = 0)

    /** The queue: e2 plays, at 0 (original quality), with its default audio track. */
    private var queue = listOf(e1, e2, e3).map { item(it, bitrate = 0, audio = null) }

    /** The quality setting, as the host stores it. */
    private var setting = 0
    private val settingChanges = mutableListOf<Int>()
    private val swaps = mutableListOf<List<PlayerItem>>()
    private val errors = mutableListOf<Exception>()
    private val forgotten = mutableListOf<UUID>()

    /** A stream request on its way: for [item], under [bitrate], with [pickedAudio]. */
    private class Request(
        val item: PlayerItem,
        val bitrate: Int,
        val playingAudio: StoredChoice?,
        val pickedAudio: Int?,
        val response: CompletableDeferred<PlayerItem> = CompletableDeferred(),
    )

    private val requests = mutableListOf<Request>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val switches =
        StreamSwitches(
            scope,
            playingBitrate = 0,
            object : StreamSwitches.Host {
                override fun queue() = queue

                override fun playing() = StreamSwitches.Playing(e2, playingAudio)

                override fun forget(itemId: UUID) {
                    forgotten += itemId
                }

                override suspend fun recreate(
                    item: PlayerItem,
                    playingAudio: StoredChoice?,
                    pickedAudio: Int?,
                ): PlayerItem {
                    val request = Request(item, setting, playingAudio, pickedAudio)
                    requests += request
                    return request.response.await()
                }

                override fun setBitrate(bitrate: Int) {
                    setting = bitrate
                    settingChanges += bitrate
                }

                override fun swapIn(rebuilt: List<PlayerItem>) {
                    swaps += rebuilt
                    queue = rebuilt
                }

                override fun failed(error: Exception) {
                    errors += error
                }
            },
        )

    private fun item(id: UUID, bitrate: Int, audio: Int?) =
        PlayerItem(
            name = "$id",
            itemId = id,
            mediaSourceId = "$id",
            playbackPosition = 0,
            mediaSourceUri = "http://server/$id?bitrate=$bitrate&audio=$audio",
            audioStreamIndex = audio,
        )

    /** The server answers the oldest open request of the switch that asked under [bitrate]. */
    private fun answer(bitrate: Int) {
        val request = requests.first { !it.response.isCompleted && it.bitrate == bitrate }
        request.response.complete(
            item(request.item.itemId, request.bitrate, request.pickedAudio ?: request.item.audio())
        )
    }

    private fun PlayerItem.audio() = audioStreamIndex

    /** Answers every open request asked under [bitrate], also the ones the answers lead to. */
    private fun answerAll(bitrate: Int) {
        while (requests.any { !it.response.isCompleted && it.bitrate == bitrate }) answer(bitrate)
    }

    /** The server fails the newest open request asked under [bitrate]. */
    private fun fail(bitrate: Int) {
        requests
            .last { !it.response.isCompleted && it.bitrate == bitrate }
            .response
            .completeExceptionally(IllegalStateException("server error"))
    }

    /** The cap and audio track each item of the last swapped-in queue was requested with. */
    private fun lastSwap() = swaps.last().map { it.mediaSourceUri.substringAfter('?') }

    @Test
    fun `a second quality switch wins when the first one's streams come in last`() {
        switches.selectBitrate(360)
        // The cap is read with each request: e1 and e2 come in, e3 is asked for under 360.
        answer(360)
        answer(360)
        switches.selectBitrate(720)

        answerAll(720)
        answer(360)

        assertEquals(1, swaps.size, "queues swapped in")
        assertEquals(List(3) { "bitrate=720&audio=null" }, lastSwap(), "the streams that play")
        assertEquals(720, setting, "the quality setting")
    }

    @Test
    fun `a failed older switch does not roll the newer one back`() {
        switches.selectBitrate(360)
        switches.selectBitrate(720)

        fail(360)
        answerAll(720)

        assertEquals(emptyList(), errors, "errors shown")
        assertEquals(listOf(360, 720), settingChanges, "the quality setting's changes")
        assertEquals(List(3) { "bitrate=720&audio=null" }, lastSwap(), "the streams that play")
    }

    @Test
    fun `a failed switch rolls back to the quality that plays`() {
        switches.selectBitrate(360)
        answerAll(360)
        // An audio pick cancels the next quality switch, then fails: 360 plays, not 720.
        switches.selectBitrate(720)
        switches.selectAudio(e2, streamIndex = 2, playingIndex = null)

        fail(720)

        assertEquals(1, errors.size, "errors shown")
        assertEquals(360, setting, "the quality setting")
        assertEquals(List(3) { "bitrate=360&audio=null" }, lastSwap(), "the streams that play")
    }

    @Test
    fun `a quality switch after an audio pick keeps the pick`() {
        switches.selectAudio(e2, streamIndex = 2, playingIndex = null)
        switches.selectBitrate(360)

        answerAll(0)
        answerAll(360)

        assertEquals(1, swaps.size, "queues swapped in")
        assertEquals(
            listOf("bitrate=360&audio=null", "bitrate=360&audio=2", "bitrate=360&audio=null"),
            lastSwap(),
            "the streams that play",
        )
    }

    @Test
    fun `the playing item keeps its track on a quality switch`() {
        switches.selectBitrate(360)
        answerAll(360)

        assertEquals(
            listOf(null, playingAudio, null),
            requests.map { it.playingAudio },
            "the playing track, asked for each item",
        )
    }

    @Test
    fun `picking the playing track again undoes a pick on its way`() {
        switches.selectAudio(e2, streamIndex = 2, playingIndex = 1)
        switches.selectAudio(e2, streamIndex = 1, playingIndex = 1)

        answerAll(0)

        assertEquals("bitrate=0&audio=1", lastSwap()[1], "e2's stream")
    }

    @Test
    fun `closing the player during a switch changes no setting`() {
        switches.selectBitrate(360)
        answer(360)

        scope.cancel()

        assertEquals(listOf(360), settingChanges, "the quality setting's changes")
        assertEquals(emptyList(), errors, "errors shown")
        assertEquals(emptyList(), swaps, "queues swapped in")
    }

    @Test
    fun `a cancelled switch still forgets the items that left the queue`() {
        switches.selectBitrate(360)
        answer(360) // e1
        // A jump in the queue replaces it meanwhile.
        queue = listOf(item(e3, bitrate = 0, audio = null))

        switches.selectBitrate(720)

        assertEquals(listOf(e1), forgotten, "items forgotten")
    }
}
