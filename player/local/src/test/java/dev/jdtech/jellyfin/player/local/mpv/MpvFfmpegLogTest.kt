package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #707: FFmpeg's lines reach only the mpv instance that holds FFmpeg's log, so they are passed on
 * to every open player. Without them a lost connection ended the item.
 */
class MpvFfmpegLogTest {

    private val log = MpvFfmpegLog()

    private fun sink(lines: MutableList<String>) = MpvFfmpegLog.Sink { prefix, _, text ->
        lines.add("$prefix: $text")
    }

    @Test
    fun `an ffmpeg line reaches every open player`() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        log.add(sink(first))
        log.add(sink(second))

        log.dispatch("ffmpeg", 10, "http: Stream ends prematurely at 12780821")

        assertEquals(listOf("ffmpeg: http: Stream ends prematurely at 12780821"), first)
        assertEquals(first, second)
    }

    @Test
    fun `a closed player gets no more lines`() {
        val lines = mutableListOf<String>()
        val closed = sink(lines)
        log.add(closed)
        log.remove(closed)

        log.dispatch("ffmpeg", 10, "tcp: Connection refused")

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a player added twice gets a line once`() {
        val lines = mutableListOf<String>()
        val player = sink(lines)
        log.add(player)
        log.add(player)

        log.dispatch("ffmpeg/demuxer", 10, "matroska: read error")

        assertEquals(1, lines.size)
    }

    @Test
    fun `the holder's own mpv lines are not passed on`() {
        val lines = mutableListOf<String>()
        log.add(sink(lines))

        log.dispatch("cplayer", 10, "Audio device underrun detected.")
        log.dispatch("vo/gpu-next", 10, "not about the player's file")

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `ffmpeg prefixes`() {
        assertTrue(MpvFfmpegLog.isFfmpeg("ffmpeg"))
        assertTrue(MpvFfmpegLog.isFfmpeg("ffmpeg/video"))
        assertTrue(MpvFfmpegLog.isFfmpeg("ffmpeg/demuxer"))
        assertFalse(MpvFfmpegLog.isFfmpeg("ffmpegx"))
        assertFalse(MpvFfmpegLog.isFfmpeg("lavf"))
        assertFalse(MpvFfmpegLog.isFfmpeg("stream"))
    }
}
