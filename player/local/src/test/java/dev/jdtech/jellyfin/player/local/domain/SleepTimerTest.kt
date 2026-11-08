package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SleepTimer] (#298). Drives the wall clock manually. */
class SleepTimerTest {

    @Test
    fun `a duration counts down and is due at its end`() {
        val timer = SleepTimer()
        timer.set(SleepTimerOption.Duration(15), nowMs = 1_000L, itemId = "a")

        assertEquals(15 * 60_000L, timer.remainingMs(nowMs = 1_000L))
        assertFalse(timer.isDue(nowMs = 1_000L + 15 * 60_000L - 1))
        assertTrue(timer.isDue(nowMs = 1_000L + 15 * 60_000L))
        assertEquals(0L, timer.remainingMs(nowMs = 1_000L + 20 * 60_000L))
        assertFalse(timer.stopsAtEndOfItem)
    }

    @Test
    fun `a duration survives a change of item`() {
        val timer = SleepTimer()
        timer.set(SleepTimerOption.Duration(30), nowMs = 0L, itemId = "a")

        assertFalse(timer.onItemChanged("b"))
        assertEquals(SleepTimerOption.Duration(30), timer.option)
        assertEquals(10 * 60_000L, timer.remainingMs(nowMs = 20 * 60_000L))
    }

    @Test
    fun `end of item stops at the end and ends when the item is left`() {
        val timer = SleepTimer()
        timer.set(SleepTimerOption.EndOfItem, nowMs = 0L, itemId = "a")

        assertTrue(timer.stopsAtEndOfItem)
        assertNull(timer.remainingMs(nowMs = 0L))
        assertFalse(timer.isDue(nowMs = Long.MAX_VALUE))
        // A reload of the same item (a quality switch) keeps it.
        assertFalse(timer.onItemChanged("a"))
        assertTrue(timer.onItemChanged("b"))
        assertEquals(SleepTimerOption.Off, timer.option)
        assertFalse(timer.stopsAtEndOfItem)
    }

    @Test
    fun `firing holds autoplay until playback resumes`() {
        val timer = SleepTimer()
        timer.set(SleepTimerOption.Duration(15), nowMs = 0L, itemId = "a")

        timer.fire()
        assertEquals(SleepTimerOption.Off, timer.option)
        assertTrue(timer.holdsAutoPlay)
        assertTrue(timer.stopsAtEndOfItem)
        assertFalse(timer.isDue(nowMs = Long.MAX_VALUE))

        timer.onResumed()
        assertFalse(timer.stopsAtEndOfItem)
    }

    @Test
    fun `clearing turns it off and releases autoplay`() {
        val timer = SleepTimer()
        timer.set(SleepTimerOption.EndOfItem, nowMs = 0L, itemId = "a")
        timer.fire()

        timer.clear()
        assertEquals(SleepTimerOption.Off, timer.option)
        assertFalse(timer.stopsAtEndOfItem)
    }

    @Test
    fun `the debug choice is only offered in debug builds`() {
        val release = SleepTimerOption.choices(debug = false)
        assertEquals(
            listOf(
                SleepTimerOption.Off,
                SleepTimerOption.Duration(15),
                SleepTimerOption.Duration(30),
                SleepTimerOption.Duration(45),
                SleepTimerOption.Duration(60),
                SleepTimerOption.Duration(90),
                SleepTimerOption.EndOfItem,
            ),
            release,
        )
        assertEquals(SleepTimerOption.Duration(1), SleepTimerOption.choices(debug = true)[1])
    }
}
