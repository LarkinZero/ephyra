package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadProgress
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    /** All seasons of the series, only loaded with `includeSeriesSeasons` (TV season tabs). */
    val seriesSeasons: List<FindroidSeason> = emptyList(),
    val episodeDownloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val error: Exception? = null,
    /** CE (#505): a load is running, so empty [episodes] may still be coming. */
    val isLoading: Boolean = false,
    /**
     * CE (#281): [episodes] are the stored (downloaded) episodes only: the downloads-only page, or
     * the server did not answer. A removed download then leaves the list instead of staying in it
     * as not downloaded.
     */
    val storedOnly: Boolean = false,
)
