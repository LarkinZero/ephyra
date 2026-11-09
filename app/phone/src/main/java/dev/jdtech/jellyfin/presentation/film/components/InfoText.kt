package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.copyOnLongClick

@Composable
fun InfoText(
    genres: List<String>,
    director: FindroidItemPerson?,
    writers: List<FindroidItemPerson>,
    // CE (#332): the studios of a movie, or the networks of a show.
    studios: List<String> = emptyList(),
    studiosAreNetworks: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        if (genres.isNotEmpty()) {
            val genresText = genres.joinToString()
            Text(
                text = "${stringResource(CoreR.string.genres)}: $genresText",
                modifier = Modifier.copyOnLongClick(genresText),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (director != null) {
            Text(
                text = "${stringResource(CoreR.string.director)}: ${director.name}",
                modifier = Modifier.copyOnLongClick(director.name),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (writers.isNotEmpty()) {
            val writersText = writers.joinToString { it.name }
            Text(
                text = "${stringResource(CoreR.string.writers)}: $writersText",
                modifier = Modifier.copyOnLongClick(writersText),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (studios.isNotEmpty()) {
            val studiosText = studios.joinToString()
            val label =
                stringResource(
                    if (studiosAreNetworks) CoreR.string.item_networks else CoreR.string.item_studios
                )
            Text(
                text = "$label: $studiosText",
                modifier = Modifier.copyOnLongClick(studiosText),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
