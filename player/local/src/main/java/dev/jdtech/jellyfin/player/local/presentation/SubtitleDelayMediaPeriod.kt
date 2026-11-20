package dev.jdtech.jellyfin.player.local.presentation

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

/**
 * CE (#650): positions the subtitle streams of a [MediaPeriod] for a positive subtitle delay.
 *
 * [SubtitleOffsetRenderer] shows a delay D by rendering the subtitles at P - D while the video is
 * at P, so after a seek to P, or after a subtitle track switch at P, the text stream has to start
 * at P - D: the cue that is due started before P. A stream of [inner] cannot be seeked on its own
 * (the subtitles of a container share a period, and so one loader, with the video), so on a seek or
 * a subtitle track switch [inner] is moved to P - D as a whole and every stream that is not a
 * subtitle stream is held back and skipped to P, where the player expects it, as soon as [inner]
 * has buffered up to P. The video and audio start at the same key frame and sample as a seek to P
 * would give them; the cost is loading D more seconds (10 s more where [inner] has to reload, see
 * [seekToUs]).
 *
 * Nothing changes without a subtitle track or with a delay of 0 or less: a negative delay renders
 * the subtitles after P, and the stream that starts at P has those.
 *
 * [delayUsProvider] gives the delay of this period's item, in microseconds. Only the playback
 * thread uses this class.
 */
