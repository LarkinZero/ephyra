package dev.jdtech.jellyfin.player.local.mpv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util

/**
 * CE (#711): the subtitle files of the item mpv plays, and which of them mpv has.
 *
 * mpv used to get every subtitle file at the start (`sub-add`). During a transcode the server
 * answers the first request for any of them only once it has extracted all of the file's subtitle
 * tracks, which takes minutes for a large file on a slow disk; mpv gave up on each file after its
 * network timeout, and the track dialog then listed only None for the rest of the playback.
 *
 * Now a file is fetched when it is wanted: the one mpv would pick by default ([defaultFile]) and
 * the one picked in the dialog. The player downloads it (without mpv's timeout) and hands mpv the
 * downloaded file; a local file (a downloaded item) goes to mpv as it is. The track list has every
 * file the server offers, in the item's order: a file mpv does not have yet is listed from what the
 * server says about it, under the same id ([id]) it keeps once mpv has it, so a pick or a saved
 * choice holds while the file loads, and picking it again after a failed download tries again.
 *
 * Not thread-safe: [MPVPlayer] uses it on the main thread.
 */
internal class MpvSubtitleFiles {

    data class SubtitleFile(
        val url: String,
        val title: String,
        val language: String,
        val mimeType: String?,
        val forced: Boolean,
        val default: Boolean,
    ) {
        /** A file the player downloads first; mpv opens any other path itself. */
        val remote: Boolean
            get() = url.startsWith("http://", true) || url.startsWith("https://", true)
    }

    /** A track in mpv's track list, as far as the subtitle files are concerned. */
    data class MpvTrack(
        val id: String,
        val type: String,
        val externalFilename: String?,
        val selected: Boolean,
    )

    var files: List<SubtitleFile> = emptyList()
        private set

    /** The file mpv would pick at the start, see [defaultSubtitleFile]. */
    var defaultFile: Int? = null
        private set

    /** What mpv opens for a file, by URL: the downloaded copy, or the path of a local file. */
    private val paths = HashMap<String, String>()
    private val downloading = HashSet<String>()

    /** Failed downloads in a row, by URL. */
    private val failures = HashMap<String, Int>()

    /** The files handed to mpv for the current file, and the ids mpv gave them. */
    private val added = HashSet<Int>()
    private val trackIds = HashMap<Int, String>()

    private sealed interface Selection {
        /** mpv's own choice; [defaultFile] is shown once it is there, if mpv shows nothing. */
        data object Auto : Selection

        data object Off : Selection

        /** A file picked by id; [shown] once mpv was told to show it. */
        data class File(val index: Int, val shown: Boolean) : Selection

        /** A track of the stream itself. */
        data class Track(val id: String) : Selection
    }

    private var selection: Selection = Selection.Auto
    private var defaultShown = false

    /**
     * Starts over with the files of a new item (or of the same one, loaded again). With [textOff]
     * (subtitles turned off in the track selection parameters) nothing is fetched or shown until a
     * file is picked, as mpv's `sid=no` did before.
     */
    fun reset(files: List<SubtitleFile>, preferredLanguage: String?, textOff: Boolean = false) {
        this.files = files
        defaultFile = defaultSubtitleFile(files, preferredLanguage)
        added.clear()
        trackIds.clear()
        selection = if (textOff) Selection.Off else Selection.Auto
        defaultShown = false
        for (file in files) if (!file.remote) paths[file.url] = file.url
    }

    /**
     * Forgets the downloads that are not files of the current item, and returns their paths to
     * delete: one item's files do not pile up over a whole queue. An item loaded again (a quality
     * switch, a reload after an error) keeps its own.
     */
    fun dropOtherDownloads(): List<String> {
        val current = files.mapTo(HashSet()) { it.url }
        val dropped = paths.filter { (url, path) -> url !in current && url != path }
        dropped.keys.forEach { paths -= it }
        return dropped.values.toList()
    }

    /** Whether [url] is one of the current item's files. */
    fun isFileOfItem(url: String): Boolean = files.any { it.url == url }

    /** The id the track list gives file [index], whether or not mpv has it. */
    fun id(index: Int): String = "$ID_PREFIX$index"

    /** The file of track id [id], or null for a track of the stream. */
    fun indexOf(id: String): Int? =
        id.takeIf { it.startsWith(ID_PREFIX) }
            ?.removePrefix(ID_PREFIX)
            ?.toIntOrNull()
            ?.takeIf { it in files.indices }

    /** The file mpv lists as [externalFilename], if it is one of these. */
    fun indexOfMpvFile(externalFilename: String?): Int? {
        if (externalFilename == null) return null
        return files.indices.firstOrNull { paths[files[it].url] == externalFilename }
    }

