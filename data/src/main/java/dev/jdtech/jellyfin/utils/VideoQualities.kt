package dev.jdtech.jellyfin.utils

/** Pixel size a quality rung transcode is pinned to. */
data class VideoResolution(val width: Int, val height: Int)

/**
 * The bitrate ladder offered by quality selectors (player streaming quality and
 * download quality), taken from jellyfin-web's quality options. 0 = no cap: the
 * server uses the original file whenever the profile allows it.
 *
 * Each rung carries a resolution as well as a bitrate. The server does not
 * downscale on its own when only `MaxStreamingBitrate` is lowered — it keeps the
 * source resolution at the lower bitrate (a 1080p source on the "720p - 4 Mbps"
 * rung was transcoded 1920x1080 at 4 Mbps). Only `MaxWidth`/`MaxHeight` on the
 * stream URL, or a width condition in the device profile, make it scale; the
 * former is what [VideoResolution] is passed on for. The label would
 * otherwise promise a resolution that is never delivered.
 */
object VideoQualities {
    val bitrates: List<Int> = listOf(
        0,
        120_000_000,
        80_000_000,
        40_000_000,
        20_000_000,
        10_000_000,
        8_000_000,
        6_000_000,
        4_000_000,
        3_000_000,
        1_500_000,
        720_000,
    )

    /**
     * Display label for a ladder rung, or null for the 0 ("original") rung —
     * callers localize that one themselves.
     */
    fun label(bitrate: Int): String? = when (bitrate) {
        120_000_000 -> "4K - 120 Mbps"
        80_000_000 -> "4K - 80 Mbps"
        40_000_000 -> "1080p - 40 Mbps"
        20_000_000 -> "1080p - 20 Mbps"
        10_000_000 -> "1080p - 10 Mbps"
        8_000_000 -> "720p - 8 Mbps"
        6_000_000 -> "720p - 6 Mbps"
        4_000_000 -> "720p - 4 Mbps"
        3_000_000 -> "480p - 3 Mbps"
        1_500_000 -> "480p - 1.5 Mbps"
        720_000 -> "360p - 720 kbps"
        else -> null
    }

    /**
     * Largest picture a rung delivers, or null when the rung caps nothing (0,
     * or a bitrate off the ladder). The server fits the source into this box,
     * keeping its aspect ratio, and never upscales.
     */
    fun resolution(bitrate: Int?): VideoResolution? = when (bitrate) {
        120_000_000, 80_000_000 -> VideoResolution(3840, 2160)
        40_000_000, 20_000_000, 10_000_000 -> VideoResolution(1920, 1080)
        8_000_000, 6_000_000, 4_000_000 -> VideoResolution(1280, 720)
        3_000_000, 1_500_000 -> VideoResolution(854, 480)
        720_000 -> VideoResolution(640, 360)
        else -> null
    }
}
