package dev.jdtech.jellyfin.player.local.mpv

/**
 * CE (#460): notices when mpv says it is playing but the position does not move.
 *
 * On a stalled (not cut) connection mpv ended its last buffering cycle, reported nothing more, and
 * the player stayed READY with Pause showing over a frozen picture. A wedged decoder looks the
 * same. [MPVPlayer] calls [check] about once a second while it expects playback to move (READY,
 * playWhenReady, nothing suppressing it, no error), and [reset] whenever it does not.
 *
 * All times are in milliseconds. Not thread-safe: [MPVPlayer] calls it on the main thread.
 */
internal class MpvStallWatch(
    private val stallMs: Long = STALL_MS,
    private val giveUpMs: Long = GIVE_UP_MS,
) {
    enum class Verdict {
        /** The position moved, or has not stood still for [STALL_MS] yet. */
        MOVING,
        /** Standing still for [STALL_MS]: report buffering. */
        STALLED,
        /** Standing still for [GIVE_UP_MS]: report an error, so the item can be loaded again. */
        GIVE_UP,
    }

    private var lastPositionMs: Long? = null
    private var stillSinceMs = 0L

    /** Playback is not expected to move (paused, seeking, loading, buffering, an error). */
    fun reset() {
        lastPositionMs = null
    }

    /** Playback is expected to move; mpv's position is [positionMs] at [nowMs]. */
    fun check(positionMs: Long?, nowMs: Long): Verdict {
        val position = positionMs ?: NO_POSITION
        val last = lastPositionMs
        if (last == null || position != last) {
            lastPositionMs = position
            stillSinceMs = nowMs
            return Verdict.MOVING
        }
        val still = nowMs - stillSinceMs
        return when {
            still >= giveUpMs -> Verdict.GIVE_UP
            still >= stallMs -> Verdict.STALLED
            else -> Verdict.MOVING
        }
    }

    /** How long the position has stood still at [nowMs], for the log. */
    fun stillForMs(nowMs: Long): Long = if (lastPositionMs == null) 0L else nowMs - stillSinceMs

    companion object {
        /**
         * Long enough that a moment's hiccup does not flash a spinner; mpv's own position updates
         * come many times a second while it plays.
         */
        const val STALL_MS = 3_000L

        /**
         * Past mpv's `network-timeout` (30 s) and a reconnect after it, so a connection that comes
         * back is picked up by mpv itself first.
         */
        const val GIVE_UP_MS = 60_000L

        /** Stands for a position mpv has not reported yet, so a later null is "still". */
        private const val NO_POSITION = Long.MIN_VALUE
    }
}
