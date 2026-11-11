package dev.jdtech.jellyfin.film.domain

import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSource
import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * CE (#510): what the media info sheet shows for one version of a movie or episode. Built from the
 * source the page already has when the sheet opens, so it costs no request and nothing until asked.
 */
data class MediaInfo(
    /** The container in capitals ("MKV"), or null when the server did not say. */
    val container: String?,
    val sizeBytes: Long,
    /** The whole file's bitrate ("62.1 Mbps"), or null. */
    val bitrate: String?,
    val video: List<MediaInfoTrack>,
    val audio: List<MediaInfoTrack>,
    val subtitles: List<MediaInfoTrack>,
)

data class MediaInfoTrack(
    /** The server's own description: "4K HEVC HDR10", "English - TrueHD 7.1 - Default". */
    val title: String,
    /** The numbers behind it, in reading order: "3840×2160", "23.976 fps", "10 bit", "58.3 Mbps". */
    val details: List<String>,
)

fun FindroidSource.toMediaInfo(locale: Locale = Locale.getDefault()): MediaInfo {
    fun tracks(type: MediaStreamType) =
        mediaStreams.filter { it.type == type }.map { it.toTrack(locale) }
    return MediaInfo(
        // A container can be a list of the names a format goes by: "mov,mp4,m4a,3gp,3g2,mj2".
        container =
            container?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT),
        sizeBytes = size,
        bitrate = bitrate?.let { formatBitrate(it, locale) },
        video = tracks(MediaStreamType.VIDEO),
        audio = tracks(MediaStreamType.AUDIO),
        subtitles = tracks(MediaStreamType.SUBTITLE),
    )
}

private fun FindroidMediaStream.toTrack(locale: Locale): MediaInfoTrack {
    val name =
        displayTitle?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it.isNotBlank() }
            ?: codec.uppercase(Locale.ROOT)
    val details =
        when (type) {
            MediaStreamType.VIDEO ->
                listOfNotNull(
                    if (width != null && height != null) "$width×$height" else null,
                    frameRate?.let { formatFrameRate(it, locale) },
                    bitDepth?.let { "$it bit" },
                    bitRate?.let { formatBitrate(it, locale) },
                )
            MediaStreamType.AUDIO ->
                listOfNotNull(
                    sampleRate?.let { formatSampleRate(it, locale) },
                    bitDepth?.let { "$it bit" },
                    bitRate?.let { formatBitrate(it, locale) },
                )
            else -> emptyList()
        }
    return MediaInfoTrack(title = name, details = details)
}

/** 62 100 000 is "62.1 Mbps", 640 000 is "640 kbps". Zero or less is not a bitrate. */
internal fun formatBitrate(bitsPerSecond: Int, locale: Locale): String? =
    when {
        bitsPerSecond <= 0 -> null
        bitsPerSecond >= 1_000_000 -> String.format(locale, "%.1f Mbps", bitsPerSecond / 1e6)
        else -> String.format(locale, "%d kbps", Math.round(bitsPerSecond / 1e3))
    }

/** 23.976025 is "23.976 fps", 25.0 is "25 fps". */
internal fun formatFrameRate(framesPerSecond: Float, locale: Locale): String? {
    if (framesPerSecond <= 0f) return null
    return "${trimmed(framesPerSecond.toDouble(), 3, locale)} fps"
}

/** 48 000 is "48 kHz", 44 100 is "44.1 kHz". */
internal fun formatSampleRate(hertz: Int, locale: Locale): String? {
    if (hertz <= 0) return null
    return "${trimmed(hertz / 1e3, 1, locale)} kHz"
}

/** [value] with at most [decimals] decimals and no trailing zeros. */
private fun trimmed(value: Double, decimals: Int, locale: Locale): String {
    val text = String.format(locale, "%.${decimals}f", value)
    val separator = text.firstOrNull { !it.isDigit() } ?: return text
    return text.trimEnd('0').trimEnd(separator)
}
