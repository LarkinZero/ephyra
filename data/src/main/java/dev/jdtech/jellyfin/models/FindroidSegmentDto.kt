package dev.jdtech.jellyfin.models

import androidx.room3.Entity
import java.util.UUID

/**
 * CE (#693): `startTicks` is part of the key, so an item with two segments of one type (two
 * commercial breaks) keeps both.
 */
@Entity(tableName = "segments", primaryKeys = ["itemId", "type", "startTicks"])
data class FindroidSegmentDto(
    val itemId: UUID,
    val type: FindroidSegmentType,
    val startTicks: Long,
    val endTicks: Long,
)

fun FindroidSegment.toFindroidSegmentsDto(itemId: UUID): FindroidSegmentDto {
    return FindroidSegmentDto(
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )
}
