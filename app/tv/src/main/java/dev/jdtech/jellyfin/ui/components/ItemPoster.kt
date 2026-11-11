package dev.jdtech.jellyfin.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidFolder
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.spacings

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(item: FindroidItem, direction: Direction, modifier: Modifier = Modifier) {
    val sources = remember(item.images, direction) { posterSources(item, direction) }

    // CE (#135): without artwork, or when none of it loads, the card shows the title and an icon
    // instead of an empty rectangle. A failed load moves on to the next source (#416).
    var index by remember(sources) { mutableIntStateOf(0) }
    val imageUri = sources.getOrNull(index)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(if (direction == Direction.HORIZONTAL) 1.77f else 0.66f)
                .background(MaterialTheme.colorScheme.surface)
    ) {
        if (imageUri == null) {
            PosterPlaceholder(
                item = item,
                // A vertical episode poster is the show's, a horizontal one the episode's own.
                title =
                    if (item is FindroidEpisode && direction == Direction.VERTICAL) item.seriesName
                    else item.name,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { index++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The artwork a card tries, in order: a horizontal movie its backdrop, a vertical episode its
 * show's poster, a vertical season its own poster and then its show's (#416), anything else its
 * own poster.
 */
internal fun posterSources(item: FindroidItem, direction: Direction): List<Uri> =
    when (direction) {
        Direction.HORIZONTAL ->
            when (item) {
                is FindroidMovie -> listOf(item.images.backdrop)
                else -> listOf(item.images.primary)
            }
        Direction.VERTICAL ->
            when (item) {
                is FindroidEpisode -> listOf(item.images.showPrimary)
                is FindroidSeason -> listOf(item.images.primary, item.images.showPrimary)
                else -> listOf(item.images.primary)
            }
    }.filterNotNull()

@Composable
private fun PosterPlaceholder(item: FindroidItem, title: String, modifier: Modifier = Modifier) {
    val icon =
        when (item) {
            is FindroidShow,
            is FindroidSeason,
            is FindroidEpisode -> CoreR.drawable.ic_tv
            is FindroidFolder,
            is FindroidBoxSet -> CoreR.drawable.ic_library
            else -> CoreR.drawable.ic_film
        }
    Column(
        modifier = modifier.padding(MaterialTheme.spacings.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
