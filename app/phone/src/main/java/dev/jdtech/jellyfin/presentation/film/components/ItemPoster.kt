package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.spoiler.LocalSpoilers
import dev.jdtech.jellyfin.core.presentation.spoiler.spoilerFreeName
import dev.jdtech.jellyfin.core.presentation.spoiler.spoilerImage
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

/**
 * [preferThumb] (#608): a horizontal card shows the thumb, the wide art with the title on it, of
 * the movie or of the episode's series. The Continue Watching and Next Up rows ask for it.
 */
@Composable
fun ItemPoster(
    item: FindroidItem,
    direction: Direction,
    modifier: Modifier = Modifier,
    preferThumb: Boolean = false,
) {
    // CE (#618): a hidden episode's still is blurred where it comes first, and left out where it
    // is only the last resort behind the show's art.
    val hidden = LocalSpoilers.current.hides(item)
    val sources =
        remember(item.images, item.id, direction, preferThumb, hidden) {
            posterSources(item, direction, preferThumb, hideStill = hidden)
        }
    val stillFirst = item.images.primary != null && sources.firstOrNull() == item.images.primary

    ArtworkImage(
        sources = sources,
        contentDescription =
            stringResource(CoreR.string.image_description_poster, spoilerFreeName(item, hidden)),
        modifier =
            modifier
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .spoilerImage(
                    hidden = hidden && stillFirst,
                    fallback = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
    )
}

/**
 * The artwork a card tries, in order. With [preferThumb] a horizontal card starts with art that
 * says what the item is, and an episode's own image, a frame from the episode, comes last.
 */
internal fun posterSources(
    item: FindroidItem,
    direction: Direction,
    preferThumb: Boolean = false,
    hideStill: Boolean = false,
): List<Uri?> =
    when (direction) {
        Direction.HORIZONTAL ->
            when (item) {
                is FindroidMovie ->
                    if (preferThumb) {
                        listOf(item.images.thumb, item.images.backdrop, item.images.primary)
                    } else {
                        listOf(item.images.backdrop, item.images.primary)
                    }
                is FindroidEpisode ->
                    if (preferThumb && hideStill) {
                        listOf(item.images.showThumb, item.images.showBackdrop)
                    } else if (preferThumb) {
                        listOf(item.images.showThumb, item.images.showBackdrop, item.images.primary)
                    } else {
                        listOf(item.images.primary, item.images.showBackdrop)
                    }
                else -> listOf(item.images.primary)
            }
        Direction.VERTICAL ->
            when (item) {
                is FindroidEpisode -> listOf(item.images.showPrimary)
                is FindroidSeason -> listOf(item.images.primary, item.images.showPrimary)
                else -> listOf(item.images.primary)
            }
    }
