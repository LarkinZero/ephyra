package dev.jdtech.jellyfin.player.local.presentation

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.StatsDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.Loader
import androidx.media3.extractor.text.CueEncoder
import androidx.media3.extractor.text.SubtitleParser
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import timber.log.Timber

/**
 * CE (#711): a subtitle file loaded next to the stream ([MediaItem.SubtitleConfiguration]), in
 * place of the source media3's DefaultMediaSourceFactory makes for it.
 *
 * media3 loads such a file with a ProgressiveMediaPeriod. While its one request waits for the
 * server, that period's next load position stays where playback started, and the merged period of
 * the item only asks the child that is furthest behind to load more: the video stopped loading
 * after its first segment, and playback waited on BUFFERING until the subtitle came. During a
 * transcode the first request for any of the file's subtitle tracks waits until the server has
 * extracted them all from the video file, which takes minutes for a large file on a slow disk.
 *
 * [SubtitleFileMediaPeriod] loads the file on its own, as soon as the track is selected, and never
 * counts for the buffer: the video loads and plays meanwhile, and the cues are there when the file
 * is. It gives the track the format media3 gives it, so the track dialog, the track selection and
 * the saved track choices see the same track.
 */
@OptIn(UnstableApi::class)
class SubtitleFileMediaSource(
    subtitle: MediaItem.SubtitleConfiguration,
    private val dataSourceFactory: DataSource.Factory,
    private val parserFactory: SubtitleParser.Factory,
) : BaseMediaSource() {

    private val uri: Uri = subtitle.uri
    private val mediaItem = MediaItem.fromUri(subtitle.uri)

    /** The format of the file, as the parser is given it. */
    private val fileFormat =
        Format.Builder()
            .setSampleMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setSelectionFlags(subtitle.selectionFlags)
            .setRoleFlags(subtitle.roleFlags)
            .setLabel(subtitle.label)
            .setId(subtitle.id)
            .build()

    private val parsed = parserFactory.supportsFormat(fileFormat)

    /**
     * The format of the track, as DefaultMediaSourceFactory makes it: the parsed cues, with the
     * file's MIME type as codecs. A format no parser reads keeps its own, and the renderer then
     * reports it as unsupported.
     */
    val trackFormat: Format =
        if (parsed) {
            fileFormat
                .buildUpon()
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(fileFormat.sampleMimeType)
                .setCueReplacementBehavior(parserFactory.getCueReplacementBehavior(fileFormat))
                .build()
        } else {
            fileFormat
        }

    private var transferListener: TransferListener? = null

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        transferListener = mediaTransferListener
        refreshSourceInfo(
            SinglePeriodTimeline(
                /* durationUs= */ C.TIME_UNSET,
                /* isSeekable= */ true,
                /* isDynamic= */ false,
                /* useLiveConfiguration= */ false,
                /* manifest= */ null,
                mediaItem,
            )
        )
    }

    override fun releaseSourceInternal() {
        transferListener = null
    }

    override fun maybeThrowSourceInfoRefreshError() = Unit

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        val parse: ((ByteArray) -> List<SubtitleFileMediaPeriod.Sample>)? =
            if (parsed) {
                { data -> parseSubtitleFile(parserFactory.create(fileFormat), data) }
            } else {
                null
            }
        return SubtitleFileMediaPeriod(trackFormat, parse) { canceled ->
            val dataSource = dataSourceFactory.createDataSource()
            transferListener?.let(dataSource::addTransferListener)
            readSubtitleFile(dataSource, uri, canceled)
        }
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        (mediaPeriod as SubtitleFileMediaPeriod).release()
    }
}

/**
 * Reads the whole file at [uri] from [dataSource], until [canceled]. A cancel while the request
 * still waits for the server ends that wait too: media3's Loader interrupts the loading thread, and
 * OkHttpDataSource cancels its call when interrupted.
 */
