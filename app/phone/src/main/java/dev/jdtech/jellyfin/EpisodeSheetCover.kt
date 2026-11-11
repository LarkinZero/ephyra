package dev.jdtech.jellyfin

import android.graphics.Rect

/**
 * CE (#456): whether a control at [view] lies under the episode sheet at [sheet], both in window
 * coordinates. The sheet is 92 % opaque (design §9), so a control under it shows through. A
 * control that only touches the sheet's edge is not under it.
 */
internal fun isUnderEpisodeSheet(view: Rect, sheet: Rect): Boolean = Rect.intersects(view, sheet)
