package dev.jdtech.jellyfin.player.local.presentation

import android.content.Context
import dev.jdtech.jellyfin.player.local.R
import java.util.Locale

/** One audio or video track as shown by the playback diagnostics. */
data class PlaybackTrackInfo(
    val codec: String?,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val frameRate: Float? = null,
    val channels: String? = null,
    val sampleRate: Int? = null,
)

data class PlaybackInfoSection(val title: String, val rows: List<Pair<String, String>>)

/** Original source tracks plus the output stream currently delivered to the player. */
data class PlaybackDebugInfo(
    val playbackMethod: PlaybackMethod,
    val originalContainer: String?,
    val originalVideo: PlaybackTrackInfo?,
    val originalAudio: PlaybackTrackInfo?,
    val outputContainer: String?,
    val outputVideo: PlaybackTrackInfo?,
    val outputAudio: PlaybackTrackInfo?,
    val streamBitrate: Int?,
    val transcodeFrameRate: Float?,
    val hardwareAcceleration: String?,
)

enum class PlaybackMethod {
    TRANSCODE,
    DIRECT_PLAY,
    LOCAL_FILE,
}

fun PlaybackDebugInfo.toSections(context: Context): List<PlaybackInfoSection> {
    val originalRows =
        listOf(
            context.getString(R.string.playback_info_video) to formatVideo(originalVideo),
            context.getString(R.string.playback_info_audio) to formatAudio(originalAudio),
            context.getString(R.string.playback_info_container) to (originalContainer ?: "—"),
        )
    val outputRows =
        buildList {
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
            add(context.getString(R.string.playback_info_video) to formatVideo(outputVideo))
            add(context.getString(R.string.playback_info_audio) to formatAudio(outputAudio))
            add(context.getString(R.string.playback_info_container) to (outputContainer ?: "—"))
            if (streamBitrate != null) {
                add(
                    context.getString(R.string.playback_info_stream_bitrate) to
                        formatBitrate(streamBitrate),
                )
            }
            if (playbackMethod == PlaybackMethod.TRANSCODE) {
                add(
                    context.getString(R.string.playback_info_transcode_speed) to
                        (transcodeFrameRate?.let { "%.1f fps".format(Locale.ROOT, it) } ?: "—"),
                )
                if (hardwareAcceleration != null) {
                    add(
                        context.getString(R.string.playback_info_hardware_acceleration) to
                            hardwareAcceleration,
                    )
                }
            }
        }
    return listOf(
        PlaybackInfoSection(context.getString(R.string.playback_info_original), originalRows),
        PlaybackInfoSection(context.getString(R.string.playback_info_output), outputRows),
    )
}

private fun formatVideo(track: PlaybackTrackInfo?): String =
    track?.let {
        listOfNotNull(
            it.codec,
            if (it.width != null && it.height != null) "${it.width}×${it.height}" else null,
            it.bitrate?.let(::formatBitrate),
            it.frameRate?.let { frameRate -> "%.2f fps".format(Locale.ROOT, frameRate) },
        ).joinToString(" · ").ifBlank { "—" }
    } ?: "—"

private fun formatAudio(track: PlaybackTrackInfo?): String =
    track?.let {
        listOfNotNull(
            it.codec,
            it.channels,
            it.sampleRate?.let { sampleRate -> "${sampleRate / 1000} kHz" },
            it.bitrate?.let(::formatBitrate),
        ).joinToString(" · ").ifBlank { "—" }
    } ?: "—"

private fun formatBitrate(bitrate: Int): String =
    if (bitrate >= 1_000_000) "%.1f Mbps".format(Locale.ROOT, bitrate / 1_000_000f)
    else "${bitrate / 1000} kbps"