@OptIn(UnstableApi::class)
internal fun readSubtitleFile(
    dataSource: DataSource,
    uri: Uri,
    canceled: () -> Boolean = { false },
): ByteArray {
    val stats = StatsDataSource(dataSource)
    try {
        stats.open(DataSpec(uri))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            if (canceled()) throw InterruptedIOException("Subtitle file load canceled")
            val read = stats.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    } finally {
        DataSourceUtil.closeQuietly(stats)
    }
}

private const val READ_BUFFER_BYTES = 16 * 1024

/**
 * Parses [data] into samples of encoded cues, as media3's SubtitleExtractor does, in time order.
 */
@OptIn(UnstableApi::class)
internal fun parseSubtitleFile(
    parser: SubtitleParser,
    data: ByteArray,
): List<SubtitleFileMediaPeriod.Sample> {
    val encoder = CueEncoder()
    val samples = mutableListOf<SubtitleFileMediaPeriod.Sample>()
    try {
        parser.parse(data, SubtitleParser.OutputOptions.allCues()) { cues ->
            samples +=
                SubtitleFileMediaPeriod.Sample(
                    timeUs = cues.startTimeUs,
                    endUs = cues.endTimeUs,
                    data = encoder.encode(cues.cues, cues.durationUs),
                )
        }
    } catch (e: RuntimeException) {
        throw ParserException.createForMalformedContainer("SubtitleParser failed.", e)
    } finally {
        parser.reset()
    }
    return samples.sortedBy { it.timeUs }
}

/**
 * CE (#711): the period of a [SubtitleFileMediaSource]. Prepared at once, with one track. The file
 * is loaded ([read], then [parse]) on a thread of its own once the track is selected; until then
 * the track is there without cues. The period reports nothing to load and nothing to wait for, so
 * the merged period of the item loads and plays the video as if it were not there.
 *
 * A load that fails is tried again (the server answers the first request only once it has extracted
 * the file's tracks, so a read can time out first), up to [MAX_ERRORS] times or until a response
 * says it never will (a 4xx, a file that does not parse). The track then has no cues; selecting it
 * again tries again. A failure never stops playback: the video does not depend on it.
 *
 * Only the file's cues are kept, encoded, for as long as the period lives (media3's own route keeps
 * them twice: in SubtitleExtractor for seeks and in the sample queue). The file's bytes are dropped
 * once they are parsed. Releasing the period ends a load that still runs, see [readSubtitleFile].
 *
 * Only the playback thread uses it, apart from [read] and [parse].
 */
