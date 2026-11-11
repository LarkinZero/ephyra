package dev.jdtech.jellyfin

/**
 * Whether the player's PiP params enable auto-enter (Android 12+), which makes Home enter PiP.
 *
 * Only with the PiP home gesture on. The PiP button's params stay with the activity after the
 * window is expanded again, so auto-enter in them would make Home enter PiP for the rest of the
 * session with the gesture off (#447).
 */
internal fun pipAutoEnterEnabled(gestureEnabled: Boolean, isPlaying: Boolean): Boolean =
    gestureEnabled && isPlaying
