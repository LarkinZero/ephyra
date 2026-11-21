package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.BaseItemKind

enum class CollectionType(val type: String) {
    Movies("movies"),
    TvShows("tvshows"),
    HomeVideos("homevideos"),
    Music("music"),
    Playlists("playlists"),
    Books("books"),
    LiveTv("livetv"),
    BoxSets("boxsets"),
    Mixed("null"),
    Folders("folders"),
    MusicVideos("musicvideos"),
    Unknown("unknown");

    /**
     * CE (#671): the kinds a Latest row of this library asks for, or null for whatever the server
     * picks. A Home Videos & Photos library would otherwise fill the row with photos, which the app
     * drops, and leave its videos out.
     */
    val latestItemTypes: List<BaseItemKind>?
        get() =
            when (this) {
                HomeVideos -> listOf(BaseItemKind.VIDEO)
                MusicVideos -> listOf(BaseItemKind.MUSIC_VIDEO)
                else -> null
            }

    companion object {
        val defaultValue = Unknown

        val supported = listOf(Movies, TvShows, BoxSets, Mixed, Folders, HomeVideos, MusicVideos)

        fun fromString(string: String?): CollectionType {
            if (
                string == null
            ) { // TODO jellyfin returns null as the collectiontype for mixed libraries. This is
                //  obviously wrong, but probably an upstream issue. Should be fixed whenever
                //  upstream fixes this
                return Mixed
            }

            return try {
                entries.first { it.type == string }
            } catch (e: NoSuchElementException) {
                defaultValue
            }
        }
    }
}
