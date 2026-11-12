package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.utils.VideoQualities
import dev.jdtech.jellyfin.utils.VideoResolution
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A lowered bitrate alone leaves the server at the source resolution: the "720p - 4 Mbps" rung
 * came back as a 1920x1080 transcode at 4 Mbps. The rung's resolution has to travel on the stream
 * URL as `MaxWidth`/`MaxHeight`.
 */
class TranscodeResolutionTest {
    private val itemId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun repository(base: String) =
        Proxy.newProxyInstance(
            JellyfinRepository::class.java.classLoader,
            arrayOf(JellyfinRepository::class.java),
        ) { _, method, _ ->
            if (method.name == "getBaseUrl") base else throw NotImplementedError(method.name)
        } as JellyfinRepository

    private fun source(transcodingUrl: String?) =
        MediaSourceInfo(
            protocol = MediaProtocol.FILE,
            id = "source",
            type = MediaSourceType.DEFAULT,
            isRemote = false,
            readAtNativeFramerate = false,
            ignoreDts = false,
            ignoreIndex = false,
            genPtsInput = false,
            supportsTranscoding = true,
            supportsDirectStream = true,
            supportsDirectPlay = true,
            isInfiniteStream = false,
            useMostCompatibleTranscodingProfile = false,
            requiresOpening = false,
            requiresClosing = false,
            requiresLooping = false,
            supportsProbing = true,
            transcodingUrl = transcodingUrl,
            transcodingSubProtocol = MediaStreamProtocol.HLS,
            hasSegments = false,
        )

    /** The source's URL as playback gets it when the quality rung pins [maxWidth]×[maxHeight]. */
    private fun sourcePath(
        transcodingUrl: String?,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
    ): String = runBlocking {
        source(transcodingUrl)
            .toFindroidSource(repository("https://jf.example"), itemId, false, maxWidth, maxHeight)
            .path
    }

    @Test
    fun `a capped rung pins the hls manifest to its resolution`() {
        assertEquals(
            "https://jf.example/videos/$itemId/master.m3u8?VideoBitrate=3744001" +
                "&MaxWidth=1280&MaxHeight=720",
            sourcePath("/videos/$itemId/master.m3u8?VideoBitrate=3744001", 1280, 720),
        )
    }

    @Test
    fun `a progressive transcode url gets the same parameters`() {
        assertEquals(
            "https://jf.example/videos/$itemId/stream.mp4?Static=false&MaxWidth=854&MaxHeight=480",
            sourcePath("/videos/$itemId/stream.mp4?Static=false", 854, 480),
        )
    }

    @Test
    fun `a transcode url without a query starts one`() {
        assertEquals(
            "https://jf.example/videos/$itemId/stream.mp4?MaxWidth=640&MaxHeight=360",
            sourcePath("/videos/$itemId/stream.mp4", 640, 360),
        )
    }

    @Test
    fun `the original quality leaves the transcode url alone`() {
        assertEquals(
            "https://jf.example/videos/$itemId/master.m3u8?VideoBitrate=3744001",
            sourcePath("/videos/$itemId/master.m3u8?VideoBitrate=3744001"),
        )
    }

    @Test
    fun `a direct play source is untouched`() {
        // No transcodingUrl: the player gets the original file, at its own resolution.
        assertEquals("", sourcePath(null, 1280, 720))
    }

    @Test
    fun `the ladder pins the resolution its label names`() {
        assertEquals(1280 to 720, VideoQualities.resolution(4_000_000).widthAndHeight())
        assertEquals(1920 to 1080, VideoQualities.resolution(10_000_000).widthAndHeight())
        assertEquals(3840 to 2160, VideoQualities.resolution(80_000_000).widthAndHeight())
        assertEquals(854 to 480, VideoQualities.resolution(1_500_000).widthAndHeight())
        assertNull(VideoQualities.resolution(0))
    }

    private fun VideoResolution?.widthAndHeight() = this?.let { it.width to it.height }
}
