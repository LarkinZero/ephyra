package dev.jdtech.jellyfin.player.cast

import dev.jdtech.jellyfin.player.cast.devices.Chromecast
import dev.jdtech.jellyfin.player.cast.devices.ChromecastH265
import kotlin.test.assertTrue
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.jupiter.api.Test

/**
 * The server encodes an HLS transcode to the first video codec of the transcoding profile and
 * copies any codec in the list, so H.264 has to come first or a transcode for an HEVC receiver is
 * encoded to HEVC (#673).
 */
class CastDeviceProfileTest {

    @Test
    fun `every HLS transcoding profile encodes to H264`() {
        val profiles =
            listOf(Chromecast.deviceProfile, ChromecastH265.deviceProfile).flatMap { profile ->
                profile.transcodingProfiles.map { profile.name to it }
            }
        val hls = profiles.filter { (_, it) -> it.protocol == MediaStreamProtocol.HLS }
        assertTrue(hls.isNotEmpty(), message = "no HLS transcoding profile")
        for ((name, it) in hls) {
            assertTrue(
                it.videoCodec.split(",").first().trim() == "h264",
                message = "$name lists ${it.videoCodec}",
            )
        }
    }
}
