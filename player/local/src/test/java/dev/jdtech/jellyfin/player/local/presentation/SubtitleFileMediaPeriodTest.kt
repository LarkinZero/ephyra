package dev.jdtech.jellyfin.player.local.presentation

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.source.CompositeSequenceableLoader
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SequenceableLoader
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SubtitleFileMediaPeriod]: a subtitle file that has not loaded yet does not hold
 * up the video, and its cues are there once it has (#711).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleFileMediaPeriodTest {

    private val format =
        Format.Builder()
            .setId("en")
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("en")
            .build()

    /** Cues every 5 s, each 4 s long, as on the test server's Big Subs Movie. */
    private val cues =
        (0 until 6).map {
            SubtitleFileMediaPeriod.Sample(
                timeUs = it * 5_000_000L,
                endUs = it * 5_000_000L + 4_000_000L,
                data = byteArrayOf(it.toByte()),
            )
        }

    /** A load that ends when the test says so, as a server still extracting. */
    private class HeldLoader : SubtitleFileMediaPeriod.FileLoader {
        var starts = 0
        private var callback: ((Result<List<SubtitleFileMediaPeriod.Sample>>) -> Boolean)? = null
        override var isLoading = false

        override fun start(
            load: () -> List<SubtitleFileMediaPeriod.Sample>,
            callback: (Result<List<SubtitleFileMediaPeriod.Sample>>) -> Boolean,
        ) {
            starts++
            isLoading = true
            this.callback = callback
        }

        /** Ends the load with [result]; returns whether the period asked to try again. */
        fun finish(result: Result<List<SubtitleFileMediaPeriod.Sample>>): Boolean {
            val retry = callback!!.invoke(result)
            isLoading = retry
            return retry
        }

        override fun release() = Unit
    }

    private val loader = HeldLoader()

    private fun period() =
        SubtitleFileMediaPeriod(format, parse = { cues }, loader = loader, read = { ByteArray(0) })
            .also { it.prepare(NoCallback, 0L) }

    private object NoCallback : MediaPeriod.Callback {
        override fun onPrepared(mediaPeriod: MediaPeriod) = Unit

        override fun onContinueLoadingRequested(source: MediaPeriod) = Unit
    }

    private fun MediaPeriod.select(positionUs: Long = 0L): SampleStream {
        val streams = arrayOfNulls<SampleStream>(1)
        selectTracks(
            arrayOf<ExoTrackSelection?>(FixedTrackSelection(trackGroups[0], 0)),
            booleanArrayOf(false),
            streams,
            booleanArrayOf(false),
            positionUs,
        )
        return streams[0]!!
    }

    private fun SampleStream.read(): Pair<Int, DecoderInputBuffer> {
        val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
        return readData(FormatHolder(), buffer, 0) to buffer
    }

    /** Reads the format, which the renderer takes first, and returns the stream. */
    private fun SampleStream.afterFormat(): SampleStream = apply {
        assertEquals(C.RESULT_FORMAT_READ, read().first, "the format comes first")
    }

    @Test
    fun `nothing is loaded before the track is selected`() {
        period()

        assertEquals(0, loader.starts, "a track that is off loads nothing")
    }

    @Test
    fun `a file that is still loading holds nothing up`() {
        val period = period()
        period.select()

        assertTrue(loader.isLoading, "the file loads once the track is selected")
        assertEquals(C.TIME_END_OF_SOURCE, period.bufferedPositionUs, "nothing to buffer")
        assertEquals(C.TIME_END_OF_SOURCE, period.nextLoadPositionUs, "nothing to load")
    }

    @Test
    fun `the video goes on loading while the file loads`() {
        // CompositeSequenceableLoader is what a MergingMediaPeriod loads its children with. It
        // only asks the children that are furthest behind, or behind the playback position.
        val video = FakeLoader(positionUs = 3_000_000L)
        val period = period()
        period.select()
        val merged =
            CompositeSequenceableLoader(
                listOf(video, period),
                listOf(listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_TEXT)),
            )

        merged.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0L).build())

        assertEquals(1, video.continued, "the video loads its next segment")
    }

    @Test
    fun `a child that waits where playback started holds the video back`() {
        // How media3's own subtitle child behaved (#711): while its request waited, its next load
        // position stayed at the start, and the video was not asked to load past its first
        // segment.
        val video = FakeLoader(positionUs = 3_000_000L)
        val waiting = FakeLoader(positionUs = 0L)
        val merged =
            CompositeSequenceableLoader(
                listOf(video, waiting),
                listOf(listOf(C.TRACK_TYPE_VIDEO), listOf(C.TRACK_TYPE_TEXT)),
            )

        merged.continueLoading(LoadingInfo.Builder().setPlaybackPositionUs(0L).build())

        assertEquals(0, video.continued, "the video waits for the subtitle child")
    }

    @Test
    fun `the cue that is due shows once the file is there, without selecting it again`() {
        val period = period()
        val stream = period.select(positionUs = 12_000_000L).afterFormat()
        assertEquals(C.RESULT_NOTHING_READ, stream.read().first, "no cues while the file loads")

        loader.finish(Result.success(cues))
        val (result, buffer) = stream.read()

        assertEquals(C.RESULT_BUFFER_READ, result, "the cues are there")
        assertEquals(10_000_000L, buffer.timeUs, "the cue showing at 12 s comes first")
    }

    @Test
    fun `a seek before the file is there starts at the cue for the new position`() {
        val period = period()
        val stream = period.select().afterFormat()

        period.seekToUs(21_000_000L)
        loader.finish(Result.success(cues))

        assertEquals(20_000_000L, stream.read().second.timeUs, "the cue showing at 21 s")
    }

    @Test
    fun `a seek after the file is there goes back to the cue for that position`() {
        val period = period()
        val stream = period.select(positionUs = 25_000_000L).afterFormat()
        loader.finish(Result.success(cues))
        stream.read()

        period.seekToUs(6_000_000L)

        assertEquals(5_000_000L, stream.read().second.timeUs, "the cue showing at 6 s")
    }

    @Test
    fun `the stream ends after the last cue`() {
        val period = period()
        val stream = period.select(positionUs = 25_000_000L).afterFormat()
        loader.finish(Result.success(cues))
        stream.read()

        val (result, buffer) = stream.read()

        assertEquals(C.RESULT_BUFFER_READ, result, "the end is read as a buffer")
        assertTrue(buffer.isEndOfStream, "after the last cue the stream ends")
    }

    @Test
    fun `a read that times out is tried again`() {
        val period = period()
        period.select()

        val retry = loader.finish(Result.failure(SocketTimeoutException("timeout")))

        assertTrue(retry, "the server may still be extracting")
    }

    @Test
    fun `a file that is not there leaves the track without cues, and picking it again retries`() {
        val period = period()
        val stream = period.select().afterFormat()

        val retry = loader.finish(Result.failure(FileNotFoundException("gone")))

        assertFalse(retry, "a missing file is not tried again by itself")
        assertTrue(stream.read().second.isEndOfStream, "the track ends without cues")
        period.select()
        assertEquals(2, loader.starts, "selecting the track again loads it again")
    }

    @Test
    fun `only errors that can pass are tried again`() {
        fun http(code: Int) =
            HttpDataSource.InvalidResponseCodeException(
                code,
                null,
                null,
                emptyMap(),
                DataSpec.Builder().setUri("http://server/Stream.srt").build(),
                ByteArray(0),
            )

        assertTrue(SubtitleFileMediaPeriod.isRetriable(SocketTimeoutException()), "a timeout")
        assertTrue(SubtitleFileMediaPeriod.isRetriable(IOException()), "a lost connection")
        assertTrue(SubtitleFileMediaPeriod.isRetriable(http(503)), "a server error")
        assertFalse(SubtitleFileMediaPeriod.isRetriable(http(404)), "not found")
        assertFalse(
            SubtitleFileMediaPeriod.isRetriable(
                ParserException.createForMalformedContainer("", null)
            ),
            "a file that does not parse",
        )
    }

    @Test
    fun `the first cue for a position includes a long one that still shows`() {
        val samples =
            listOf(
                SubtitleFileMediaPeriod.Sample(0L, 30_000_000L, byteArrayOf()),
                SubtitleFileMediaPeriod.Sample(5_000_000L, 9_000_000L, byteArrayOf()),
                SubtitleFileMediaPeriod.Sample(10_000_000L, 14_000_000L, byteArrayOf()),
            )

        assertEquals(0, SubtitleFileMediaPeriod.firstSampleIndex(samples, 12_000_000L), "0 s")
        assertEquals(2, SubtitleFileMediaPeriod.firstSampleIndex(samples, 40_000_000L), "40 s")
        assertEquals(0, SubtitleFileMediaPeriod.firstSampleIndex(emptyList(), 1L), "no cues")
    }

    @Test
    fun `a file is parsed into its cues in time order`() {
        val srt =
            "1\n00:00:05,000 --> 00:00:09,000\nLine 2\n\n2\n00:00:00,000 --> 00:00:04,000\nLine 1\n"
        val parser =
            DefaultSubtitleParserFactory()
                .create(Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).build())

        val samples = parseSubtitleFile(parser, srt.toByteArray())

        assertEquals(listOf(0L, 5_000_000L), samples.map { it.timeUs }, "both cues, in order")
        assertEquals(4_000_000L, samples[0].endUs, "with their end")
    }

    private class FakeLoader(private val positionUs: Long) : SequenceableLoader {
        var continued = 0

        override fun getBufferedPositionUs(): Long = positionUs

        override fun getNextLoadPositionUs(): Long = positionUs

        override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
            continued++
            return false
        }

        override fun isLoading(): Boolean = true

        override fun reevaluateBuffer(positionUs: Long) = Unit
    }
}
