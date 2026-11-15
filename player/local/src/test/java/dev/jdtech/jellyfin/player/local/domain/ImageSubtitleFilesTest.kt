package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #613: the files the test server delivers for the image tracks of "Image Subs Movie" while it
 * transcodes, saved as they came: the PGS track as a `.sup` file and the VobSub track as a Matroska
 * file. Both show a white bar for 4 of every 5 seconds, from 0 to 120 s (tools/test-server/pgs.py).
 * Loaded on their own, each file reached media3's parser as one sample, which drew nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageSubtitleFilesTest {

    @Test
    fun `a sup file gives every picture at its own time, each cleared by the next`() {
        val cues = parse("image-subs-movie.sup", MimeTypes.APPLICATION_PGS)

        assertEquals(48, cues.size)
        val shown = cues.filterIndexed { index, _ -> index % 2 == 0 }
        val cleared = cues.filterIndexed { index, _ -> index % 2 == 1 }
        assertEquals((0 until 24).map { it * 5_000_000L }, shown.map { it.startTimeUs })
        assertEquals(
            (0 until 24).map { it * 5_000_000L + 4_000_000L },
            cleared.map { it.startTimeUs },
        )
        assertTrue(shown.all { it.cues.single().bitmap?.width == 320 })
        assertTrue(cleared.all { it.cues.isEmpty() })
        // Each lasts until the next: ExoPlayer showed none of them without a duration.
        assertEquals(
            List(47) { if (it % 2 == 0) 4_000_000L else 1_000_000L } + C.TIME_UNSET,
            cues.map { it.durationUs },
        )
    }

    @Test
    fun `a matroska file with a vobsub track gives its pictures with their times`() {
        val cues =
            parse("image-subs-movie-vobsub.mks", MimeTypes.APPLICATION_VOBSUB).filter {
                it.cues.isNotEmpty()
            }

        assertEquals((0 until 24).map { it * 5_000_000L }, cues.map { it.startTimeUs })
        cues.forEach { assertNotNull(it.cues.single().bitmap) }
    }

    @Test
    fun `a last picture that nothing clears still shows`() {
        // Without a duration ExoPlayer would not show it from a file loaded on its own.
        val cues = mutableListOf<CuesWithTiming>()
        val timing = LastingUntilNext { cues += it }
        timing.add(CuesWithTiming(emptyList(), 0, C.TIME_UNSET))
        timing.add(CuesWithTiming(listOf(Cue.Builder().setText("x").build()), 3, C.TIME_UNSET))
        timing.finish()

        assertEquals(listOf(3L), cues.dropLast(1).map { it.durationUs })
        assertTrue(cues.last().durationUs > 10L * 60 * 60 * 1_000_000)
    }

    @Test
    fun `a picture still showing at the start time comes first, the earlier ones last`() {
        val cues = listOf(0L, 4L, 5L, 9L).map { CuesWithTiming(emptyList(), it, 1) }
        fun order(options: SubtitleParser.OutputOptions): List<Long> {
            val out = mutableListOf<Long>()
            val ordered = OrderedOutput(options) { out += it.startTimeUs }
            val timing = LastingUntilNext(ordered::accept)
            cues.forEach { timing.add(CuesWithTiming(it.cues, it.startTimeUs, C.TIME_UNSET)) }
            timing.finish()
            ordered.finish()
            return out
        }

        assertEquals(listOf(5L, 9L), order(SubtitleParser.OutputOptions.onlyCuesAfter(6)))
        assertEquals(
            listOf(5L, 9L, 0L, 4L),
            order(SubtitleParser.OutputOptions.cuesAfterThenRemainingCuesBefore(6)),
        )
    }

    private fun parse(resource: String, mimeType: String): List<CuesWithTiming> {
        val data = javaClass.getResourceAsStream("/subtitles/$resource")!!.use { it.readBytes() }
        // What a subtitle loaded as a file of its own has: the type, nothing from a container.
        val format = Format.Builder().setSampleMimeType(mimeType).build()
        val factory = ImageSubtitleParserFactory()
        assertTrue(factory.supportsFormat(format))
        val cues = mutableListOf<CuesWithTiming>()
        factory.create(format).parse(data, SubtitleParser.OutputOptions.allCues()) { cues += it }
        return cues
    }
}
