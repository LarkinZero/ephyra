package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.runtime.compositionLocalOf

/**
 * CE (#622): the "Backdrop beside the text" setting. When it is on, an item page in a landscape
 * window shows its backdrop at 16:9 beside the text ([Layout.heroBackdropWidth]); off, the
 * full-width band.
 */
val LocalSideBackdrop = compositionLocalOf { false }
