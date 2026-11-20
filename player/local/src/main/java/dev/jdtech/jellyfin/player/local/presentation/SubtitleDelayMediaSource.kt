package dev.jdtech.jellyfin.player.local.presentation

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.text.SubtitleParser

/**
 * CE (#650): gives every period of an item a [SubtitleDelayMediaPeriod], with the subtitle delay of
 * that item. [delayUsForMediaId] runs on the playback thread.
 */
@OptIn(UnstableApi::class)
class SubtitleDelayMediaSource(
    mediaSource: MediaSource,
    private val delayUsForMediaId: (String) -> Long,
) : WrappingMediaSource(mediaSource) {

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        val mediaId = mediaItem.mediaId
        return SubtitleDelayMediaPeriod(super.createPeriod(id, allocator, startPositionUs)) {
            delayUsForMediaId(mediaId)
        }
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        super.releasePeriod((mediaPeriod as SubtitleDelayMediaPeriod).inner)
    }

    /** Wraps every source [factory] creates in a [SubtitleDelayMediaSource]. */
    class Factory(
        private val factory: MediaSource.Factory,
        private val delayUsForMediaId: (String) -> Long,
    ) : MediaSource.Factory {

        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider
        ): MediaSource.Factory = apply {
            factory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        }

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy
        ): MediaSource.Factory = apply {
            factory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        }

        override fun setSubtitleParserFactory(
            subtitleParserFactory: SubtitleParser.Factory
        ): MediaSource.Factory = apply { factory.setSubtitleParserFactory(subtitleParserFactory) }

        @Deprecated("Deprecated in Media3")
        override fun experimentalParseSubtitlesDuringExtraction(
            parseSubtitlesDuringExtraction: Boolean
        ): MediaSource.Factory = apply {
            @Suppress("DEPRECATION")
            factory.experimentalParseSubtitlesDuringExtraction(parseSubtitlesDuringExtraction)
        }

        override fun getSupportedTypes(): IntArray = factory.supportedTypes

        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            SubtitleDelayMediaSource(factory.createMediaSource(mediaItem), delayUsForMediaId)
    }
}
