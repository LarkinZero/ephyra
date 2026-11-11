package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #510: the media info sheet's content, from the source the page already has. */
class MediaInfoTest {
    private fun stream(
        type: MediaStreamType,
        displayTitle: String? = null,
        title: String = "",
        codec: String = "",
        width: Int? = null,
        height: Int? = null,
        bitRate: Int? = null,
        frameRate: Float? = null,
        bitDepth: Int? = null,
        sampleRate: Int? = null,
    ) =
        FindroidMediaStream(
            title = title,
            displayTitle = displayTitle,
            language = "eng",
            type = type,
            codec = codec,
            profile = null,
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = height,
            width = width,
            videoDoViTitle = null,
            bitRate = bitRate,
            frameRate = frameRate,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
        )

    private fun source(
        vararg streams: FindroidMediaStream,
        container: String? = null,
        bitrate: Int? = null,
    ) =
        FindroidSource(
            id = "s",
            name = "s",
            type = FindroidSourceType.REMOTE,
            path = "",
            size = 58_000_000_000,
            mediaStreams = streams.toList(),
            container = container,
            bitrate = bitrate,
        )

    @Test
    fun `a UHD remux lists every track with its numbers`() {
        val info =
            source(
                    stream(
                        MediaStreamType.VIDEO,
                        displayTitle = "4K HEVC Dolby Vision",
                        width = 3840,
                        height = 2160,
                        bitRate = 58_300_000,
                        frameRate = 23.976025f,
                        bitDepth = 10,
                    ),
                    stream(
                        MediaStreamType.AUDIO,
                        displayTitle = "English - TrueHD 7.1 - Default",
                        bitRate = 4_210_000,
                        bitDepth = 24,
                        sampleRate = 48_000,
                    ),
                    stream(
                        MediaStreamType.AUDIO,
                        displayTitle = "English - Dolby Digital 5.1",
                        bitRate = 640_000,
                        sampleRate = 44_100,
                    ),
                    stream(MediaStreamType.SUBTITLE, displayTitle = "English - PGSSUB"),
                    container = "mkv",
                    bitrate = 62_100_000,
                )
                .toMediaInfo(Locale.US)

        assertEquals("MKV", info.container)
        assertEquals(58_000_000_000, info.sizeBytes)
        assertEquals("62.1 Mbps", info.bitrate)
        assertEquals(
            listOf(
                MediaInfoTrack(
                    "4K HEVC Dolby Vision",
                    listOf("3840×2160", "23.976 fps", "10 bit", "58.3 Mbps"),
                )
            ),
            info.video,
        )
        assertEquals(
            listOf(
                MediaInfoTrack(
                    "English - TrueHD 7.1 - Default",
                    listOf("48 kHz", "24 bit", "4.2 Mbps"),
                ),
                MediaInfoTrack("English - Dolby Digital 5.1", listOf("44.1 kHz", "640 kbps")),
            ),
            info.audio,
        )
        assertEquals(listOf(MediaInfoTrack("English - PGSSUB", emptyList())), info.subtitles)
    }

    @Test
    fun `a download, which stores none of the numbers, still lists its tracks`() {
        val info =
            source(
                    stream(MediaStreamType.VIDEO, displayTitle = "1080p H264 SDR"),
                    stream(MediaStreamType.AUDIO, displayTitle = "English - AAC - Stereo"),
                )
                .toMediaInfo(Locale.US)

        assertNull(info.container)
        assertNull(info.bitrate)
        assertEquals(listOf(MediaInfoTrack("1080p H264 SDR", emptyList())), info.video)
        assertEquals(listOf(MediaInfoTrack("English - AAC - Stereo", emptyList())), info.audio)
        assertEquals(emptyList<MediaInfoTrack>(), info.subtitles)
    }

    @Test
    fun `a track without a display title falls back to its title, then its codec`() {
        val info =
            source(
                    stream(MediaStreamType.AUDIO, displayTitle = " ", title = "Commentary"),
                    stream(MediaStreamType.SUBTITLE, codec = "subrip"),
                )
                .toMediaInfo(Locale.US)

        assertEquals("Commentary", info.audio.single().title)
        assertEquals("SUBRIP", info.subtitles.single().title)
    }

    @Test
    fun `a container that lists its aliases shows the first`() {
        assertEquals("MOV", source(container = "mov,mp4,m4a,3gp,3g2,mj2").toMediaInfo().container)
        assertNull(source(container = "").toMediaInfo().container)
    }

    @Test
    fun `whole numbers lose their decimals and zero is left out`() {
        assertEquals("25 fps", formatFrameRate(25f, Locale.US))
        assertEquals("59.94 fps", formatFrameRate(59.94f, Locale.US))
        assertEquals("96 kHz", formatSampleRate(96_000, Locale.US))
        assertEquals("128 kbps", formatBitrate(128_000, Locale.US))
        assertNull(formatBitrate(0, Locale.US))
        assertNull(formatFrameRate(0f, Locale.US))
        assertNull(formatSampleRate(0, Locale.US))
    }

    @Test
    fun `numbers follow the locale's decimal separator`() {
        assertEquals("62,1 Mbps", formatBitrate(62_100_000, Locale.GERMANY))
        assertEquals("23,976 fps", formatFrameRate(23.976f, Locale.GERMANY))
        assertEquals("44,1 kHz", formatSampleRate(44_100, Locale.GERMANY))
        assertEquals("100 fps", formatFrameRate(100f, Locale.GERMANY))
    }
}