@OptIn(UnstableApi::class)
class SubtitleFileMediaPeriod(
    private val format: Format,
    private val parse: ((ByteArray) -> List<Sample>)?,
    private val loader: FileLoader = MediaLoader(),
    /** Reads the file; the argument says when the load was canceled. */
    private val read: (canceled: () -> Boolean) -> ByteArray,
) : MediaPeriod {

    /** One parsed cue change: [data] is the encoded cues, [endUs] may be [C.TIME_UNSET]. */
    class Sample(val timeUs: Long, val endUs: Long, val data: ByteArray)

    /** Runs the load of the file; a seam for the tests. */
    interface FileLoader {
        val isLoading: Boolean

        /**
         * Runs [load] off the calling thread, with whether it was canceled, and calls back on the
         * calling thread with the result and, for an error, the number of errors so far. The
         * callback returns whether to try again.
         */
        fun start(
            load: (canceled: () -> Boolean) -> List<Sample>,
            callback: (result: Result<List<Sample>>, errorCount: Int) -> Boolean,
        )

        fun release()
    }

    private val trackGroups = TrackGroupArray(TrackGroup("0", format))
    private val streams = mutableListOf<Stream>()

    /** The cues of the file once it is loaded. */
    private var samples: List<Sample>? = null

    /** The load gave up, see the class comment. */
    private var failed = false

    private var released = false

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        callback.onPrepared(this)
    }

    override fun maybeThrowPrepareError() = Unit

    override fun getTrackGroups(): TrackGroupArray = trackGroups

    override fun selectTracks(
        selections: Array<out ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
    ): Long {
        var added = false
        for (i in selections.indices) {
            val stream = streams[i]
            if (stream != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                this.streams.remove(stream)
                streams[i] = null
            }
            if (streams[i] == null && selections[i] != null) {
                val newStream = Stream(positionUs)
                this.streams += newStream
                streams[i] = newStream
                streamResetFlags[i] = true
                added = true
            }
        }
        // Picking the track again after a load gave up tries again.
        if (added && failed) failed = false
        if (this.streams.isNotEmpty()) startLoading()
        return positionUs
    }

    private fun startLoading() {
        val parse = parse ?: return
        if (released || samples != null || failed || loader.isLoading) return
        Timber.d("Subtitle file loading (${format.label ?: format.language})")
        // The bytes go out of scope once parsed; only the samples are kept.
        loader.start(load = { canceled -> parse(read(canceled)) }, callback = ::onLoadResult)
    }

    /** Returns whether to try again; when not, a failed load leaves the track without cues. */
    private fun onLoadResult(result: Result<List<Sample>>, errorCount: Int): Boolean {
        if (released) return false
        result.onSuccess {
            Timber.d("Subtitle file loaded (${format.label ?: format.language}): ${it.size} cues")
            samples = it
            return false
        }
        val error = result.exceptionOrNull()
        Timber.w(error, "Subtitle file did not load (${format.label ?: format.language})")
        if (error is IOException && isRetriable(error) && errorCount < MAX_ERRORS) return true
        failed = true
        return false
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = Unit

    override fun readDiscontinuity(): Long = C.TIME_UNSET

    override fun seekToUs(positionUs: Long): Long {
        for (stream in streams) stream.seekTo(positionUs)
        return positionUs
    }

    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long =
        positionUs

    // Nothing here holds up playback: see the class comment.
    override fun getBufferedPositionUs(): Long = C.TIME_END_OF_SOURCE

    override fun getNextLoadPositionUs(): Long = C.TIME_END_OF_SOURCE

    override fun continueLoading(loadingInfo: LoadingInfo): Boolean = false

    override fun isLoading(): Boolean = loader.isLoading

    override fun reevaluateBuffer(positionUs: Long) = Unit

    fun release() {
        released = true
        loader.release()
    }

    private inner class Stream(positionUs: Long) : SampleStream {
        private var formatSent = false

        /** The next sample to hand out, or [C.INDEX_UNSET] until [positionUs] is resolved. */
        private var nextIndex = C.INDEX_UNSET
        private var positionUs = positionUs

        fun seekTo(positionUs: Long) {
            this.positionUs = positionUs
            nextIndex = C.INDEX_UNSET
        }

        private fun ended(): Boolean = parse == null || failed

        override fun isReady(): Boolean = samples != null || ended()

        override fun maybeThrowError() = Unit

        override fun readData(
            formatHolder: FormatHolder,
            buffer: DecoderInputBuffer,
            readFlags: Int,
        ): Int {
            val peek = readFlags and SampleStream.FLAG_PEEK != 0
            if (readFlags and SampleStream.FLAG_REQUIRE_FORMAT != 0 || !formatSent) {
                formatHolder.format = format
                if (!peek) formatSent = true
                return C.RESULT_FORMAT_READ
            }
            val samples = samples
            if (samples == null) {
                if (!ended()) return C.RESULT_NOTHING_READ
                buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                return C.RESULT_BUFFER_READ
            }
            if (nextIndex == C.INDEX_UNSET) nextIndex = firstSampleIndex(samples, positionUs)
            val sample = samples.getOrNull(nextIndex)
            if (sample == null) {
                buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
                return C.RESULT_BUFFER_READ
            }
            buffer.timeUs = sample.timeUs
            buffer.setFlags(C.BUFFER_FLAG_KEY_FRAME)
            if (readFlags and SampleStream.FLAG_OMIT_SAMPLE_DATA == 0) {
                buffer.ensureSpaceForWrite(sample.data.size)
                buffer.data!!.put(sample.data)
            }
            if (!peek) nextIndex++
            return C.RESULT_BUFFER_READ
        }

        override fun skipData(positionUs: Long): Int {
            val samples = samples ?: return 0
            if (nextIndex == C.INDEX_UNSET) nextIndex = firstSampleIndex(samples, this.positionUs)
            val target = maxOf(nextIndex, firstSampleIndex(samples, positionUs))
            val skipped = target - nextIndex
            nextIndex = target
            return skipped
        }
    }

    /** Runs the load on a media3 [Loader] thread, trying again with media3's default delay. */
    private class MediaLoader : FileLoader, Loader.Callback<MediaLoader.Load> {
        private val loader = Loader("SubtitleFile")
        private var callback: ((Result<List<Sample>>, Int) -> Boolean)? = null

        class Load(private val load: (canceled: () -> Boolean) -> List<Sample>) : Loader.Loadable {
            @Volatile var result: List<Sample>? = null
            @Volatile private var canceled = false

            /** Ends a read that runs; Loader interrupts one that waits for the server. */
            override fun cancelLoad() {
                canceled = true
            }

            override fun load() {
                result = load.invoke { canceled }
            }
        }

        override val isLoading: Boolean
            get() = loader.isLoading

        override fun start(
            load: (canceled: () -> Boolean) -> List<Sample>,
            callback: (result: Result<List<Sample>>, errorCount: Int) -> Boolean,
        ) {
            this.callback = callback
            loader.startLoading(Load(load), this, /* defaultMinRetryCount= */ 0)
        }

        override fun onLoadCompleted(
            loadable: Load,
            elapsedRealtimeMs: Long,
            loadDurationMs: Long,
        ) {
            callback?.invoke(Result.success(loadable.result.orEmpty()), 0)
        }

        override fun onLoadCanceled(
            loadable: Load,
            elapsedRealtimeMs: Long,
            loadDurationMs: Long,
            released: Boolean,
        ) {
            Timber.d("Subtitle file load canceled after $loadDurationMs ms")
        }

        override fun onLoadError(
            loadable: Load,
            elapsedRealtimeMs: Long,
            loadDurationMs: Long,
            error: IOException,
            errorCount: Int,
        ): Loader.LoadErrorAction {
            val retry = callback?.invoke(Result.failure(error), errorCount) == true
            return if (retry) Loader.RETRY else Loader.DONT_RETRY
        }

        override fun release() {
            callback = null
            loader.release()
        }
    }

    companion object {
        /** How often a load is tried before the track is left without cues. */
        const val MAX_ERRORS = 10

        /**
         * Whether a load that failed with [error] can succeed later: not for a file that is not
         * there or does not parse, and not for a request the server turned down (4xx).
         */
        fun isRetriable(error: IOException): Boolean =
            when (error) {
                is ParserException,
                is FileNotFoundException,
                // A bug in the load or the parser, which a retry repeats.
                is Loader.UnexpectedLoaderException -> false
                is HttpDataSource.InvalidResponseCodeException -> error.responseCode !in 400..499
                else -> true
            }

        /**
         * The first sample to hand out for [positionUs]: the last one that starts at or before it,
         * or an earlier one that still shows then.
         */
        fun firstSampleIndex(samples: List<Sample>, positionUs: Long): Int {
            val floor = samples.indexOfLast { it.timeUs <= positionUs }.coerceAtLeast(0)
            val stillShowing =
                samples.subList(0, floor).indexOfFirst {
                    it.endUs != C.TIME_UNSET && it.endUs > positionUs
                }
            return if (stillShowing != -1) stillShowing else floor
        }
    }
}
