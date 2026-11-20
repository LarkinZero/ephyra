package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.player.local.mpv.MpvStallWatch.Verdict
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * #460: on a stalled connection mpv reported READY after its last buffering cycle and then nothing,
 * so the player showed Pause over a frozen picture for minutes. The watch must catch a position
 * that stands still, and nothing else.
 */
class MpvStallWatchTest {

    private val watch = MpvStallWatch(stallMs = 3_000L, giveUpMs = 60_000L)
    private var now = 1_000_000L

    /** One check a second, as MPVPlayer does, with the position at [positionMs]. */
    private fun tick(positionMs: Long?): Verdict {
        now += 1_000L
        return watch.check(positionMs, now)
    }

    @Test
    fun `a moving position is never a stall`() {
        repeat(120) { second -> assertEquals(Verdict.MOVING, tick(second * 1_000L)) }
    }

    @Test
    fun `a position that stands still is a stall after three seconds`() {
        assertEquals(Verdict.MOVING, tick(617_000L)) // the first look
        assertEquals(Verdict.MOVING, tick(617_000L)) // 1 s
        assertEquals(Verdict.MOVING, tick(617_000L)) // 2 s
        assertEquals(Verdict.STALLED, tick(617_000L)) // 3 s
        assertEquals(3_000L, watch.stillForMs(now))
    }

    @Test
    fun `a stall that goes on gives up after a minute`() {
        tick(617_000L)
        repeat(59) {
            assertEquals(true, tick(617_000L) != Verdict.GIVE_UP, message = "second ${it + 1}")
        }
        assertEquals(Verdict.GIVE_UP, tick(617_000L))
    }

    @Test
    fun `moving again ends the stall`() {
        tick(617_000L)
        repeat(5) { tick(617_000L) }
        assertEquals(Verdict.STALLED, tick(617_000L))
        assertEquals(Verdict.MOVING, tick(617_040L))
        assertEquals(Verdict.MOVING, tick(617_040L))
    }

    @Test
    fun `a reset starts over, so a pause does not count as standing still`() {
        tick(617_000L)
        tick(617_000L)
        tick(617_000L)
        // Paused for a minute: MPVPlayer resets on every check while it does not expect movement.
        repeat(60) {
            now += 1_000L
            watch.reset()
        }
        assertEquals(0L, watch.stillForMs(now))
        assertEquals(Verdict.MOVING, tick(617_000L))
        assertEquals(Verdict.MOVING, tick(617_000L))
    }

    @Test
    fun `while mpv buffers, new data is progress`() {
        // Sourcery on #691: mpv's own buffering must time out too, but not while data arrives.
        var buffered = 620_000L
        watch.check(617_000L, now, buffered)
        repeat(120) {
            buffered += 500L
            now += 1_000L
            assertEquals(Verdict.MOVING, watch.check(617_000L, now, buffered))
        }
    }

    @Test
    fun `mpv buffering without data gives up`() {
        watch.check(617_000L, now, 620_000L)
        repeat(59) {
            now += 1_000L
            assertEquals(true, watch.check(617_000L, now, 620_000L) != Verdict.GIVE_UP)
        }
        now += 1_000L
        assertEquals(Verdict.GIVE_UP, watch.check(617_000L, now, 620_000L))
    }

    @Test
    fun `no position at all stands still too`() {
        // READY before mpv reported time-pos, and it never does.
        tick(null)
        tick(null)
        tick(null)
        assertEquals(Verdict.STALLED, tick(null))
        assertEquals(Verdict.MOVING, tick(0L))
    }
}
