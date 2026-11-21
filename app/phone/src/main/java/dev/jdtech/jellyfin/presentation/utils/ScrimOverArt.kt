package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.EphyraColor

/** How far down the status-bar scrim reaches over a backdrop (4-design.md §3). */
val TopScrimOverArtHeight = 96.dp

/**
 * The top `scrimOverArt` gradient (VISION §3.3): black at 10 % at the top edge, fading to nothing
 * over [TopScrimOverArtHeight], so the status-bar icons stay readable over bright artwork.
 */
fun DrawScope.drawTopScrimOverArt() {
    val height = TopScrimOverArtHeight.toPx()
    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(EphyraColor.scrimOverArtTop, Color.Transparent),
                startY = 0f,
                endY = height,
            ),
        size = Size(size.width, minOf(height, size.height)),
    )
}

/**
 * How tall the band is in which a backdrop fades into the page (#631): at the bottom edge of the
 * hero, and above the title on the compact item page.
 */
val FadeIntoPageHeight = 80.dp

/**
 * CE (#631): fades a backdrop into the page [background]. Above [fadeTop] the artwork is left as it
 * is; from there to [fadeBottom] the page colour comes in on an eased curve, so neither end of the
 * band shows as a line, until it covers the artwork at [alpha]. Below [fadeBottom], down to
 * [artBottom], it goes on evenly to the full [background]. All positions are in this scope's
 * coordinates and may lie outside it.
 */
fun DrawScope.drawFadeIntoPage(
    background: Color,
    fadeTop: Float,
    fadeBottom: Float,
    alpha: Float = 1f,
    artBottom: Float = fadeBottom,
) {
    val height = artBottom - fadeTop
    if (height <= 0f) return
    drawRect(
        brush =
            Brush.verticalGradient(
                *fadeStops(
                    background,
                    (fadeBottom - fadeTop) / height,
                    alpha,
                    artBottom > fadeBottom,
                ),
                startY = fadeTop,
                endY = artBottom,
            ),
        topLeft = Offset(0f, fadeTop),
        size = Size(size.width, height),
    )
}

/**
 * CE (#622): the side edge of a backdrop that does not span the page, faded into the page
 * [background] over [width] on the eased curve of [drawFadeIntoPage]. The fade is on the start
 * side, so it follows the layout direction: the left edge, or the right one in RTL.
 */
fun DrawScope.drawStartFadeIntoPage(background: Color, width: Float) {
    if (width <= 0f) return
    val stops = fadeStops(background, band = 1f, alpha = 1f, solidPast = false)
    val brush =
        if (layoutDirection == LayoutDirection.Ltr) {
            Brush.horizontalGradient(*stops, startX = width, endX = 0f)
        } else {
            Brush.horizontalGradient(*stops, startX = size.width - width, endX = size.width)
        }
    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
    drawRect(brush = brush, topLeft = Offset(left, 0f), size = Size(width, size.height))
}

/**
 * Colour stops of a fade: the eased curve over the first [band] (a fraction) of the gradient, up to
 * [alpha], and from there evenly to the full [background] when [solidPast].
 */
private fun fadeStops(
    background: Color,
    band: Float,
    alpha: Float,
    solidPast: Boolean,
): Array<Pair<Float, Color>> =
    buildList {
            for (step in 0..FadeSteps) {
                val t = step / FadeSteps.toFloat()
                // Smoothstep: starts and ends flat, so the band has no hard edge.
                val eased = t * t * (3 - 2 * t)
                add(t * band to background.copy(alpha = alpha * eased))
            }
            if (solidPast) add(1f to background)
        }
        .toTypedArray()

/** Colour stops of the eased part of [drawFadeIntoPage], past its first one. */
private const val FadeSteps = 8