@OptIn(UnstableApi::class)
class SubtitleDelayMediaPeriod(val inner: MediaPeriod, private val delayUsProvider: () -> Long) :
    MediaPeriod, MediaPeriod.Callback {

    private var callback: MediaPeriod.Callback? = null

    /** The non-subtitle streams handed out, so a seek can hold them. */
    private val heldStreams = mutableListOf<HeldSampleStream>()

    /** Whether a subtitle stream is enabled: without one, nothing is moved. */
    private var hasTextStream = false

    /** The last position [inner] was moved to for a delay, and the position the player asked. */
    private var movedInnerPositionUs = C.TIME_UNSET
    private var movedOuterPositionUs = C.TIME_UNSET

    private fun leadUs(): Long = delayUsProvider().coerceAtLeast(0L)

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        inner.prepare(this, positionUs)
    }

    override fun maybeThrowPrepareError() = inner.maybeThrowPrepareError()

    override fun getTrackGroups(): TrackGroupArray = inner.trackGroups

    override fun getStreamKeys(trackSelections: List<ExoTrackSelection>): List<StreamKey> =
        inner.getStreamKeys(trackSelections)

    override fun selectTracks(
        selections: Array<out ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
    ): Long {
        val innerStreams = arrayOfNulls<SampleStream>(streams.size)
        for (i in streams.indices) {
            val stream = streams[i]
            innerStreams[i] = if (stream is HeldSampleStream) stream.upstream else stream
        }
        // Only a subtitle stream that starts now needs the earlier position. An audio or video
        // switch stays at P: moving it would reload what is buffered for nothing.
        val newTextStream =
            selections.indices.any { i ->
                selections[i]?.isText() == true &&
                    (innerStreams[i] == null || !mayRetainStreamFlags[i])
            }
        // A container keeps no subtitles behind the position for a track that is off, so the new
        // track starts with a reload at the earlier position: one that looks back further.
        val leadUs =
            if (newTextStream) leadUs().let { if (it > 0) it + RELOAD_LOOKBACK_US else 0 } else 0L
        val innerPositionUs = (positionUs - leadUs).coerceAtLeast(0L)
        val selectedPositionUs =
            inner.selectTracks(
                selections,
                mayRetainStreamFlags,
                innerStreams,
                streamResetFlags,
                innerPositionUs,
            )
        val moved = innerPositionUs != positionUs && selectedPositionUs == innerPositionUs

        heldStreams.clear()
        hasTextStream = false
        for (i in streams.indices) {
            val innerStream = innerStreams[i]
            val old = streams[i]
            streams[i] =
                when {
                    innerStream == null -> null
                    selections[i]?.isText() == true -> {
                        hasTextStream = true
                        innerStream
                    }
                    old is HeldSampleStream && old.upstream === innerStream -> old
                    else -> HeldSampleStream(innerStream, this)
                }
            val held = streams[i] as? HeldSampleStream ?: continue
            heldStreams += held
            // A stream that kept its place was not moved; one that starts again was.
            if (streamResetFlags[i]) held.holdUntil(if (moved) positionUs else C.TIME_UNSET)
        }
        return if (moved) remember(innerPositionUs, positionUs) else forget(selectedPositionUs)
    }

    override fun seekToUs(positionUs: Long): Long {
        var leadUs = if (hasTextStream) leadUs() else 0L
        // A seek outside what is buffered reloads, and a reload starts at a key frame: a cue that
        // started before it is not read. Looking back further then costs only the loading of those
        // seconds. A seek inside the buffer reads the cues that are kept, so it is left there.
        if (leadUs > 0 && !insideBuffer(positionUs - leadUs, positionUs)) {
            leadUs += RELOAD_LOOKBACK_US
        }
        val innerPositionUs = (positionUs - leadUs).coerceAtLeast(0L)
        val seekedPositionUs = inner.seekToUs(innerPositionUs)
        // An unseekable source goes to 0 whatever it is asked: then there is nothing to hold.
        if (seekedPositionUs != innerPositionUs || innerPositionUs == positionUs) {
            heldStreams.forEach { it.holdUntil(C.TIME_UNSET) }
            return forget(seekedPositionUs)
        }
        heldStreams.forEach { it.holdUntil(positionUs) }
        return remember(innerPositionUs, positionUs)
    }

    override fun readDiscontinuity(): Long {
        val discontinuityUs = inner.readDiscontinuity()
        // The inner period reports the position it was moved to; the player knows the one it
        // asked for.
        return if (discontinuityUs != C.TIME_UNSET && discontinuityUs == movedInnerPositionUs) {
            movedOuterPositionUs
        } else {
            discontinuityUs
        }
    }

    private fun remember(innerPositionUs: Long, outerPositionUs: Long): Long {
        movedInnerPositionUs = innerPositionUs
        movedOuterPositionUs = outerPositionUs
        return outerPositionUs
    }

    private fun forget(positionUs: Long): Long {
        movedInnerPositionUs = C.TIME_UNSET
        movedOuterPositionUs = C.TIME_UNSET
        return positionUs
    }

    /**
     * Whether [inner] still has the video and audio from [fromUs] to [toUs]: the player discards
     * what lies before the playback position, which it passes to [discardBuffer].
     */
    private fun insideBuffer(fromUs: Long, toUs: Long): Boolean {
        val bufferedUs = inner.bufferedPositionUs
        return discardedToUs != C.TIME_UNSET &&
            fromUs >= discardedToUs &&
            (bufferedUs == C.TIME_END_OF_SOURCE || toUs <= bufferedUs)
    }

    /** The position the player last discarded the buffer up to. */
    private var discardedToUs = C.TIME_UNSET

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        discardedToUs = positionUs
        inner.discardBuffer(positionUs, toKeyframe)
    }

    override fun setUsesStreamPrerollFlags() = inner.setUsesStreamPrerollFlags()

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long =
        inner.getAdjustedSeekPositionUs(positionUs, seekParameters)

    override fun getBufferedPositionUs(): Long = inner.bufferedPositionUs

    override fun getNextLoadPositionUs(): Long = inner.nextLoadPositionUs

    override fun continueLoading(loadingInfo: LoadingInfo): Boolean =
        inner.continueLoading(loadingInfo)

    override fun isLoading(): Boolean = inner.isLoading

    override fun reevaluateBuffer(positionUs: Long) = inner.reevaluateBuffer(positionUs)

    override fun setEndPositionUs(endPositionUs: Long): Long = inner.setEndPositionUs(endPositionUs)

    override fun onPrepared(mediaPeriod: MediaPeriod) {
        callback?.onPrepared(this)
    }

    override fun onContinueLoadingRequested(source: MediaPeriod) {
        callback?.onContinueLoadingRequested(this)
    }

    private fun ExoTrackSelection.isText(): Boolean = trackGroup.type == C.TRACK_TYPE_TEXT

    private companion object {
        /**
         * How long before the delayed position a reload starts, for a cue that started before the
         * key frame it would start at and is still showing.
         */
        const val RELOAD_LOOKBACK_US = 10_000_000L
    }

    /**
     * A stream that is not a subtitle stream. After [inner] was moved back for the delay it hands
     * out nothing until [inner] has buffered up to the position the player asked for, then skips to
     * it: to the key frame at or before it, as a seek there does.
     */
    class HeldSampleStream(
        val upstream: SampleStream,
        private val period: SubtitleDelayMediaPeriod,
    ) : SampleStream {

        private var holdUntilUs = C.TIME_UNSET

        fun holdUntil(positionUs: Long) {
            holdUntilUs = positionUs
        }

        /** True once the stream is at the position it was held for, or was not held. */
        private fun release(): Boolean {
            if (holdUntilUs == C.TIME_UNSET) return true
            val bufferedUs = period.inner.bufferedPositionUs
            if (bufferedUs != C.TIME_END_OF_SOURCE && bufferedUs < holdUntilUs) return false
            upstream.skipData(holdUntilUs)
            holdUntilUs = C.TIME_UNSET
            return true
        }

        override fun isReady(): Boolean = release() && upstream.isReady()

        override fun maybeThrowError() = upstream.maybeThrowError()

        override fun readData(
            formatHolder: FormatHolder,
            buffer: DecoderInputBuffer,
            readFlags: Int,
        ): Int {
            // The format read takes no sample, and the renderer needs it to set up.
            if (readFlags and SampleStream.FLAG_REQUIRE_FORMAT == 0 && !release()) {
                return C.RESULT_NOTHING_READ
            }
            return upstream.readData(formatHolder, buffer, readFlags)
        }

        override fun skipData(positionUs: Long): Int =
            if (release()) upstream.skipData(positionUs) else 0

        override fun getFlags(): Int = upstream.flags
    }
}
