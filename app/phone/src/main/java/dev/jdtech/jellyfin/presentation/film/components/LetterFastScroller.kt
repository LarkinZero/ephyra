package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.ephyra.EphyraMotion
import dev.jdtech.jellyfin.film.presentation.library.LetterIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transform

/**
 * CE (#672): a draggable thumb on the end edge of a library grid, with the letter of the titles
 * under it in a bubble while dragging.
 *
 * The thumb is proportional: where it is on the track is where the grid is in the list, so it works
 * in every sort. The bubble needs [letterIndex], which the ViewModel only builds when sorting by
 * name. It needs a grid with placeholders ([itemCount] is the whole library), or a jump has nothing
 * to land on.
 *
 * It shows while the grid scrolls and fades out after [HIDE_DELAY_MS]. Only the thumb takes touches
 * and keeps the back gesture off, and only while it shows. It is left out for screen readers: the
 * grid scrolls for them as it is.
 */
@Composable
fun LetterFastScroller(
    gridState: LazyGridState,
    itemCount: Int,
    letterIndex: LetterIndex?,
    modifier: Modifier = Modifier,
) {
    // Read through a state, so the derived values below follow a count that changes after they
    // were remembered (it is 0 until the first page is in).
    val currentItemCount by rememberUpdatedState(itemCount)
    val maxFirst by
        remember(gridState) {
            derivedStateOf {
                (currentItemCount - gridState.layoutInfo.visibleItemsInfo.size).coerceAtLeast(0)
            }
        }
    val canScroll by
        remember(gridState) {
            derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
        }
    val gridFraction by
        remember(gridState) {
            derivedStateOf {
                if (!gridState.canScrollForward) 1f
                else fastScrollFraction(gridState.firstVisibleItemIndex, maxFirst)
            }
        }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragTarget by remember {
        derivedStateOf { dragFraction?.let { fastScrollTarget(it, maxFirst) } }
    }

    // A jump far from the loaded pages makes the paging source load again, so the drag doesn't
    // jump on every move: at most once per JUMP_INTERVAL_MS, and the last position always.
    LaunchedEffect(gridState) {
        snapshotFlow { dragTarget }
            .filterNotNull()
            .throttledJumps(JUMP_INTERVAL_MS)
            .collect { target ->
                // The end of the track is the end of the list, not its last screen at the top
                // (the grid stops at the end by itself).
                gridState.scrollToItem(
                    if (target >= maxFirst) (currentItemCount - 1).coerceAtLeast(0) else target
                )
            }
    }

    val dragging = dragFraction != null
    val active = dragging || gridState.isScrollInProgress
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(HIDE_DELAY_MS)
            shown = false
        }
    }
    val visible = shown && canScroll && itemCount > 0
    val alpha by
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis =
                        if (visible) EphyraMotion.DURATION_SHORT else EphyraMotion.DURATION_MEDIUM,
                    easing = if (visible) EphyraMotion.EasingEnter else EphyraMotion.EasingExit,
                ),
            label = "fastScrollerAlpha",
        )
    if (alpha == 0f && !visible) return

    val bubbleLetter = dragTarget?.let { letterIndex?.letterAt(it) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().alpha(alpha).clearAndSetSemantics {}) {
        val density = LocalDensity.current
        val travelPx = with(density) { (maxHeight - TouchHeight).toPx() }.coerceAtLeast(1f)
        val fraction = dragFraction ?: gridFraction
        val thumbY = (fraction * travelPx).roundToInt()

        if (bubbleLetter != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                x = -TouchWidth.roundToPx(),
                                y = thumbY + ((TouchHeight - BubbleSize) / 2).roundToPx(),
                            )
                        }
                        .size(BubbleSize)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Text(
                    text = bubbleLetter.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The touch area is wider than the pill, so it's easy to hit without covering a card.
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbY) }
                    .size(TouchWidth, TouchHeight)
                    .then(
                        if (visible) {
                            Modifier.systemGestureExclusion().pointerInput(travelPx) {
                                var y = 0f
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        y = gridFraction * travelPx
                                        dragFraction = gridFraction
                                    },
                                    onDragEnd = { dragFraction = null },
                                    onDragCancel = { dragFraction = null },
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        y = (y + dy).coerceIn(0f, travelPx)
                                        dragFraction = y / travelPx
                                    },
                                )
                            }
                        } else Modifier
                    ),
        ) {
            Box(
                modifier =
                    Modifier.padding(end = ThumbEndPadding)
                        .align(Alignment.CenterEnd)
                        .size(ThumbWidth, ThumbHeight)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        )
            )
        }
    }
}

/** Where the thumb is for a grid whose first visible item is [firstVisible], from 0 to 1. */
internal fun fastScrollFraction(firstVisible: Int, maxFirst: Int): Float =
    if (maxFirst <= 0) 0f else (firstVisible.toFloat() / maxFirst).coerceIn(0f, 1f)

/** The item the grid scrolls to for a thumb at [fraction]: the inverse of [fastScrollFraction]. */
internal fun fastScrollTarget(fraction: Float, maxFirst: Int): Int =
    (fraction.coerceIn(0f, 1f) * maxFirst.coerceAtLeast(0)).roundToInt()

/**
 * The targets of a drag, at most one per [intervalMillis]. A target that comes in while waiting
 * replaces the one before it, so the last position of the drag is always jumped to.
 */
internal fun Flow<Int>.throttledJumps(intervalMillis: Long): Flow<Int> =
    distinctUntilChanged().conflate().transform {
        emit(it)
        delay(intervalMillis)
    }

internal const val JUMP_INTERVAL_MS = 100L
private const val HIDE_DELAY_MS = 1000L

private val TouchWidth = 40.dp
private val TouchHeight = 64.dp
private val ThumbWidth = 6.dp
private val ThumbHeight = 48.dp
private val ThumbEndPadding = 4.dp
private val BubbleSize = 64.dp
