package dev.jdtech.jellyfin.presentation.utils

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * CE (R8): app-wide keyboard shortcuts. `MainActivity` hands every key event to [onKeyEvent]
 * before the views see it, because a Compose key handler only gets keys when something below it
 * has focus, and after a touch nothing has.
 */
object KeyboardShortcuts {
    private val find = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Returns true when the event was a shortcut that a screen on display took. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val isFind =
            event.keyCode == KeyEvent.KEYCODE_F &&
                event.isCtrlPressed &&
                !event.isAltPressed &&
                !event.isMetaPressed
        if (!isFind || find.subscriptionCount.value == 0) return false
        // The up event is consumed too, so nothing below sees half a shortcut.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) find.tryEmit(Unit)
        return true
    }

    /** Calls [onFind] for Ctrl+F while the calling screen is resumed. */
    @Composable
    fun OnFind(onFind: () -> Unit) {
        val currentOnFind by rememberUpdatedState(onFind)
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                find.collect { currentOnFind() }
            }
        }
    }
}
