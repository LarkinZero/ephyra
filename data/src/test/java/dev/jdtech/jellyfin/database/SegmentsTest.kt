package dev.jdtech.jellyfin.database

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dev.jdtech.jellyfin.models.FindroidSegmentDto
import dev.jdtech.jellyfin.models.FindroidSegmentType
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CE (#693): a downloaded item keeps every segment the server has for it, also when two have the
 * same type, and a new download of the item replaces the segments of the previous one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SegmentsTest {
    private val itemId = UUID.fromString("00000000-0000-0000-0000-000000000005")
    private val otherId = UUID.fromString("00000000-0000-0000-0000-000000000006")

    private val room =
        Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                ServerDatabase::class.java,
            )
            .setDriver(AndroidSQLiteDriver())
            .build()
    private val database = room.getServerDatabaseDao()

    @After fun close() = room.close()

    private fun segment(id: UUID, type: FindroidSegmentType, start: Long, end: Long) =
        FindroidSegmentDto(itemId = id, type = type, startTicks = start, endTicks = end)

    @Test
    fun `two segments of one type are both kept, in playback order`() = runBlocking {
        val second = segment(itemId, FindroidSegmentType.COMMERCIAL, 9_000, 10_000)
        val intro = segment(itemId, FindroidSegmentType.INTRO, 0, 500)
        val first = segment(itemId, FindroidSegmentType.COMMERCIAL, 3_000, 4_000)

        database.replaceSegments(itemId, listOf(second, intro, first))

        assertEquals(listOf(intro, first, second), database.getSegments(itemId))
    }

    @Test
    fun `a new download replaces the earlier segments of that item only`() = runBlocking {
        val other = segment(otherId, FindroidSegmentType.INTRO, 0, 500)
        database.replaceSegments(otherId, listOf(other))
        database.replaceSegments(
            itemId,
            listOf(segment(itemId, FindroidSegmentType.COMMERCIAL, 3_000, 4_000)),
        )

        val moved = segment(itemId, FindroidSegmentType.COMMERCIAL, 5_000, 6_000)
        database.replaceSegments(itemId, listOf(moved))

        assertEquals(listOf(moved), database.getSegments(itemId))
        assertEquals(listOf(other), database.getSegments(otherId))
    }

    @Test
    fun `deleting an item's segments leaves other items alone`() = runBlocking {
        val other = segment(otherId, FindroidSegmentType.OUTRO, 7_000, 8_000)
        database.replaceSegments(otherId, listOf(other))
        database.replaceSegments(itemId, listOf(segment(itemId, FindroidSegmentType.INTRO, 0, 500)))

        database.deleteSegments(itemId)

        assertEquals(emptyList<FindroidSegmentDto>(), database.getSegments(itemId))
        assertEquals(listOf(other), database.getSegments(otherId))
    }
}
