package dev.jdtech.jellyfin.player.local.mpv

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import timber.log.Timber

/**
 * CE (#460): runs the calls [MPVPlayer] makes into libmpv on a thread of their own, in the order
 * they were made.
 *
 * Every libmpv call that sets something or runs a command waits for mpv's core. When the core is
 * stuck (a decoder that never answers, a read on a dead connection), a call on the main thread
 * blocks it until Android reports an ANR: `onPause` pausing mpv, `surfaceChanged` passing the new
 * size, leaving the player. Here the main thread only hands the call over:
 * - [post] for a call nothing waits for. One thread, so the calls keep their order.
 * - [read] for a value: waits at most a timeout for it, and returns null after it, as libmpv does
 *   for a property it cannot read. Behind the calls posted before it, so it sees their effect.
 * - [runBounded] for a call that must be done before the caller goes on (the surface going away),
 *   but not at the cost of an ANR.
 * - [close] runs one last call (`destroy`) and takes no more.
 *
 * A call that throws is logged and dropped, so one failure does not stop the calls after it.
 */
internal class MpvCallQueue(name: String = "mpv-calls") {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }

    @Volatile private var closed = false

    /**
     * A wait for the mpv thread ran out, and it has finished nothing since (as far as this thread
     * saw). Further waits return at once then, so a stuck mpv costs the main thread one timeout,
     * not one per call.
     */
    @Volatile private var stuck = false

    /** Runs [block] on the mpv thread after every call posted before it. Returns at once. */
    fun post(block: () -> Unit) {
        if (closed) return
        try {
            executor.execute { runLogged(block) }
        } catch (_: RejectedExecutionException) {
            // close() raced this call: mpv is gone.
        }
    }

    /**
     * Runs [block] on the mpv thread and waits up to [timeoutMs] for its result. Null when it takes
     * longer (the call still runs when mpv gets to it), when it fails or after [close].
     */
    fun <T> read(timeoutMs: Long = READ_TIMEOUT_MS, block: () -> T?): T? {
        if (closed) return null
        // Still queued while stuck: the call keeps its place in the order, and when it is done it
        // clears [stuck] again.
        val wasStuck = stuck
        val future =
            try {
                executor.submit<T?> { runLogged(block) }
            } catch (_: RejectedExecutionException) {
                return null
            }
        return try {
            future.get(if (wasStuck) 0L else timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            if (!wasStuck) Timber.w("mpv did not answer within $timeoutMs ms")
            stuck = true
            null
        } catch (e: Exception) {
            Timber.w(e, "mpv call failed")
            null
        }
    }

    /**
     * Runs [block] on the mpv thread and waits up to [timeoutMs] for it. Returns whether it was
     * done in time; if not, it still runs when mpv gets to it.
     */
    fun runBounded(timeoutMs: Long, block: () -> Unit): Boolean =
        read(timeoutMs) {
            block()
            true
        } == true

    /**
     * Runs [last] after every call posted before it, then stops the thread. Returns at once, and
     * every call made after it is ignored.
     */
    fun close(last: () -> Unit) {
        if (closed) return
        closed = true
        try {
            executor.execute { runLogged(last) }
        } catch (_: RejectedExecutionException) {
            // Already shut down.
        }
        executor.shutdown()
    }

    private fun <T> runLogged(block: () -> T?): T? =
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "mpv call failed")
            null
        } finally {
            stuck = false
        }

    companion object {
        /** How long the main thread waits for a property. */
        const val READ_TIMEOUT_MS = 1_000L
    }
}
