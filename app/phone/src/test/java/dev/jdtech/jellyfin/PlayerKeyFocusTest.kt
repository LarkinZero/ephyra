package dev.jdtech.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #448: Space on a hardware keyboard closed the player. Android had put focus on the back button by
 * itself, and Space pressed it; with the controls locked, Space pressed the focused Unlock or skip.
 */
class PlayerKeyFocusTest {
    @Test
    fun `focus Android gave a control does not take Space`() {
        assertFalse(
            keyGoesToFocusedControl(
                focusChosenByKeyboard = false,
                focusOnControl = true,
                controlsShowing = true,
            )
        )
        assertEquals(
            SpaceKeyAction.PLAY_PAUSE,
            spaceKeyAction(locked = false, goesToFocusedControl = false),
        )
    }

    @Test
    fun `a control the user tabbed to takes Space`() {
        assertTrue(
            keyGoesToFocusedControl(
                focusChosenByKeyboard = true,
                focusOnControl = true,
                controlsShowing = true,
            )
        )
        assertEquals(
            SpaceKeyAction.FOCUSED_CONTROL,
            spaceKeyAction(locked = false, goesToFocusedControl = true),
        )
    }

    @Test
    fun `hidden controls never take Space`() {
        assertFalse(
            keyGoesToFocusedControl(
                focusChosenByKeyboard = true,
                focusOnControl = true,
                controlsShowing = false,
            )
        )
        assertFalse(
            keyGoesToFocusedControl(
                focusChosenByKeyboard = true,
                focusOnControl = false,
                controlsShowing = true,
            )
        )
    }

    @Test
    fun `locked Space does nothing unless the user moved to Unlock`() {
        assertEquals(
            SpaceKeyAction.NOTHING,
            spaceKeyAction(locked = true, goesToFocusedControl = false),
        )
        assertEquals(
            SpaceKeyAction.FOCUSED_CONTROL,
            spaceKeyAction(locked = true, goesToFocusedControl = true),
        )
    }
}
