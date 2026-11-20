package dev.jdtech.jellyfin.core.presentation.ephyra

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import kotlin.test.assertEquals
import org.junit.Test

class SeekBarSegmentsTest {
    private fun segment(type: FindroidSegmentType, start: Long, end: Long) =
        FindroidSegment(type = type, startTicks = start, endTicks = end)

    @Test
    fun `segments become fractions of the runtime`() {
        val result =
            seekBarSegments(
                listOf(
                    segment(FindroidSegmentType.INTRO, 10_000L, 40_000L),
                    segment(FindroidSegmentType.OUTRO, 80_000L, 100_000L),
                ),
                durationMs = 100_000L,
            )
        assertEquals(
            listOf(
                SeekBarSegment(FindroidSegmentType.INTRO, 0.1f, 0.4f),
                SeekBarSegment(FindroidSegmentType.OUTRO, 0.8f, 1f),
            ),
            result,
        )
    }

    @Test
    fun `a segment past the runtime is clamped to it`() {
        val result =
            seekBarSegments(
                listOf(segment(FindroidSegmentType.OUTRO, 60_000L, 9_999_000L)),
                durationMs = 100_000L,
            )
        assertEquals(listOf(SeekBarSegment(FindroidSegmentType.OUTRO, 0.6f, 1f)), result)
    }

    @Test
    fun `a segment wholly past the runtime is dropped`() {
        val result =
            seekBarSegments(
                listOf(segment(FindroidSegmentType.PREVIEW, 120_000L, 130_000L)),
                durationMs = 100_000L,
            )
        assertEquals(emptyList<SeekBarSegment>(), result)
    }

    @Test
    fun `an unknown runtime gives nothing`() {
        val segments = listOf(segment(FindroidSegmentType.INTRO, 0L, 30_000L))
        assertEquals(emptyList<SeekBarSegment>(), seekBarSegments(segments, durationMs = 0L))
        assertEquals(emptyList<SeekBarSegment>(), seekBarSegments(segments, durationMs = -1L))
    }

    @Test
    fun `unknown segments and empty ones are not drawn`() {
        val result =
            seekBarSegments(
                listOf(
                    segment(FindroidSegmentType.UNKNOWN, 0L, 30_000L),
                    segment(FindroidSegmentType.RECAP, 30_000L, 30_000L),
                ),
                durationMs = 100_000L,
            )
        assertEquals(emptyList<SeekBarSegment>(), result)
    }
}
