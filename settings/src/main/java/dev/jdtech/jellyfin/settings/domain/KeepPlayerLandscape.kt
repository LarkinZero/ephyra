package dev.jdtech.jellyfin.settings.domain

import android.content.Context
import android.os.Build

/** CE (#363): where "Keep the player in landscape" ([AppPreferences.playerKeepLandscape]) works. */
object KeepPlayerLandscape {
    /**
     * Whether the system honours the player's orientation request on a wide window at [sdkInt].
     *
     * From Android 16 (API 36) the system ignores `setRequestedOrientation()` on displays at least
     * 600 dp wide for apps that target API 36 or later, unless the user picks "App default" in the
     * aspect ratio settings ("Ignore orientation and resizability restrictions on large screens",
     * Android 16 behaviour changes). Ephyra targets 37, so there the setting would do nothing.
     * Re-check this whenever `Versions.TARGET_SDK` changes or a new Android release changes the
     * rule.
     */
    fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt < 36

    /**
     * Whether the settings show the switch: the system honours it ([isSupported]) and the device
     * can give the player a wide window, now (a tablet, an unfolded foldable) or after unfolding (a
     * hinge-angle sensor, so a folded foldable shows it too; a flip phone, narrow either way, shows
     * it as well). Phones always play in landscape, so the switch would do nothing there.
     */
    fun isShown(context: Context): Boolean = isSupported() && WideWindow.isPossible(context)
}
