package dev.jdtech.jellyfin.presentation.film.components

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** CE (#672): the thumb's position, its target and how often a drag jumps. */
class LetterFastScrollerTest {
    @Test
    fun fractionAndTargetAreInverse() {
        val maxFirst = 580
        for (first in listOf(0, 1, 145, 290, 579, 580)) {
            assertEquals(first, fastScrollTarget(fastScrollFraction(first, maxFirst), maxFirst))
        }
    }

    @Test
    fun fractionIsClampedAndZeroWithoutRoom() {
        assertEquals(0f, fastScrollFraction(10, 0))
        assertEquals(1f, fastScrollFraction(700, 580))
        assertEquals(0f, fastScrollTarget(-0.5f, 580).toFloat())
        assertEquals(580, fastScrollTarget(1.5f, 580))
        assertEquals(0, fastScrollTarget(0.5f, -3))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dragJumpsAtMostOncePerIntervalAndEndsOnTheLastTarget() = runTest {
        // A drag event every 16 ms for one second, each to a new target.
        val targets = flow {
            for (i in 0 until 60) {
                emit(i * 10)
                delay(16)
            }
        }
        val timed = mutableListOf<Pair<Long, Int>>()
        targets.throttledJumps(JUMP_INTERVAL_MS).collect { timed += currentTime to it }

        assertEquals(590, timed.last().second)
        assertEquals(0, timed.first().second)
        // 60 events over ~1 s: about ten jumps, never two within the interval.
        assertTrue(timed.size in 9..12, message = "jumps: ${timed.size}")
        timed.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= JUMP_INTERVAL_MS) }
    }

    @Test
    fun repeatedTargetsDoNotJumpAgain() = runTest {
        val jumps = flow { repeat(5) { emit(42) } }.throttledJumps(JUMP_INTERVAL_MS).toList()
        assertEquals(listOf(42), jumps)
    }
}
