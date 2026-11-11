package dev.jdtech.jellyfin

import android.content.pm.ActivityInfo
import dev.jdtech.jellyfin.settings.domain.KeepPlayerLandscape

/** Below this many dp a window is compact width (Material window size classes). */
internal const val COMPACT_WIDTH_DP = 600

/**
 * CE (R6, T7, #363): the orientation the player asks for.
 * - Locked controls freeze the current orientation, whatever else holds.
 * - A phone or a folded foldable (the maximum window's shorter side under [COMPACT_WIDTH_DP])
 *   plays in landscape, either way round.
 * - A wider window (a tablet, an unfolded foldable) follows the device, unless "Keep the player in
 *   landscape" is on. That setting is left out in split-screen or a freeform window, in
 *   picture-in-picture and with a fold half-opened (tabletop or book posture, R9): there the
 *   window has to be free to turn. It is also left out where the system ignores the request
 *   ([KeepPlayerLandscape.isSupported]).
 *
 * The fold counts half-opened in either orientation: with landscape forced, a Pixel Fold held in
 * tabletop posture reports its hinge as vertical, because a fold's orientation is relative to the
 * window, so only a horizontal hinge would never release it.
 */
internal fun playerOrientation(
    shorterSideDp: Float,
    keepLandscape: Boolean,
    locked: Boolean,
    multiWindow: Boolean,
    pictureInPicture: Boolean,
    foldHalfOpened: Boolean,
    sdkInt: Int,
): Int =
    when {
        locked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        shorterSideDp < COMPACT_WIDTH_DP -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        keepLandscape &&
            KeepPlayerLandscape.isSupported(sdkInt) &&
            !multiWindow &&
            !pictureInPicture &&
            !foldHalfOpened -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
