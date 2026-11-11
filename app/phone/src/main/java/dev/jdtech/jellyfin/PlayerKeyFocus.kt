package dev.jdtech.jellyfin

/** What Space does in the phone player (#448). */
internal enum class SpaceKeyAction {
    PLAY_PAUSE,
    /** Passed on: the focused control is pressed, as Android does for a confirm key. */
    FOCUSED_CONTROL,
    NOTHING,
}

/**
 * CE (#448): whether Space and the arrows go to the focused control instead of acting as player
 * shortcuts. Only when the user put focus on that control with Tab or the arrows: Android also
 * focuses a control by itself, when the first key leaves touch mode or the focused view is hidden,
 * and Space on that control pressed it unseen (the back button closed the player).
 */
internal fun keyGoesToFocusedControl(
    focusChosenByKeyboard: Boolean,
    focusOnControl: Boolean,
    controlsShowing: Boolean,
): Boolean = focusChosenByKeyboard && focusOnControl && controlsShowing

/** CE (#448): with the controls locked, Space only presses an Unlock or skip the user moved to. */
internal fun spaceKeyAction(locked: Boolean, goesToFocusedControl: Boolean): SpaceKeyAction =
    when {
        goesToFocusedControl -> SpaceKeyAction.FOCUSED_CONTROL
        locked -> SpaceKeyAction.NOTHING
        else -> SpaceKeyAction.PLAY_PAUSE
    }