    /**
     * Whether the track list shows file [index] as selected while mpv does not have it yet: the
     * picked file, or the default one while mpv chooses and the file still comes.
     */
    fun isWaitingFor(index: Int): Boolean =
        when (val current = selection) {
            is Selection.File -> current.index == index && !current.shown
            Selection.Auto ->
                index == defaultFile &&
                    !defaultShown &&
                    index !in trackIds &&
                    (failures[files[index].url] ?: 0) < MAX_AUTOMATIC_RETRIES
            else -> false
        }

    /** The file that is wanted now: the default one while mpv chooses, or the picked one. */
    private fun wantedFile(): Int? =
        when (val current = selection) {
            Selection.Auto -> defaultFile
            is Selection.File -> current.index
            else -> null
        }

    /** The file to fetch now, see [wantedFile], when it is not there or on its way. */
    fun toFetch(): List<String> =
        listOfNotNull(wantedFile())
            .map { files[it].url }
            .filter { it !in paths && it !in downloading }

    fun downloadStarted(url: String) {
        downloading += url
    }

    /** The file at [url] is at [path] now. */
    fun downloaded(url: String, path: String) {
        downloading -= url
        failures -= url
        paths[url] = path
    }

    /**
     * The download of [url] failed. Returns whether to try again by itself: not after
     * [MAX_AUTOMATIC_RETRIES] failures, not for a file no longer wanted, and not when the server
     * said it never will ([retriable] false: a 4xx). Picking the file again always tries again.
     */
    fun downloadFailed(url: String, retriable: Boolean = true): Boolean {
        downloading -= url
        val count = if (retriable) (failures[url] ?: 0) + 1 else MAX_AUTOMATIC_RETRIES
        failures[url] = count
        return count < MAX_AUTOMATIC_RETRIES && url in toFetch()
    }

    /** The player dropped the download of [url]; it is fetched again when wanted. */
    fun downloadCanceled(url: String) {
        downloading -= url
    }

    /** The files mpv can be given now and does not have, in order; they count as given after it. */
    fun takeToAdd(): List<Pair<Int, String>> =
        files.indices
            .filter { it !in added }
            .mapNotNull { index -> paths[files[index].url]?.let { index to it } }
            .also { list -> list.forEach { added += it.first } }

    /**
     * mpv sent [tracks]. Returns the subtitle track id to select now, if any: a picked file that
     * mpv lists now, or the default one when nothing else shows.
     */
    fun onTrackList(tracks: List<MpvTrack>): String? {
        trackIds.clear()
        for (track in tracks) {
            val index = indexOfMpvFile(track.externalFilename) ?: continue
            trackIds[index] = track.id
        }
        when (val current = selection) {
            is Selection.File -> {
                val id = trackIds[current.index]
                if (!current.shown && id != null) {
                    selection = current.copy(shown = true)
                    return id
                }
            }
            Selection.Auto -> {
                val index = defaultFile ?: return null
                val id = trackIds[index] ?: return null
                if (defaultShown) return null
                defaultShown = true
                if (tracks.none { it.type == SUBTITLE && it.selected }) return id
            }
            else -> Unit
        }
        return null
    }

    /**
     * The player asked for subtitle track [id] ("no", "auto", a stream track's mpv id, or [id] of a
     * file). Returns what mpv's `sid` is to be set to, or null to leave it.
     */
    fun select(id: String): String? {
        val index = indexOf(id)
        return when {
            id == "no" -> {
                selection = Selection.Off
                "no"
            }
            id == "auto" -> {
                if (selection == Selection.Auto) return null
                selection = Selection.Auto
                "auto"
            }
            index != null -> {
                failures -= files[index].url
                val trackId = trackIds[index]
                selection = Selection.File(index, shown = trackId != null)
                // Until the file is there nothing shows, not the track picked before.
                trackId ?: "no"
            }
            else -> {
                selection = Selection.Track(id)
                id
            }
        }
    }

    companion object {
        const val ID_PREFIX = "file:"
        const val SUBTITLE = "sub"

        /** Failed downloads of a wanted file before the player waits for it to be picked again. */
        const val MAX_AUTOMATIC_RETRIES = 10

        /**
         * The file mpv would pick at the start, by mpv's default rules as far as the files go: one
         * in [preferredLanguage] (mpv's `slang`), else a forced one, else one marked default. Null
         * when none is, or without files.
         */
        @OptIn(UnstableApi::class)
        fun defaultSubtitleFile(files: List<SubtitleFile>, preferredLanguage: String?): Int? {
            val preferred = preferredLanguage?.let(Util::normalizeLanguageCode)
            if (!preferred.isNullOrBlank()) {
                val index = files.indexOfFirst {
                    Util.normalizeLanguageCode(it.language) == preferred
                }
                if (index != -1) return index
            }
            return files.indexOfFirst { it.forced }.takeIf { it != -1 }
                ?: files.indexOfFirst { it.default }.takeIf { it != -1 }
        }
    }
}
