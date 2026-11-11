package dev.jdtech.jellyfin.presentation.film.components

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.domain.MediaInfo
import dev.jdtech.jellyfin.film.domain.MediaInfoTrack
import dev.jdtech.jellyfin.film.domain.toMediaInfo
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Locale

const val MEDIA_INFO_SHEET_TAG = "media_info_sheet"

/**
 * CE (#510): the file behind a movie or episode, opened from the info chip at the end of the badge
 * row. It replaces the "Display Extra Info" setting, which put the same facts on every page as
 * four lines of text.
 *
 * Everything comes from [sources], which the page already has; nothing is worked out until the
 * sheet opens. An item with several versions gets a pill per version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(sources: List<FindroidSource>, onDismissRequest: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        MediaInfoContent(sources = sources, modifier = Modifier.testTag(MEDIA_INFO_SHEET_TAG))
    }
}

@Composable
fun MediaInfoContent(sources: List<FindroidSource>, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val source = sources.getOrNull(selected) ?: sources.firstOrNull() ?: return
    val locale =
        ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val info = remember(source, locale) { source.toMediaInfo(locale) }

    // Selectable, so a line can be copied into a bug report or a search.
    SelectionContainer(modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = MaterialTheme.spacings.medium,
                        end = MaterialTheme.spacings.medium,
                        bottom = MaterialTheme.spacings.default,
                    ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall)) {
                Text(
                    text = stringResource(CoreR.string.ce_media_info),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                val file = fileLine(info)
                if (file.isNotEmpty()) {
                    Text(
                        text = file,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sources.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    sources.forEachIndexed { index, version ->
                        FilterChip(
                            selected = index == selected,
                            onClick = { selected = index },
                            label = {
                                Text(
                                    text = version.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            shape = CircleShape,
                        )
                    }
                }
            }
            MediaInfoSection(stringResource(CoreR.string.video), info.video)
            MediaInfoSection(stringResource(CoreR.string.audio), info.audio)
            MediaInfoSection(stringResource(CoreR.string.subtitle), info.subtitles)
        }
    }
}

/** "MKV · 54.01 GB · 62.1 Mbps", with whatever the server left out left out. */
@Composable
private fun fileLine(info: MediaInfo): String {
    val context = LocalContext.current
    val size = info.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatFileSize(context, it) }
    return listOfNotNull(info.container, size, info.bitrate).joinToString(" · ")
}

/** One kind of track: its name, how many there are when there are several, and a card of them. */
@Composable
private fun MediaInfoSection(title: String, tracks: List<MediaInfoTrack>) {
    if (tracks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            if (tracks.size > 1) {
                Text(
                    text = tracks.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                tracks.forEachIndexed { index, track ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier =
                                Modifier.padding(horizontal = MaterialTheme.spacings.medium),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    MediaInfoTrackRow(track)
                }
            }
        }
    }
}

@Composable
private fun MediaInfoTrackRow(track: MediaInfoTrack) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.medium, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = track.title, style = MaterialTheme.typography.bodyMedium)
        if (track.details.isNotEmpty()) {
            Text(
                text = track.details.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaInfoContentPreview() {
    FindroidTheme { MediaInfoContent(sources = dummyMovie.sources) }
}
