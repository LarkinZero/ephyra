package dev.jdtech.jellyfin.repository

/** The local user data a playback stop writes: the resume position and the played flag. */
internal data class StopUserData(val positionTicks: Long, val played: Boolean)

/**
 * What a stop at [positionTicks] writes to the local database, decided by [playedPercentage]: under
 * 10 % resets the position, over 90 % resets it and marks the item played, in between saves it.
 *
 * `null` writes nothing: the item was left before its duration was known (#461), so the local
 * position, played flag and sync flag stay as they were.
 */
internal fun stopUserData(positionTicks: Long, playedPercentage: Int?): StopUserData? {
    val percentage = playedPercentage ?: return null
    return when {
        percentage < 10 -> StopUserData(positionTicks = 0L, played = false)
        percentage > 90 -> StopUserData(positionTicks = 0L, played = true)
        else -> StopUserData(positionTicks = positionTicks, played = false)
    }
}
