package dev.jdtech.jellyfin.settings.domain

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.window.layout.WindowMetricsCalculator

/** CE: which devices can give the app a wide window, for settings that only matter there. */
object WideWindow {
    /**
     * A window whose shorter side is below this many dp is compact (a phone, a folded foldable).
     */
    private const val WIDE_SHORTER_SIDE_DP = 600

    /**
     * Whether the device can give the app a wide window, now (a tablet, an unfolded foldable) or
     * after unfolding (a hinge-angle sensor, so a folded foldable counts too; a flip phone, narrow
     * either way, counts as well).
     */
    fun isPossible(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
        ) {
            return true
        }
        val bounds =
            WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(context).bounds
        val shorterSideDp =
            minOf(bounds.width(), bounds.height()) / context.resources.displayMetrics.density
        return shorterSideDp >= WIDE_SHORTER_SIDE_DP
    }
}
