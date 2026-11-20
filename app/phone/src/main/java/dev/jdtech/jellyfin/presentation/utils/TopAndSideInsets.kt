package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * CE: the insets of a screen's top bar, and of content that scrolls under the navigation bar at the
 * bottom. The status bar, the display cutout, and a navigation bar on the side: with 3-button
 * navigation in landscape it sits on the right edge, over the last top bar action and the end of
 * the content (#700).
 */
val topAndSideInsets: WindowInsets
    @Composable
    get() =
        WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
