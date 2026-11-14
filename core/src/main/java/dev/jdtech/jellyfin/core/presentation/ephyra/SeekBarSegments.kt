package dev.jdtech.jellyfin.core.presentation.ephyra

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType

/**
 * CE (#681): a media segment's range on a seek bar, as fractions of the runtime, so the phone, TV
 * and Cast bars draw the same thing.
 */
data class SeekBarSegment(val type: FindroidSegmentType, val start: Float, val end: Float)

/**
 * The ranges of [segments] on a bar for an item of [durationMs]. Each is clamped to the runtime (a
 * segment can end past it); an unknown runtime, an [FindroidSegmentType.UNKNOWN] segment (it says
 * nothing about the content) and one that is empty after clamping give nothing.
 */
fun seekBarSegments(segments: List<FindroidSegment>, durationMs: Long): List<SeekBarSegment> {
    if (durationMs <= 0L) return emptyList()
    return segments.mapNotNull { segment ->
        if (segment.type == FindroidSegmentType.UNKNOWN) return@mapNotNull null
        val start = (segment.startTicks.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        val end = (segment.endTicks.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        if (end > start) SeekBarSegment(segment.type, start, end) else null
    }
}

/**
 * The colour of a segment of [type] on the seek bar (docs/identity/VISION.md §3.4): neutral roles
 * that stay clear of `primary` (progress) and `tertiary` (yours). Bars draw it on the part of the
 * track that hasn't played; the played part stays `primary`.
 */
fun ColorScheme.seekBarSegmentColor(type: FindroidSegmentType): Color =
    when (type) {
        FindroidSegmentType.INTRO -> secondary
        FindroidSegmentType.RECAP -> onSecondaryContainer
        FindroidSegmentType.OUTRO -> onSurfaceVariant
        FindroidSegmentType.PREVIEW -> outline
        FindroidSegmentType.COMMERCIAL -> error
        FindroidSegmentType.UNKNOWN -> outlineVariant
    }
