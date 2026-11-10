package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C

/**
 * The playback stop to send for an item: [positionTicks] for the server, and the
 * [playedPercentage] that decides whether the local database resets, saves or marks it played.
 * A `null` percentage leaves the local database as it is.
 */
data class PlaybackStopReport(val positionTicks: Long, val playedPercentage: Int?)

/**
 * The stop owed for an item left at [positionMs] of [durationMs], or `null` if none is owed.
 *
 * With a known duration the stop carries the position and the percentage played. Before the
 * duration is known (the item was left while it was still loading), a stop is only owed if the
 * start was sent ([startSent]): it then goes out at [positionMs], which is the position playback
 * was asked to start at, and without a percentage, so a saved resume position is kept (#461).
 */
fun playbackStopReport(positionMs: Long, durationMs: Long, startSent: Boolean): PlaybackStopReport? {
    if (durationMs != C.TIME_UNSET && durationMs > 0) {
        return PlaybackStopReport(
            positionTicks = positionMs.times(10000),
            playedPercentage = positionMs.div(durationMs.toFloat()).times(100).toInt(),
        )
    }
    if (!startSent) return null
    return PlaybackStopReport(positionTicks = positionMs.times(10000), playedPercentage = null)
}
