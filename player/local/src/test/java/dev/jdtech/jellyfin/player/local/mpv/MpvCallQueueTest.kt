package dev.jdtech.jellyfin.player.local.mpv

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * #460: libmpv calls on the main thread waited for mpv's core, and a core stuck on a wedged decoder
 * or a dead connection turned onPause, surfaceChanged and leaving the player into ANRs. The queue
 * must keep the calls in order and never keep the caller waiting longer than it was told.
 */
class MpvCallQueueTest {

    private val queue = MpvCallQueue("mpv-calls-test")

    /** Lets a call stand in for a stuck mpv core until the test ends. */
    private val unstick = CountDownLatch(1)

    @AfterEach
    fun tearDown() {
        unstick.countDown()
        queue.close {}
    }

    private fun stick() = queue.post { unstick.await(10, TimeUnit.SECONDS) }

    @Test
    fun `calls run in the order they were made, on another thread`() {
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val caller = Thread.currentThread()
        var otherThread = false
        for (i in 1..50) queue.post { order.add(i) }
        queue.post { otherThread = Thread.currentThread() != caller }
        assertEquals(50, queue.read { order.size })
        assertEquals((1..50).toList(), order.toList())
        assertTrue(otherThread)
    }

    @Test
    fun `a read sees the calls posted before it`() {
        var value = 0
        queue.post { value = 1 }
        queue.post { value = 2 }
        assertEquals(2, queue.read { value })
    }

    @Test
    fun `a post returns at once while mpv is stuck`() {
        stick()
        val start = System.nanoTime()
        repeat(10) { queue.post {} }
        assertTrue(elapsedMs(start) < 500)
    }

    @Test
    fun `a read gives up after its timeout while mpv is stuck, and later ones at once`() {
        stick()
        var start = System.nanoTime()
        assertNull(queue.read(timeoutMs = 200L) { 1 })
        val first = elapsedMs(start)
        assertTrue(first in 150..1_500, message = "waited $first ms")
        start = System.nanoTime()
        repeat(5) { assertNull(queue.read(timeoutMs = 200L) { 1 }) }
        assertTrue(elapsedMs(start) < 150)
    }

    @Test
    fun `reads wait again once mpv answers`() {
        stick()
        assertNull(queue.read(timeoutMs = 100L) { 1 })
        unstick.countDown()
        // The calls queued while stuck finish and clear the stuck mark. A wait that starts before
        // the thread got there still returns at once, so poll until one is answered.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!queue.runBounded(2_000L) {}) {
            assertTrue(System.nanoTime() < deadline, message = "mpv thread did not recover")
            Thread.sleep(10)
        }
        assertEquals(7, queue.read(timeoutMs = 2_000L) { 7 })
    }

    @Test
    fun `runBounded says whether the call finished in time`() {
        assertTrue(queue.runBounded(2_000L) {})
        stick()
        assertFalse(queue.runBounded(100L) {})
    }

    @Test
    fun `a call that throws does not stop the ones after it`() {
        queue.post { error("mpv is gone") }
        assertEquals(3, queue.read { 3 })
    }

    @Test
    fun `close runs the last call after the others and takes no more`() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)
        queue.post { order.add("detach") }
        queue.close {
            order.add("destroy")
            done.countDown()
        }
        queue.post { order.add("after") }
        assertNull(queue.read { "after" })
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("detach", "destroy"), order.toList())
    }

    @Test
    fun `close returns at once while mpv is stuck`() {
        stick()
        val start = System.nanoTime()
        queue.close {}
        assertTrue(elapsedMs(start) < 500)
    }

    private fun elapsedMs(startNanos: Long) =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
}
