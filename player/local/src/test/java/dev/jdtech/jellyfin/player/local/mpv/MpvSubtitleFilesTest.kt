package dev.jdtech.jellyfin.player.local.mpv

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import dev.jdtech.jellyfin.player.local.domain.trackName
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MpvSubtitleFiles] and the track list [MPVPlayer] makes with it: mpv fetches a
 * subtitle file when it is wanted, the dialog lists every file the server offers, and a file that
 * comes late shows without a new pick (#711).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MpvSubtitleFilesTest {

    private val server = "http://server/Videos/1/1/Subtitles"

    /** As the server offers the test server's Big Subs Movie during a transcode. */
    private val pgs =
        MpvSubtitleFiles.SubtitleFile(
            url = "$server/2/0/Stream.pgssub?ApiKey=k",
            title = "",
            language = "eng",
            mimeType = MimeTypes.APPLICATION_PGS,
            forced = false,
            default = true,
        )
    private val srt =
        MpvSubtitleFiles.SubtitleFile(
            url = "$server/3/0/Stream.srt?ApiKey=k",
            title = "",
            language = "nld",
            mimeType = MimeTypes.APPLICATION_SUBRIP,
            forced = false,
            default = false,
        )

    private val video = """{"id":1,"type":"video","selected":true,"codec":"h264"}"""
    private val audio = """{"id":1,"type":"audio","selected":true,"codec":"aac"}"""

    private fun files(vararg files: MpvSubtitleFiles.SubtitleFile, language: String? = null) =
        MpvSubtitleFiles().apply { reset(files.toList(), language) }

    /** mpv's track list with the stream and the given subtitle tracks. */
    private fun trackList(vararg subtitles: String) =
        "[" + (listOf(video, audio) + subtitles).joinToString(",") + "]"

    private fun subtitle(id: Int, path: String, lang: String, codec: String, selected: Boolean) =
        """{"id":$id,"type":"sub","selected":$selected,"external-filename":"$path",""" +
            """"lang":"$lang","codec":"$codec","external":true}"""

    private fun mpvTracks(json: String) =
        org.json.JSONArray(json).let { array ->
            (0 until array.length()).map {
                val track = array.getJSONObject(it)
                MpvSubtitleFiles.MpvTrack(
                    id = track.optInt("id").toString(),
                    type = track.optString("type"),
                    externalFilename =
                        track.optString("external-filename").takeIf {
                            track.has("external-filename")
                        },
                    selected = track.optBoolean("selected"),
                )
            }
        }

    @Test
    fun `only the file mpv would pick by default is fetched at the start`() {
        val files = files(pgs, srt)

        assertEquals(listOf(pgs.url), files.toFetch(), "not every file, as before #711")
    }

    @Test
    fun `nothing is fetched at the start when no file would be picked`() {
        val files = files(srt)

        assertEquals(emptyList(), files.toFetch(), "subtitles off: no request")
    }

    @Test
    fun `the default file follows mpv's rules as far as the files go`() {
        val forced = srt.copy(url = "forced", forced = true)

        assertEquals(1, MpvSubtitleFiles.defaultSubtitleFile(listOf(pgs, srt), "nl"), "slang")
        assertEquals(1, MpvSubtitleFiles.defaultSubtitleFile(listOf(pgs, forced), null), "forced")
        assertEquals(0, MpvSubtitleFiles.defaultSubtitleFile(listOf(pgs, srt), "de"), "default")
        assertNull(MpvSubtitleFiles.defaultSubtitleFile(listOf(srt), null), "none")
    }

    @Test
    fun `the dialog lists every file while mpv has none of them`() {
        val files = files(pgs, srt)

        val tracks = MPVPlayer.getTracks(trackList(), files)
        val names =
            tracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .map { trackName(it.mediaTrackGroup.getFormat(0), "Forced", Locale.ENGLISH) }

        assertEquals(listOf("English · PGS", "Dutch · SRT"), names, "as the server offers them")
    }

    @Test
    fun `a file keeps its name and id once mpv has it`() {
        val files = files(pgs, srt)
        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")
        val before =
            MPVPlayer.getTracks(trackList(), files).groups.first { it.type == C.TRACK_TYPE_TEXT }

        val json =
            trackList(subtitle(1, "/cache/0-Stream.pgssub", "eng", "hdmv_pgs_subtitle", true))
        files.onTrackList(mpvTracks(json))
        val groups = MPVPlayer.getTracks(json, files).groups.filter { it.type == C.TRACK_TYPE_TEXT }

        assertEquals(2, groups.size, "both files, once each")
        val after = groups[0].mediaTrackGroup.getFormat(0)
        assertEquals("file:0", after.id, "the same id: a pick holds")
        assertEquals(
            "English · PGS",
            trackName(after, "Forced", Locale.ENGLISH),
            "the same name as before mpv had it",
        )
        assertEquals("file:0", before.mediaTrackGroup.getFormat(0).id, "the id before")
    }

    @Test
    fun `the default file shows when mpv has it, while it is listed as coming`() {
        val files = files(pgs, srt)
        assertTrue(files.isWaitingFor(0), "listed as selected while it comes")
        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")

        val show =
            files.onTrackList(
                mpvTracks(trackList(subtitle(1, "/cache/0-Stream.pgssub", "eng", "pgs", false)))
            )

        assertEquals("1", show, "shown without a pick")
    }

    @Test
    fun `the default file does not replace a track mpv shows`() {
        val files = files(pgs)
        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")
        val embedded = """{"id":1,"type":"sub","selected":true,"codec":"subrip"}"""

        val show =
            files.onTrackList(
                mpvTracks(
                    trackList(embedded, subtitle(2, "/cache/0-Stream.pgssub", "eng", "pgs", false))
                )
            )

        assertNull(show, "mpv's own choice stays")
    }

    @Test
    fun `a picked file is fetched, listed as picked, and shown once mpv has it`() {
        val files = files(pgs, srt)

        assertEquals("no", files.select(files.id(1)), "nothing shows while it comes")
        assertEquals(listOf(srt.url), files.toFetch(), "the picked file is fetched")
        assertTrue(files.isWaitingFor(1), "the dialog shows the pick")
        assertFalse(files.isWaitingFor(0), "not the default one")
        files.downloadStarted(srt.url)
        files.downloaded(srt.url, "/cache/1-Stream.srt")

        val show =
            files.onTrackList(
                mpvTracks(trackList(subtitle(1, "/cache/1-Stream.srt", "nld", "subrip", false)))
            )

        assertEquals("1", show, "shown without picking it again")
    }

    @Test
    fun `None during the wait keeps the file from showing when it comes`() {
        val files = files(pgs, srt)

        assertEquals("no", files.select("no"), "off")
        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")
        val show =
            files.onTrackList(
                mpvTracks(trackList(subtitle(1, "/cache/0-Stream.pgssub", "eng", "pgs", false)))
            )

        assertNull(show, "stays off")
        assertFalse(files.isWaitingFor(0), "listed as off")
        assertEquals(emptyList(), files.toFetch(), "and nothing is fetched")
    }

    @Test
    fun `a file mpv has is selected by its mpv id`() {
        val files = files(pgs, srt)
        files.downloaded(srt.url, "/cache/1-Stream.srt")
        files.onTrackList(
            mpvTracks(trackList(subtitle(4, "/cache/1-Stream.srt", "nld", "subrip", false)))
        )

        assertEquals("4", files.select(files.id(1)), "mpv's id for it")
        assertEquals("2", files.select("2"), "a track of the stream as it is")
    }

    @Test
    fun `a failed download is tried again while it is wanted, then waits for a new pick`() {
        val files = files(pgs, srt)
        files.downloadStarted(pgs.url)

        val retries =
            (1..MpvSubtitleFiles.MAX_AUTOMATIC_RETRIES).map { files.downloadFailed(pgs.url) }

        assertEquals(MpvSubtitleFiles.MAX_AUTOMATIC_RETRIES - 1, retries.count { it }, "retries")
        assertFalse(files.isWaitingFor(0), "no longer listed as coming")
        files.select(files.id(0))
        assertEquals(listOf(pgs.url), files.toFetch(), "picking it again fetches it again")
    }

    @Test
    fun `a file that is no longer wanted is not tried again`() {
        val files = files(pgs, srt)
        files.downloadStarted(pgs.url)
        files.select(files.id(1))

        assertFalse(files.downloadFailed(pgs.url), "the default one, after another pick")
    }

    @Test
    fun `a file goes to mpv once, after it is there`() {
        val files = files(pgs, srt)
        assertEquals(emptyList(), files.takeToAdd(), "nothing is there yet")

        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")

        assertEquals(listOf(0 to "/cache/0-Stream.pgssub"), files.takeToAdd(), "the download")
        assertEquals(emptyList(), files.takeToAdd(), "once")
    }

    @Test
    fun `the local files of a downloaded item go to mpv as they are`() {
        val local = srt.copy(url = "/storage/item/subtitle.srt")
        val files = files(local)

        assertEquals(emptyList(), files.toFetch(), "nothing to download")
        assertEquals(listOf(0 to local.url), files.takeToAdd(), "the path itself")
    }

    @Test
    fun `a download that ends after the next item started is kept for it`() {
        val files = files(pgs, srt)
        files.downloadStarted(pgs.url)
        files.reset(listOf(pgs), null)

        files.downloaded(pgs.url, "/cache/0-Stream.pgssub")

        assertEquals(listOf(0 to "/cache/0-Stream.pgssub"), files.takeToAdd(), "the same URL")
    }
}
