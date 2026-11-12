package dev.jdtech.jellyfin.player.local.presentation

import android.content.Context
import dev.jdtech.jellyfin.player.local.R
import java.util.Locale

/** A live snapshot of the selected player tracks and the Jellyfin session's transcode state. */
data class PlaybackDebugInfo(
    val playbackMethod: PlaybackMethod,
    val container: String?,
    val videoCodec: String?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val videoBitrate: Int?,
    val videoFrameRate: Float?,
    val audioCodec: String?,
    val audioBitrate: Int?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    val streamBitrate: Int?,
    val transcodeFrameRate: Float?,
    val hardwareAcceleration: String?,
    val transcodeReasons: List<String>,
)

enum class PlaybackMethod {
    TRANSCODE,
    DIRECT_PLAY,
    LOCAL_FILE,
}

fun PlaybackDebugInfo.toRows(context: Context): List<Pair<String, String>> = buildList {
    add(
        context.getString(R.string.playback_info_mode) to
            context.getString(
                when (playbackMethod) {
                    PlaybackMethod.TRANSCODE -> R.string.playback_info_mode_transcode
                    PlaybackMethod.DIRECT_PLAY -> R.string.playback_info_mode_direct_play
                    PlaybackMethod.LOCAL_FILE -> R.string.playback_info_mode_local
                },
            ),
    )
    add(
        context.getString(R.string.playback_info_video) to
            listOfNotNull(
                videoCodec,
                if (videoWidth != null && videoHeight != null) "${videoWidth}×$videoHeight" else null,
                videoBitrate?.let(::formatBitrate),
            ).joinToString(" · ").ifBlank { "—" },
    )
    add(
        context.getString(R.string.playback_info_audio) to
            listOfNotNull(
                audioCodec,
                audioChannels?.let { "${it} ch" },
                audioSampleRate?.let { "${it / 1000} kHz" },
                audioBitrate?.let(::formatBitrate),
            ).joinToString(" · ").ifBlank { "—" },
    )
    add(context.getString(R.string.playback_info_container) to (container ?: "—"))
    add(
        context.getString(R.string.playback_info_video_fps) to
            (videoFrameRate?.let { "%.2f fps".format(Locale.ROOT, it) } ?: "—"),
    )
    if (streamBitrate != null) {
        add(context.getString(R.string.playback_info_stream_bitrate) to formatBitrate(streamBitrate))
    }
    if (playbackMethod == PlaybackMethod.TRANSCODE) {
        add(
            context.getString(R.string.playback_info_transcode_speed) to
                (transcodeFrameRate?.let { "%.1f fps".format(Locale.ROOT, it) } ?: "—"),
        )
        if (hardwareAcceleration != null) {
            add(context.getString(R.string.playback_info_hardware_acceleration) to hardwareAcceleration)
        }
        if (transcodeReasons.isNotEmpty()) {
            add(
                context.getString(R.string.playback_info_transcode_reasons) to
                    transcodeReasons.joinToString(", "),
            )
        }
    }
}

private fun formatBitrate(bitrate: Int): String =
    if (bitrate >= 1_000_000) "%.1f Mbps".format(Locale.ROOT, bitrate / 1_000_000f)
    else "${bitrate / 1000} kbps"
