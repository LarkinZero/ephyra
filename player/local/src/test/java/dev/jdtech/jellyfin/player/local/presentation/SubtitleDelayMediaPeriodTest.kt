package dev.jdtech.jellyfin.player.local.presentation

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.trackselection.FixedTrackSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SubtitleDelayMediaPeriod]: with a positive subtitle delay the subtitle stream
 * starts early enough after a seek or a track switch, and the video still starts at the seek
 * position (#650).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleDelayMediaPeriodTest {

    private val video =
        TrackGroup("video", Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build())
    private val english =
        TrackGroup(
            "en",
            Format.Builder()
                .setId("en")
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .build(),
        )
    private val dutch =
        TrackGroup(
            "nl",
            Format.Builder()
                .setId("nl")
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .build(),
        )

    /**
     * One container, like a progressive period: a seek moves every stream, a new stream starts at
     * the sample at or before the position. Video has a key frame every 2 s and a frame every
     * second; the subtitles have a cue every 5 s (0, 5, 10, ... as in Tracks Movie).
     */
    private inner class FakePeriod : MediaPeriod {
        val seeks = mutableListOf<Long>()
        val selectPositions = mutableListOf<Long>()
        val streams = mutableListOf<FakeStream>()
        var buffered = Long.MAX_VALUE

        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            callback.onPrepared(this)
        }

        override fun maybeThrowPrepareError() = Unit

        override fun getTrackGroups() = TrackGroupArray(video, english, dutch)

        override fun selectTracks(
            selections: Array<out ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray,
            streams: Array<SampleStream?>,
            streamResetFlags: BooleanArray,
            positionUs: Long,
        ): Long {
            selectPositions += positionUs
            for (i in selections.indices) {
                val old = streams[i] as FakeStream?
                if (old != null && (selections[i] == null || !mayRetainStreamFlags[i])) {
                    this.streams.remove(old)
                    streams[i] = null
                }
                val selection = selections[i] ?: continue
                if (streams[i] == null) {
                    val stream =
                        if (selection.trackGroup === video) FakeStream(1, keyFrameEvery = 2)
                        else FakeStream(5, keyFrameEvery = 1, id = selection.trackGroup.id)
                    stream.seekTo(positionUs)
                    this.streams += stream
                    streams[i] = stream
                    streamResetFlags[i] = true
                }
            }
            return positionUs
        }

        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = Unit

        override fun readDiscontinuity() = C.TIME_UNSET

        override fun seekToUs(positionUs: Long): Long {
            seeks += positionUs
            streams.forEach { it.seekTo(positionUs) }
            return positionUs
        }

        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters) =
            positionUs

        override fun getBufferedPositionUs() = buffered

        override fun getNextLoadPositionUs() = buffered

        override fun continueLoading(loadingInfo: LoadingInfo) = false

        override fun isLoading() = false

        override fun reevaluateBuffer(positionUs: Long) = Unit
    }

    /** Samples every [everyS] seconds, a key frame every [keyFrameEvery] samples. */
    private class FakeStream(val everyS: Int, val keyFrameEvery: Int, val id: String = "video") :
        SampleStream {
        var index = 0

        fun timeUs(index: Int) = index * everyS * 1_000_000L

        /** To the key frame at or before [positionUs], as a sample queue seeks. */
        fun seekTo(positionUs: Long) {
            index = (positionUs / (everyS * 1_000_000L)).toInt()
            index -= index % keyFrameEvery
        }

        override fun isReady() = true

        override fun maybeThrowError() = Unit

        override fun readData(holder: FormatHolder, buffer: DecoderInputBuffer, flags: Int): Int {
            buffer.timeUs = timeUs(index)
            if (flags and SampleStream.FLAG_PEEK == 0) index++
            return C.RESULT_BUFFER_READ
        }

        override fun skipData(positionUs: Long): Int {
            val before = index
            var target = (positionUs / (everyS * 1_000_000L)).toInt()
            target -= target % keyFrameEvery
            if (target > index) index = target
            return index - before
        }
    }

    private var delayUs = 0L
    private val inner = FakePeriod()
    private val period =
        SubtitleDelayMediaPeriod(inner) { delayUs }
            .also {
                it.prepare(
                    object : MediaPeriod.Callback {
                        override fun onPrepared(mediaPeriod: MediaPeriod) = Unit

                        override fun onContinueLoadingRequested(source: MediaPeriod) = Unit
                    },
                    0L,
                )
            }

    private val selections: Array<ExoTrackSelection?> =
        arrayOf(FixedTrackSelection(video, 0), FixedTrackSelection(english, 0))
    private val streams = arrayOfNulls<SampleStream>(2)

    private fun select(atS: Int, selections: Array<ExoTrackSelection?> = this.selections): Long =
        period.selectTracks(
            selections,
            BooleanArray(selections.size) { true },
            streams,
            BooleanArray(selections.size),
            atS * 1_000_000L,
        )

    /** The time of the next sample [stream] hands out, in seconds, or null for nothing. */
    private fun next(stream: SampleStream?): Double? {
        val buffer = DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED)
        val result = stream!!.readData(FormatHolder(), buffer, 0)
        return if (result == C.RESULT_BUFFER_READ) buffer.timeUs / 1_000_000.0 else null
    }

    private val videoStream
        get() = streams[0]

    private val textStream
        get() = streams[1]

    @Test
    fun `a forward seek with a positive delay starts the subtitles at the delayed position`() {
        select(0)
        period.discardBuffer(0L, false)
        delayUs = 2_700_000L
        // At 15.5 s the video is due, and with +2.7 s the cue of 12.8 s, which started at 10 s.
        // Seeked to 15.5 s itself, the stream would start with the cue of 15 s.
        assertEquals(15_500_000L, period.seekToUs(15_500_000L))
        assertEquals(listOf(12_800_000L), inner.seeks)
        assertEquals(10.0, next(textStream))
        // The video starts at the key frame before 15.5 s, as a seek there gives it.
        assertEquals(14.0, next(videoStream))
    }

    @Test
    fun `a seek outside the buffer looks back further for a cue that started before a key frame`() {
        select(0)
        period.discardBuffer(20_000_000L, false)
        delayUs = 2_700_000L
        // Back to 15.5 s: the buffer starts at 20 s, so the period reloads anyway.
        assertEquals(15_500_000L, period.seekToUs(15_500_000L))
        assertEquals(listOf(2_800_000L), inner.seeks)
        assertEquals(listOf(0.0, 5.0, 10.0), List(3) { next(textStream) })
        assertEquals(14.0, next(videoStream))
    }

    @Test
    fun `the video waits until the period has buffered up to the seek position`() {
        select(0)
        delayUs = 3_000_000L
        inner.buffered = 0L
        period.seekToUs(30_000_000L)
        inner.buffered = 29_000_000L
        assertFalse(videoStream!!.isReady())
        assertEquals(null, next(videoStream))
        inner.buffered = 30_500_000L
        assertTrue(videoStream!!.isReady())
        assertEquals(30.0, next(videoStream))
        assertEquals(31.0, next(videoStream))
    }

    @Test
    fun `a subtitle track switch with a positive delay starts the new track at the delayed position`() {
        select(0)
        repeat(17) { next(videoStream) }
        val videoBefore = videoStream
        delayUs = 2_700_000L
        val switched: Array<ExoTrackSelection?> =
            arrayOf(selections[0], FixedTrackSelection(dutch, 0))
        val flags = booleanArrayOf(true, false)
        val positionUs = period.selectTracks(switched, flags, streams, BooleanArray(2), 16_000_000L)
        assertEquals(16_000_000L, positionUs)
        // 2.7 s for the delay and 10 s for a cue that started before the reload's key frame.
        assertEquals(3_300_000L, inner.selectPositions.last())
        assertEquals(listOf(0.0, 5.0, 10.0), List(3) { next(textStream) })
        // The video kept its stream and its place.
        assertSame(videoBefore, videoStream)
        assertEquals(17.0, next(videoStream))
    }

    @Test
    fun `an audio or video switch stays at the position`() {
        select(0)
        delayUs = 2_700_000L
        val flags = booleanArrayOf(false, true)
        period.selectTracks(selections, flags, streams, BooleanArray(2), 16_000_000L)
        assertEquals(16_000_000L, inner.selectPositions.last())
    }

    @Test
    fun `without a delay, with a negative one or without subtitles a seek is passed on`() {
        select(0)
        period.seekToUs(15_500_000L)
        delayUs = -2_000_000L
        period.seekToUs(20_500_000L)
        assertEquals(listOf(15_500_000L, 20_500_000L), inner.seeks)
        assertEquals(20.0, next(videoStream))

        delayUs = 2_000_000L
        val videoOnly: Array<ExoTrackSelection?> = arrayOf(selections[0], null)
        period.selectTracks(videoOnly, booleanArrayOf(true, false), streams, BooleanArray(2), 0L)
        period.seekToUs(40_500_000L)
        assertEquals(40_500_000L, inner.seeks.last())
    }

    @Test
    fun `a delay longer than the position starts the subtitles at 0`() {
        select(0)
        delayUs = 10_000_000L
        assertEquals(4_000_000L, period.seekToUs(4_000_000L))
        assertEquals(listOf(0L), inner.seeks)
        assertEquals(0.0, next(textStream))
        assertEquals(4.0, next(videoStream))
    }

    @Test
    fun `a seek without a delay drops a hold that was not released`() {
        select(0)
        delayUs = 3_000_000L
        inner.buffered = 0L
        period.seekToUs(60_000_000L)
        delayUs = 0L
        period.seekToUs(10_000_000L)
        inner.buffered = Long.MAX_VALUE
        assertEquals(10.0, next(videoStream))
    }
}
